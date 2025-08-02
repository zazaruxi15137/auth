package com.example.rednote.auth.handler;
import com.example.rednote.auth.repository.UserRepository;
import com.example.rednote.auth.tool.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UserLogoutHandler implements LogoutHandler {


    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // System.out.println("用户登出处理器被调用" + SecurityContextHolder.getContext().getAuthentication().getName());
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            // 清除用户的认证信息
            redisUtil.delete("user:" + username);
            log.info("用户 {} 已登出", username);
        }
        
    }
    
}
