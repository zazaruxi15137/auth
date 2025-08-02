package com.example.rednote.auth.tool;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
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

   @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }
    public String generateToken(String username, long expiration) {
        return Jwts.builder()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000L)) // 转换为毫秒
                .setIssuedAt(new Date())
                .setId(UUID.randomUUID().toString())
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