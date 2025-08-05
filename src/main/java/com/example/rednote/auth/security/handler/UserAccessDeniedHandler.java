package com.example.rednote.auth.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class UserAccessDeniedHandler implements AccessDeniedHandler {

     public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        log.warn("权限不足，无法访问资源.url{},user:{}", request.getRequestURI(), SecurityContextHolder.getContext().getAuthentication().getName());
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("code", 403);
        responseBody.put("message", "权限不足，无法访问该资源");
        responseBody.put("path", request.getRequestURI());
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(responseBody));
    }

}
