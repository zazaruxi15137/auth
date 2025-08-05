package com.example.rednote.auth.tool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.loginHeader}")
    private String loginHeader;
    @Value("${spring.redis.userTokenSetHeader}")
    private String userTokenSetHeader;
    @Value("${spring.redis.expiration}")
    private long defaultExpiration;


    /**
     * 保存数据（带过期时间）
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 保存数据到集合中（带过期时间）
     */
    public void setToSet(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForSet().add(key, value);
        redisTemplate.expire(key, timeout, unit);
    }

    // public Long getLiveTokenSize(String key){
    //     return redisTemplate.opsForSet().size(key);
    // }

    public int getLiveTokenSize(Long userId){
        Set<String> jtiset = redisTemplate.opsForSet().members( userTokenSetHeader+ userId);
        if(jtiset==null){
            return -1;
        }
        Iterator<String> it = jtiset.iterator();
        int validCount = 0;
        while (it.hasNext()) {
            String jti = it.next();
            if (Boolean.TRUE.equals(redisTemplate.hasKey(loginHeader + jti))) {
                validCount++;
            } else {
                // 过期token，自动移除
                redisTemplate.opsForSet().remove(userTokenSetHeader + userId, jti);
            }
        }
        return validCount;
    }

    /**
     * 保存数据（无过期时间）
     */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }



    /**
     * 获取数据
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除数据
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public void deleteFromSet(String key,String value) {
        redisTemplate.opsForSet().remove(key, value);
    }

    /**
     * 判断 Key 是否存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /**
     * 获取过期时间（秒）
     */
    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire != null ? expire : -1;
    }

    /**
     * 刷新过期时间（不改变 value）
     */
    public boolean refreshExpireToken(String key, String keySet,long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(keySet, defaultExpiration, TimeUnit.SECONDS))
            && Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }
}
