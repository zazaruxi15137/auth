package com.example.rednote.auth.filter;

import com.example.rednote.auth.entity.User;
import com.example.rednote.auth.tool.JwtUtil;
import com.example.rednote.auth.tool.RedisUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
import com.example.rednote.auth.tool.SerializaUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.concurrent.TimeUnit;
import com.example.rednote.auth.dto.JwtUserDto;
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

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final SerializaUtil serializaUtil;
    

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(requestHeader);
        // 检查token是否符合要求
        if (header != null && header.startsWith(prefix)) {
            String token = header.substring(prefix.length());
            String jti=null;
            JwtUserDto jwtUserDto=null;
            // 从token中解析用户信息
            try {
                Claims claims = jwtUtil.parserJWT(token);
                jti=claims.getId();
                jwtUserDto = serializaUtil.fromJson(claims.getSubject(), JwtUserDto.class);
            }catch (JwtException exception){
                log.warn("token错误{}",exception);
                writeError(response,401,"token错误");
                return;
            }catch(JsonProcessingException e){
                log.error("Json反序列化失败{}",e.getMessage());
                writeError(response,401,"服务器内部错误");
                return;
            }
            catch (Exception e) {
                log.warn("jwt验证:"+e);
                writeError(response,401,"jwt验证:"+e);
                return;
            }
            // 检查token是否在黑名单中
            if(redisUtil.hasKey(blackTokenHeader+jti)){
                log.info("尝试使用黑名单token访问");
                writeError(response,401,"登录失效，请重新登录");
                return;
            }
            // token中解析出有用的信息且未经过认证
            else if (jwtUserDto!=null
            && SecurityContextHolder.getContext().getAuthentication() == null
            ) {
                // 刷新token过期时间
                // 从Redis中获取用户信息
                try {
                String[] authorities = jwtUserDto.getPermission() == null
                    ? new String[0] : jwtUserDto.getPermission().toArray(new String[0]);
                UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(jwtUserDto.getUsername())
                    .password("") // 或null
                    .authorities(authorities)
                    .build();
                // 3. 构造认证信息放入上下文
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                }
                catch (Exception e) {
                    log.warn("权限认证失败: {}",e.getMessage());
                    writeError(response,401,"内部错误，请尝试重新登录");
                    return;
                }
            }
        }
        // 继续过滤链
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
