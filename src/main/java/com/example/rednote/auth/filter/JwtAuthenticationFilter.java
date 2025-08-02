package com.example.rednote.auth.filter;

import com.example.rednote.auth.entity.User;
import com.example.rednote.auth.tool.JwtUtil;
import com.example.rednote.auth.tool.RedisUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import java.util.concurrent.TimeUnit;
import com.example.rednote.auth.dto.LoginUserDto;


import java.io.IOException;



@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${jwt.header}")
    private String requestHeader;
    @Value("${jwt.prefix}")
    private String prefix;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    @Value("${spring.redis.expiration}")
    private long expiration;
    

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(requestHeader);

        if (header != null && header.startsWith(prefix)) {
            String token = header.substring(prefix.length());
            String username = "";
            try {
                Claims claims = jwtUtil.parserJWT(token);
                username = claims.getSubject();
}catch (ExpiredJwtException exception){
                log.error("身份验证过期"+exception);
                writeError(response,401,"身份验证过期");
                return;
}catch (MalformedJwtException exception){
                log.error("JWT Token格式不对"+exception);
                writeError(response,401,"JWT Token格式不对");
                return;
}catch (SignatureException exception){
                log.error("JWT 签名错误"+exception);
                writeError(response,401,"JWT 签名错误");
                return;
}catch (UnsupportedJwtException exception){
                log.error("不支持的 Jwt "+exception);
                writeError(response,401,"不支持的 Jwt ");
                return;
}catch (Exception e) {
                log.error("jwt验证:"+e);
                writeError(response,401,"jwt验证:"+e);
                return;
}
            if ( !"".equals(username)
            && SecurityContextHolder.getContext().getAuthentication() == null
            ) {
                // 刷新token过期时间
                // 从Redis中获取用户信息
                try {
                    redisUtil.refreshExpire(username, expiration, TimeUnit.SECONDS);
                    LoginUserDto loginUser = (LoginUserDto) redisUtil.get(username);
                    UserDetails userDetails = org.springframework.security.core.userdetails.User
                            .withUsername(loginUser.getUsername())
                            .password(loginUser.getPassword())
                            .roles(loginUser.getRoles())
                            .build();
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                }catch (Exception e) {
                    writeError(response,401,"登录失效，请重新登录");
                    return;
                }
                
            }
        }
        // 继续过滤链
        chain.doFilter(request, response);
    }
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        log.warn("JWT认证失败: {}", message);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{"
                + "\"message\": \"" + message + "\","
                + "\"status\": " + status + ","
                + "\"data\": null"
                + "}");
    }
    
}
