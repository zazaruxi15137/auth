package com.example.rednote.auth.tool;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.example.rednote.auth.dto.JwtUserDto;
import com.example.rednote.auth.dto.LoginUserDto;
import com.example.rednote.auth.dto.UserDto;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Value;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secretKey;
    private Key key;
    private final SerializaUtil serializaUtil;

   @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }
    public String generateToken(String jti,JwtUserDto jwtuserDto, long expiration)throws JsonProcessingException {
        return Jwts.builder()
                .setSubject(serializaUtil.toJson(jwtuserDto))
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000L)) // 转换为毫秒
                .setIssuedAt(new Date())
                .setId(jti)
                // .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
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
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
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