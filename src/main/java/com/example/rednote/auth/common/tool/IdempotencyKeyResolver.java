package com.example.rednote.auth.common.tool;


import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IdempotencyKeyResolver {

    private static final ExpressionParser PARSER =
            new SpelExpressionParser(new SpelParserConfiguration(false, false));
    private static final Map<String, Expression> EXPR_CACHE = new ConcurrentHashMap<>();
    private static final ParameterNameDiscoverer PND = new DefaultParameterNameDiscoverer();

    /** 解析幂等键；required=false 时支持指纹兜底 */
    public static String resolve(String spel, Object[] args, HttpServletRequest req, long userId, boolean required) {
        try {
            SimpleEvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            ctx.setVariable("req", req);
            ctx.setVariable("headers", headersLowercase(req));
            ctx.setVariable("params", paramsToSingleValueMap(req));
            ctx.setVariable("path", pathVars(req));
            ctx.setVariable("userId", userId);
            ctx.setVariable("args", args != null ? args : new Object[0]);
            String val = null;
            if (StringUtils.hasText(spel)) {
                Expression exp = EXPR_CACHE.computeIfAbsent(spel, PARSER::parseExpression);
                Object v = exp.getValue(ctx);
                if (v != null) val = String.valueOf(v).trim();
            }

            if (!StringUtils.hasText(val) && !required) {
                // 允许指纹兜底（仅在 required=false 时）
                val = fingerprint(req, userId);
            }

            if (!StringUtils.hasText(val)) return null;
            return normalize(val);
        } catch (Exception e) {
            return null;
        }
    }

    /** 仅保留安全字符，超长转 SHA-256（避免高基数/超长键） */
    private static String normalize(String s) {
        String cleaned = s.replaceAll("[^A-Za-z0-9_\\-\\.]", "-");
        if (cleaned.length() > 128) {
            return sha256Hex(cleaned.getBytes(StandardCharsets.UTF_8));
        }
        return cleaned;
    }

    private static Map<String, String> headersLowercase(HttpServletRequest req) {
        Map<String, String> map = new HashMap<>();
        Enumeration<String> names = req.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String n = names.nextElement();
            map.put(n.toLowerCase(Locale.ROOT), req.getHeader(n));
        }
        return map;
    }

    private static Map<String, String> paramsToSingleValueMap(HttpServletRequest req) {
        Map<String, String[]> raw = req.getParameterMap();
        Map<String, String> out = new HashMap<>(raw.size());
        raw.forEach((k, v) -> out.put(k, (v != null && v.length > 0) ? v[0] : null));
        return out;
    }

    private static Map<String, String> pathVars(HttpServletRequest req) {
        Object attr = req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attr instanceof Map<?, ?> m) {
            Map<String, String> cast = new HashMap<>();
            m.forEach((k, v) -> cast.put(String.valueOf(k), String.valueOf(v)));
            return cast;
        }
        return Collections.emptyMap();
    }

    /** 请求指纹（method|endpointTemplate|sortedQuery|userId） */
    private static String fingerprint(HttpServletRequest req, long userId) {
        String method = req.getMethod();
        String endpoint = Optional.ofNullable(
                (String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
        ).orElse(req.getRequestURI());

        String query = paramsToSingleValueMap(req).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + String.valueOf(e.getValue()))
                .collect(Collectors.joining("&"));

        String raw = method + "|" + endpoint + "|" + query + "|" + userId;
        return "fp-" + sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
