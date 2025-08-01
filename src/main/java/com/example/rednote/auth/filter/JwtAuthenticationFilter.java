package com.example.rednote.auth.filter;

import com.example.rednote.auth.entity.User;
import com.example.rednote.auth.tool.JwtUtil;
import com.example.rednote.auth.tool.RedisUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.TimeUnit;
import java.io.IOException;




@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${jwt.header}")
    private String header;
    @Value("${jwt.prefix}")
    private String prefix;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    @Value("${jwt.expiration}")
    private long expiration;
    

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(this.header);

        if (header != null && header.startsWith(prefix)) {
            String token = header.substring(prefix.length());
            String username = "";
            try {
                username = "user:" + jwtUtil.extractUsername(token);
            } catch (Exception e) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "非法令牌");
                return;
            } 
            
            if (username != null
            && SecurityContextHolder.getContext().getAuthentication() == null
            ) {
                if (!redisUtil.hasKey(username)) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录失效，请重新登录");
                    return;
                }
                // 刷新token过期时间
                redisUtil.refreshExpire(username, expiration, TimeUnit.SECONDS);
                // 从Redis中获取用户信息    
                try{
                User user = (User)redisUtil.get(username);
                UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRoles())
                .build();
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                }catch (Exception e) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录失效，请重新登录");
                    return;
                }
                
            }
        }
        // 继续过滤链
        System.out.println("当前用户: " + SecurityContextHolder.getContext().getAuthentication());
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
