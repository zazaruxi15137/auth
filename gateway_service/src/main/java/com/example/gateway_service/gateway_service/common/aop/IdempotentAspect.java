package com.example.gateway_service.gateway_service.common.aop;


import com.example.gateway_service.gateway_service.common.RespondMessage;
import com.example.gateway_service.gateway_service.common.tool.IdempotencyKeyResolver;
import com.example.gateway_service.gateway_service.common.tool.MetricsNames;
import com.example.gateway_service.gateway_service.security.model.JwtUser;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Aspect
@Component
@Order(0)
@Slf4j
public class IdempotentAspect {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meter; // 指标上报
    @Value("${spring.respondCacheSize}")
    private Integer respondCacheSize;

    public IdempotentAspect(StringRedisTemplate redis, ObjectMapper objectMapper, MeterRegistry meter) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.meter = meter;

    }

    /** Lua：原子判定 NEW / PROCESSING / REPLAY，并取出缓存响应 */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>(
            // KEYS[1]=idemKey, KEYS[2]=respKey, ARGV[1]=ttlSeconds
            "local v = redis.call('GET', KEYS[1]); " +
            "if not v then " +
            "  redis.call('SET', KEYS[1], 'P', 'EX', ARGV[1]); " +
            "  return {'NEW', ''}; " +
            "end; " +
            "if v == 'D' then " +
            "  local r = redis.call('GET', KEYS[2]); " +
            "  if r then return {'REPLAY', r}; else return {'REPLAY',''}; end; " +
            "end; " +
            "return {'PROCESSING', ''};",
            List.class 
    );
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<Boolean> ROLLBACK_IF_P = new DefaultRedisScript<>(
        // KEYS[1]=idemKey
        "local v = redis.call('GET', KEYS[1]); "+
        "if v == 'P' then redis.call('DEL', KEYS[1]); return true else return false end;",
        Boolean.class
    );

    @Around("@annotation(anno)")
    public Object around(ProceedingJoinPoint pjp, Idempotent anno) throws Throwable {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr == null) return pjp.proceed();
        HttpServletRequest req = attr.getRequest();

        // 仅拦截写操作
        final String method = req.getMethod();
        if (!List.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
            return pjp.proceed();
        }

        final String endpoint = Optional.ofNullable(req.getRequestURI()
                
        ).orElse((String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));

        // 入口请求计数
        safeCount(MetricsNames.IDEMPOTENCY_REQUESTS_TOTAL, "endpoint", endpoint, "method", method, "enabled", "true");

        long userId = currentUserIdOrZero(); 
        //未取到用户id使用请求指纹分片
        String anonFp = null;
        if (userId == 0L) {
            String ip = Optional.ofNullable(req.getHeader("X-Forwarded-For"))
                                .map(s -> s.split(",")[0].trim())
                                .orElse(req.getRemoteAddr());
            String ua = Optional.ofNullable(req.getHeader("User-Agent")).orElse("UA");
            anonFp = Integer.toUnsignedString((ip + "|" + ua).hashCode());
        }

        String bizKey = IdempotencyKeyResolver.resolve(anno.key(), pjp.getArgs(), req, userId, anno.required());
        if (!StringUtils.hasText(bizKey)) {
            // 必须携带时，缺失直接返回 428
            return ResponseEntity.status(428)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json("{\"code\":\"IDEMPOTENCY_KEY_REQUIRED\",\"message\":\"Missing Idempotency-Key\"}"));
        }
        int SHARDS = 128;
        int slotInt = (userId != 0L) ? (int)(Math.floorMod(userId, SHARDS))
                             : Math.floorMod(anonFp.hashCode(), SHARDS);
        
        String idemKey = "{"+slotInt+"}:idem:" + endpoint + ":" + userId + ":" + bizKey;
        log.info(idemKey);
        String respKey = idemKey + ":resp";
        String ttlStr = String.valueOf(anno.ttlSeconds());
        long ttl = anno.ttlSeconds();            // 你的固定 TTL（对外 SLA）
        double jitterRatio = 0.05;              // 5% 抖动
        int maxJitter = Math.max(1, (int)Math.floor(ttl * jitterRatio));
        // 只向下抖动，避免“超过配置 TTL”导致语义歧义
        long ttlWithJitter = Math.max(1, ttl - ThreadLocalRandom.current().nextInt(0, maxJitter + 1));

