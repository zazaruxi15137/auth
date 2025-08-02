package com.example.rednote.auth.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Component
public class UserAuthenticationEntryPoint  implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        log.warn("匿名操作", authException.getMessage());
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("code", 401);
        responseBody.put("message", authException.getMessage());
        responseBody.put("path", request.getRequestURI());
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(responseBody));
    }
    
}
