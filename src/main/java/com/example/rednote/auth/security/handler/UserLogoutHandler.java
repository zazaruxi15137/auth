package com.example.rednote.auth.security.handler;
import com.example.rednote.auth.common.exception.CustomException;
import com.example.rednote.auth.common.tool.RedisUtil;
import com.example.rednote.auth.common.tool.SerializaUtil;
import com.example.rednote.auth.model.user.repository.UserRepository;
import com.example.rednote.auth.security.model.JwtUser;
import com.example.rednote.auth.security.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${jwt.header}")
    private String requestHeader;
    @Value("${jwt.prefix}")
    private String prefix;
    @Value("${jwt.loginHeader}")
    private String loginHeader;
    @Value("${jwt.blackTokenHeader}")
    private String blackTokenHeader;
    @Value("${spring.redis.userTokenSetHeader}")
    private String userTokenSetHeader;
    @Autowired
    private JwtUtil jwtUtil;
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // String header=request.getHeader(requestHeader);
        // if (header == null && !header.startsWith(prefix)) {

        // }

        

        String token=request.getHeader(requestHeader).substring(prefix.length());
        String jti=null;
        long expire = 0;
        // Long userId = 0L;
        try{
            Claims claims = jwtUtil.parseClaims(token);
            jti = claims.getId();
            expire = claims.getExpiration().getTime() - System.currentTimeMillis();
            // JwtUserDto jwtUserDto = serializaUtil.fromJson(claims.getSubject(), JwtUserDto.class);
            // userId=jwtUserDto.getId();
        }catch(Exception exception){
            log.error("Jwt解析错误{}", exception.getMessage());
            throw new CustomException("内部错误");
        }
        try{
        // 清除用户的认证信息
        redisUtil.delete(loginHeader + jti);
        // redisUtil.deleteFromSet(userTokenSetHeader+userId, jti);
        redisUtil.set(blackTokenHeader+jti,"1",expire,TimeUnit.MILLISECONDS);
        SecurityContextHolder.clearContext();
        }catch(Exception e){
            log.error("redis 操作错误:{}",e.getMessage());
            throw new CustomException("内部错误");
        }
        
        log.info("用户已登出");
    }
    
}