// 用途：仅用于写入 D/respKey 的过期；P 锁仍用固定/watchdog 续期


        List<?> res;
        Timer.Sample s = Timer.start(meter);
        try {
            res = redis.execute(SCRIPT, Arrays.asList(idemKey, respKey), ttlStr);
        } catch (Exception e) {
            log.error("指纹获取失败{}", e.getMessage());
            Counter.builder(MetricsNames.LUA_EXEC_FAIL)
                .tag("name","幂等性脚本").register(meter).increment();
            safeCount(MetricsNames.IDEMPOTENCY_RESULT_TOTAL, "endpoint", endpoint, "method", method, "outcome", "ERROR");
            throw e;
        }finally {
            s.stop(Timer.builder(MetricsNames.LUA_EXEC_TIMER)
                .tag("name","幂等性脚本")
                .publishPercentiles(0.95,0.99)
                .register(meter));
        }
        String outcome = (res != null && !res.isEmpty()) ? String.valueOf(res.get(0)) : "NEW";
        String cached = (res != null && res.size() > 1) ? Objects.toString(res.get(1), null) : null;

        switch (outcome) {
            case "PROCESSING":
                safeCount(MetricsNames.IDEMPOTENT_HIT,"endpoint", endpoint, "method", method, "outcome", "PROCESSING");
                safeCount(MetricsNames.IDEMPOTENCY_RESULT_TOTAL, "endpoint", endpoint, "method", method, "outcome", "PROCESSING");
                
                return ResponseEntity.status(409)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json("{\"code\":\"DUPLICATE_PROCESSING\",\"message\":\"Duplicate request is processing\"}"));
            case "REPLAY":
                safeCount(MetricsNames.IDEMPOTENT_HIT,"endpoint", endpoint, "method", method,"outcome", "REPLAY");
                safeCount(MetricsNames.IDEMPOTENCY_RESULT_TOTAL, "endpoint", endpoint, "method", method, "outcome", "REPLAY");
                if (anno.storeResponse() && StringUtils.hasText(cached)) {
                    // 回放缓存的响应（带状态码）
                    CachedResp cr = parseCached(cached);
                    if (cr != null) {
                        log.info("sdsadas{},{}",cr.status);
                        MediaType ct = StringUtils.hasText(cr.contentType) ? MediaType.parseMediaType(cr.contentType) : MediaType.APPLICATION_JSON;
                        return ResponseEntity.status(cr.status > 0 ? cr.status : 200)
                                .contentType(ct)
                                .body(json(cr.RespondMessage));
                    }
                    // 兼容旧值：直接回放 JSON 字符串
                    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(RespondMessage.success(cached));
                }
                // 没存响应体时给个通用成功
                return ResponseEntity.ok(json("{\"code\":200,\"Message\":请求成功}"));
            case "NEW":
                safeCount(MetricsNames.IDEMPOTENCY_RESULT_TOTAL, "endpoint", endpoint, "method", method, "outcome", "NEW");
                Timer.Sample s2 = Timer.start(meter);

                // 首次进入：执行业务
                Object result = null;
                Throwable error = null;
                try {
                    result = pjp.proceed();
                    return result;
                } catch (Throwable t) {
                    log.error("首次幂等命中执行错误{}",t.getMessage());
                    error = t;
                    throw t;
                } finally {
                    try {
                        // 异常则允许重试（删除 'P' 占位）；成功则标记完成并缓存响应
                        if (error != null) {
                            redis.execute(ROLLBACK_IF_P,List.of(idemKey));
                        } else {
                            if (anno.storeResponse()) {
                                // 仅缓存 2xx 且体积不大且 Content-Type 为 JSON
                                CachedResp cr = toCached(result);
                                // boolean is2xx = cr.status >= 200 && cr.status < 300;
                                int bytes = cr.RespondMessage != null ? cr.RespondMessage.getBytes(StandardCharsets.UTF_8).length : 0;
                                boolean isJson = (cr.contentType != null && cr.contentType.toLowerCase().contains("application/json"));
                                if (isJson && bytes <= respondCacheSize) {  // 阈值可配置
                                    String cachedJson = objectMapper.writeValueAsString(cr);
                                    redis.opsForValue().set(respKey, cachedJson, Duration.ofSeconds(ttlWithJitter));
                                }else{
                                    redis.opsForValue().set(
                                        respKey,
                                        objectMapper.writeValueAsString( new CachedResp(cr.status)), 
                                        Duration.ofSeconds(ttlWithJitter)
                                        );
                                    //
                                    safeCount(MetricsNames.IDEMPOTENCY_CACHE_SKIP_TOTAL, "reason","缓存体为空或者过大");
                                    log.info("跳过缓存，缓存体为空或者过大");
                                }
                            }
                            // 标记完成
                            redis.opsForValue().set(idemKey, "D", Duration.ofSeconds(ttlWithJitter));
                        }
                    } catch (Exception ignore) {
                        // 缓存/标记失败不影响主流程
                    }finally{
                        s2.stop(Timer.builder(MetricsNames.IDEMPOTENCY_FIRST_LATENCY_TIMER)
                            .tags("endpoint",endpoint,"method",method,"outcome", "NEW")
                            .publishPercentileHistogram()
                            .register(meter));
                                }}
            default:
                return pjp.proceed();
                
        }
    }

    private long currentUserIdOrZero() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return 0L;

        Object principal = auth.getPrincipal(); // 大多数项目把登录用户放这里
        if (principal instanceof JwtUser ju) {
            Long id = ju.getId();
            return id != null ? id : 0L;
        }

        // 兼容：有些项目把登录用户塞到 details
        Object details = auth.getDetails();
        if (details instanceof JwtUser ju) {
            Long id = ju.getId();
            return id != null ? id : 0L;
        }
        return 0L;
    }

    /** 将首次执行结果转为可缓存结构（仅 JSON 场景） */
    private CachedResp toCached(Object result) {
        try {
            if (result instanceof ResponseEntity<?> re) {
                Object body = re.getBody();
                String bodyJson = (body instanceof String s) ? s : objectMapper.writeValueAsString(body);
                String ct = Optional.ofNullable(re.getHeaders().getContentType()).orElse(MediaType.APPLICATION_JSON).toString();
                return new CachedResp(re.getStatusCode().value(), ct, bodyJson);
            } else {
                String bodyJson = (result instanceof String s) ? s : objectMapper.writeValueAsString(result);
                return new CachedResp(200, MediaType.APPLICATION_JSON_VALUE, bodyJson);
            }
        } catch (Exception e) {
            // 序列化失败就只存字符串化
            String s = Objects.toString(result, "null");
            return new CachedResp(200, MediaType.APPLICATION_JSON_VALUE, s);
        }
    }

    private CachedResp parseCached(String js) {
        try {
            return objectMapper.readValue(js.getBytes(StandardCharsets.UTF_8), CachedResp.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String json(String s) { return s; }

    /** 缓存的响应结构（最简：状态码 + ContentType + JSON字符串） */
    public static class CachedResp {
        public int status;
        public String contentType;
        public String RespondMessage;

        public CachedResp() {}
        public CachedResp(int status) {
            this.status = status;
        }
        public CachedResp(int status, String contentType, String bodyJson) {
            this.status = status;
            this.contentType = contentType;
            this.RespondMessage = bodyJson;
        }
    }

    /** 安全计数（meter 可为 null 时直接忽略） */
    private void safeCount(String name, String... tags) {
        try {
            if (meter != null) meter.counter(name, tags).increment();
        } catch (Throwable ignore) {}
    }
}