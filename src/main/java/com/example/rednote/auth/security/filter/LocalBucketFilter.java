package com.example.rednote.auth.security.filter;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.rednote.auth.common.properties.LocalBucketProperties;
import com.example.rednote.auth.common.properties.RateLimitProperties;
import com.example.rednote.auth.common.service.BlockingTokenBucket;
import com.example.rednote.auth.common.service.RateLimitService;
import com.example.rednote.auth.common.tool.MetricsNames;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalBucketFilter extends OncePerRequestFilter {

    private final BlockingTokenBucket bucket;
    private final LocalBucketProperties props;
    private final MeterRegistry meterRegistry;
    private final RateLimitService service;
    @Autowired
    private RateLimitProperties rateLimitProps;

    @PostConstruct
    public void gauges() {
    Gauge.builder(MetricsNames.LOCAL_BUCKET_AVAILABLE, bucket, b ->{return b.availableTokens();})
        .description("Available tokens in local bucket")
        .strongReference(true)      // 关键：避免被 GC
        .register(meterRegistry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // if (request.getHeader("").equals(request)){
        //     return false;
        // }
        String uri = request.getRequestURI();
        for (String prefix : rateLimitProps.getIgnorePaths()) {
        if (uri.startsWith(prefix)) return true;
        }
        return false;
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

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
                
        long userId = currentUserIdOrZero();
        String ip = clientIp(req);
        req.setAttribute("UserID", userId);
        req.setAttribute("IP", ip);
        // 黑名单优先
        if (userId > 0 && service.isUserBlacklisted(userId) || service.isIpBlacklisted(ip)) {
            meterRegistry.counter(MetricsNames.RATELIMIT_REDIS_BLICKED_TOTAL, "reason", "blacklist").increment();
            req.setAttribute("LOCAL_BUCKET_REFUND", Boolean.TRUE);
            write429(resp, 60, "BLACKLISTED");
            return;
        }
        int cost=resolveCost(req.getRequestURI(), rateLimitProps.getPathCost());
        req.setAttribute("RL_COST", cost);
        try {
            long t0 = System.nanoTime();
            int ms = bucket.acquire(Duration.ofMillis(props.getAcquireTimeoutMs()), cost);

            if (ms!=-1) {
                // 等待超时，给 429（更健康，避免长时间占用 Tomcat 线程）
                meterRegistry.counter(MetricsNames.RATELIMIT_LOCAL_TIMEOUT_TOTAL).increment();
                resp.setStatus(429);
                resp.setHeader("Retry-After", String.valueOf(Math.floor(ms*1000/props.getRatePerSecond())));
                resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                resp.getWriter().write("{\"code\":429,\"message\":\"Server busy, try later\"}");
                return;
            }

            req.setAttribute("LOCAL_BUCKET_CONSUMED", cost);
            long waitedMs = (System.nanoTime() - t0) / 1_000_000;

            DistributionSummary.builder(MetricsNames.RATELIMIT_LOCAL_WAIT_MS)
            .baseUnit("milliseconds")
            .publishPercentileHistogram()
            .register(meterRegistry)
            .record(waitedMs);
            
            meterRegistry.counter(MetricsNames.RATELIMIT_LOCAL_ALLOWED_TOTAL).increment();
            chain.doFilter(req, resp);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            resp.setStatus(503);
            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resp.getWriter().write("{\"code\":503,\"message\":\"Interrupted\"}");
            return;
        }finally {
            // 如果后续（Redis）限流拒绝了，走退款
            Object refund = req.getAttribute("LOCAL_BUCKET_REFUND");
            if (Boolean.TRUE.equals(refund)) {
                bucket.release();
            }
        }
    }
        private void write429(HttpServletResponse resp, int retryAfterSec, String reason) throws IOException {
        resp.setStatus(429);
        resp.setHeader("Retry-After", String.valueOf(retryAfterSec));
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.getWriter().write("{\"code\":429,\"message\":\"Too Many Requests\",\"reason\":\""+reason+"\"}");
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
}