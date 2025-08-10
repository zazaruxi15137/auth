package com.example.rednote.auth.security.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.example.rednote.auth.common.tool.RedisUtil;
import com.example.rednote.auth.common.tool.SerializaUtil;
import com.example.rednote.auth.security.model.JwtUser;
import com.example.rednote.auth.security.util.JwtUtil;

import java.io.IOException;



@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${jwt.header}")
    private String requestHeader;
    @Value("${jwt.prefix}")
    private String prefix;
    @Value("${spring.redis.expiration}")
    private long expiration;
    @Value("${jwt.loginHeader}")
    private String loginHeader;
    @Value("${jwt.blackTokenHeader}")
    private String blackTokenHeader;
    @Value("${spring.redis.userTokenSetHeader}")
    private String userTokenSetHeader;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    

    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(requestHeader);
        if (header == null || !header.startsWith(prefix)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(prefix.length());
        Claims claims = null;
        JwtUser jwtUser = null;
        String jti = null;
        // 验证token
        try {
            claims = jwtUtil.parserJWT(token);
            jti = claims.getId();
            Date exp = claims.getExpiration();
            jwtUser = SerializaUtil.fromJson(claims.getSubject(), JwtUser.class);

            // 黑名单提前校验
            if (redisUtil.hasKey(blackTokenHeader + jti)) {
                writeError(response, 401, "登录失效，请重新登录");
                return;
            }

            long tokenExpireSec = (exp.getTime() - System.currentTimeMillis()) / 1000L;
            long expireSec = Math.min(expiration, tokenExpireSec);
            if (!redisUtil.refreshExpireToken(loginHeader + jti, userTokenSetHeader + jwtUser.getId(), expireSec, TimeUnit.SECONDS)) {
                log.info("用户{},id{}的会话已断开，请重新登录",jwtUser.getUsername(),jwtUser.getId());
                writeError(response, 401, "会话已断开，请重新登录");
                return;
            }

        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException | SignatureException e) {
            writeError(response, 401, "token错误或者已过期");
            return;
        } catch (JsonProcessingException e) {
            log.error("服务器内部错误{}", e.getMessage());
            writeError(response, 500, "服务器内部错误");
            return;
        } catch (Exception e) {
            writeError(response, 401, "jwt验证失败: " + e.getMessage());
            return;
        }
        // 尝试构建用户权限
        if (jwtUser != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String[] authorities = jwtUser.getPermission() == null
                ? new String[0] : jwtUser.getPermission().toArray(new String[0]);
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(jwtUser.getUsername())
                .password("")
                .authorities(authorities)
                .build();
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{"
                + "\"message\": \"" + message + "\","
                + "\"status\": " + status + ","
                + "\"data\": null"
                + "}");
    }
    
}
