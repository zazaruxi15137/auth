package com.example.rednote.auth.security.handler;
import com.example.rednote.auth.common.exception.CustomException;
import com.example.rednote.auth.common.tool.RedisUtil;
import com.example.rednote.auth.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
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

        String token=request.getHeader(requestHeader).substring(prefix.length());
        String jti=null;
        long expire = 0;
        try{
            Claims claims = jwtUtil.parseClaims(token);
            jti = claims.getId();
            expire = claims.getExpiration().getTime() - System.currentTimeMillis();
        }catch(Exception exception){
            log.error("Jwt解析错误{}", exception.getMessage());
            throw new CustomException("内部错误");
        }
        try{
        // 清除用户的认证信息
        redisUtil.delete(loginHeader + jti);
        redisUtil.set(blackTokenHeader+jti,"1",expire,TimeUnit.MILLISECONDS);
        SecurityContextHolder.clearContext();
        }catch(Exception e){
            log.error("redis 操作错误:{}",e.getMessage());
            throw new CustomException("内部错误");
        }
        
        log.info("用户已登出");
    }
    
}
