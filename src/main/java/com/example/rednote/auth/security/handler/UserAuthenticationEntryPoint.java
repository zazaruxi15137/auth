package com.example.rednote.auth.security.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.example.rednote.auth.common.tool.MetricsNames;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Component
public class UserAuthenticationEntryPoint  implements AuthenticationEntryPoint {
    @Autowired
    private MeterRegistry meter; // 指标上报
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        log.warn("匿名操作{}{}", authException.getMessage(),request.getContextPath());
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("code", 401);
        responseBody.put("message", "未授权的请求");
        responseBody.put("path", request.getRequestURI());
        ObjectMapper mapper = new ObjectMapper();
        meter.counter(MetricsNames.AUTH_FAIL_TOTAL, Tags.of("endpoint",request.getRequestURI())).increment();
        response.getWriter().write(mapper.writeValueAsString(responseBody));
    }
    
}
