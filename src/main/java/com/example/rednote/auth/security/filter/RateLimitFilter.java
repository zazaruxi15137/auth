package com.example.rednote.auth.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.rednote.auth.common.properties.RateLimitProperties;
import com.example.rednote.auth.common.service.RateLimitService;
import com.example.rednote.auth.common.tool.MetricsNames;

import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimitProperties props;
  private final RateLimitService service;
  private final MeterRegistry meterRegistry;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // if (request.getHeader("").equals(request)){//放行特定请求
    //         return false;
    //     }
    String uri = request.getRequestURI();
    for (String prefix : props.getIgnorePaths()) {
      if (uri.startsWith(prefix)) return true;
    }
    return false;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {

    String uri = req.getRequestURI();
    int cost = Optional.ofNullable((Integer) req.getAttribute("RL_COST"))
        .orElseGet(() -> resolveCost(uri,props.getPathCost())); // resolveCost 与本地一致的实现
    String apiTag = props.isPerApiBucket() ? apiTag(uri, req.getMethod()) : null;

    long userId = currentUserIdOrZero();
    String ip = clientIp(req);

    // 选择主体：已登录→用户；未登录→看配置是否优先按 IP
    RateLimitService.RateResult rr;
    if (userId > 0) {
      rr = service.tryConsumeUser(userId, apiTag, cost,
          props.getDefaultCap(), props.getDefaultRatePerSec(), props.getDefaultIdleTtlSec());
    } else if (props.isIpFirstForAnonymous()) {
      rr = service.tryConsumeIp(ip, apiTag, cost,
          props.getDefaultCap(), props.getDefaultRatePerSec(), props.getDefaultIdleTtlSec());
    } else {
      rr = service.tryConsumeUser(0L, apiTag, cost, // 匿名也走“用户=0”的桶
          props.getDefaultCap(), props.getDefaultRatePerSec(), props.getDefaultIdleTtlSec());
    }

    if (!rr.allowed()) {
        req.setAttribute("LOCAL_BUCKET_REFUND", Boolean.TRUE);
        write429(resp, Math.max(1, (int)Math.ceil(rr.waitMs()/1000.0)), "RATE_LIMITED");
        meterRegistry.counter(MetricsNames.RATELIMIT_REDIS_BLICKED_TOTAL, "reason", "quota").increment();
        return;
    }
    meterRegistry.counter(MetricsNames.RATELIMIT_REDIS_ALLOWED_TOTAL).increment();
    chain.doFilter(req, resp);
  }

  private int resolveCost(String uri, Map<String,Integer> map) {
    // 简单前缀匹配；需要更灵活可用 AntPathMatcher
    int best = 1;
    int bestLen = -1;
    for (var e : map.entrySet()) {
      String p = e.getKey();
      if (uri.startsWith(p) && p.length() > bestLen) {
        best = e.getValue();
        bestLen = p.length();
      }
    }
    return best;
  }

  private String apiTag(String uri, String method) {
    // 统一模板化，避免标签基数暴涨（可用 HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE）
    return method + ":" + uri.replaceAll("/\\d+", "/{id}");
  }

  private long currentUserIdOrZero() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) return 0L;
    Object p = auth.getPrincipal();
    try {
      // 你项目里的 JwtUser.getId()，此处用反射兜底
      var m = p.getClass().getMethod("getId");
      Object id = m.invoke(p);
      if (id instanceof Number n) return n.longValue();
    } catch (Exception ignore) {}
    return 0L;
  }

  private String clientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
    return req.getRemoteAddr();
  }

  private void write429(HttpServletResponse resp, int retryAfterSec, String reason) throws IOException {
    resp.setStatus(429);

    resp.setHeader("Retry-After", String.valueOf(retryAfterSec));
    resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
    resp.getWriter().write("{\"code\":429,\"message\":\"Too Many Requests\",\"reason\":\""+reason+"\"}");
  }
}