package com.example.gateway_service.gateway_service.security.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.example.gateway_service.gateway_service.common.exception.CustomException;
import com.example.gateway_service.gateway_service.common.tool.RedisUtil;
import com.example.gateway_service.gateway_service.common.tool.SerializaUtil;
import com.example.gateway_service.gateway_service.model.user.dto.UserDto;
import com.example.gateway_service.gateway_service.security.model.JwtUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secretKey;
    private Key key;
    @Value("${jwt.expiration}")
    private long jwtExpiration;
    @Value("${spring.redis.expiration}")
    private long redisExpiration;
    @Value("${jwt.loginHeader}")
    private String loginHeader;
    private final RedisUtil redisUtil;  
    @Value("${spring.redis.allowedOnlineNum}")
    private long allowedOnlineNum;
    @Value("${spring.redis.userTokenSetHeader}")
    private String userTokenSetHeader;
   @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }


    public String generateToken(String jti,JwtUser jwtuserDto, long expiration)throws JsonProcessingException {
        return Jwts.builder()
                .setSubject(SerializaUtil.toJson(jwtuserDto))
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000L)) // 转换为毫秒
                .setIssuedAt(new Date())
                .setId(jti)
                // .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public UserDto setAuthToken(UserDto userDto)throws JsonProcessingException {
        // 检查同一账户同时在线人数
        if(redisUtil.getLiveTokenSize(userDto.getId())>=allowedOnlineNum){
            log.warn("超过同时在线人数限制: {}", userDto.getUsername());
            throw new CustomException("超过同时在线人数限制");
        }

        String jti=UUID.randomUUID().toString();
        String token=Jwts.builder()
        .setSubject(userDto.getUsername())
        .claim("uid", userDto.getId())
        .claim("auth", SerializaUtil.toJson(userDto.getPermission()))
        .claim("roles", userDto.getRoles())
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration * 1000L))
        .setId(jti)
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();

        redisUtil.set(loginHeader+jti, SerializaUtil.toJson(userDto), redisExpiration, TimeUnit.SECONDS);
        redisUtil.setToSet(userTokenSetHeader+userDto.getId(), jti, redisExpiration, TimeUnit.SECONDS);
        userDto.setToken(token);
        return userDto;
    }
 


    public Claims parseClaims(String token){
            return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
    public Claims parserJWT(String token){
        return Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody();
    }


    public boolean validateToken(String token) {
    try {
        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
        return true;
    } catch (io.jsonwebtoken.security.SecurityException e) {
        log.warn("JWT签名异常: {}", e.getMessage());
        return false;
    } catch (io.jsonwebtoken.ExpiredJwtException e) {
        log.warn("JWT过期: {}", e.getMessage());
        return false;
    } catch (io.jsonwebtoken.JwtException e) {
        log.warn("JWT非法: {}", e.getMessage());
        return false;
    }
}
}