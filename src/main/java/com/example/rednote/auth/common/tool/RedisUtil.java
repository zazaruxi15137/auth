package com.example.rednote.auth.common.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.hash.HashMapper;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

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
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public void deleteFromSet(String key,String value) {
            stringRedisTemplate.opsForSet().remove(key, value);
        }
    /**
     * 保存数据到集合中（带过期时间）
     */
    public void setToSet(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForSet().add(key, value);
        stringRedisTemplate.expire(key, timeout, unit);
    }

    public void setAllToSet(String key, String[] value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForSet().add(key, value);
        stringRedisTemplate.expire(key, timeout, unit);
    }
    
    public void setToZSet(String key, String value,Long score, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForZSet().add(key, value, score );
        stringRedisTemplate.expire(key, timeout, unit);
    }
    public void setToZSet(String key, String value,Long score) {
        stringRedisTemplate.opsForZSet().add(key, value, score );
    }
    public void trimZSet(String key,long min, long max) {
        stringRedisTemplate.opsForZSet().removeRange(key,min, max);
    }
    public void trimZSetByscore(String key,long score) {
        stringRedisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY,score);
    }

    public int getLiveTokenSize(Long userId){
        Set<String> jtiset = stringRedisTemplate.opsForSet().members( userTokenSetHeader+ userId);
        if(jtiset==null){
            return -1;
        }
        Iterator<String> it = jtiset.iterator();
        int validCount = 0;
        while (it.hasNext()) {
            String jti = it.next();
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(loginHeader + jti))) {
                validCount++;
            } else {
                // 过期token，自动移除
                stringRedisTemplate.opsForSet().remove(userTokenSetHeader + userId, jti);
            }
        }
        return validCount;
    }


    /**
     * 获取数据
     */
    public Object get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 删除数据
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    

    /**
     * 判断 Key 是否存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key, timeout, unit));
    }

    /**
     * 获取过期时间（秒）
     */
    public long getExpire(String key) {
        Long expire = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire != null ? expire : -1;
    }

    /**
     * 刷新过期时间（不改变 value）
     */
    public boolean refreshExpireToken(String key, String keySet,long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(keySet, defaultExpiration, TimeUnit.SECONDS))
            && Boolean.TRUE.equals(stringRedisTemplate.expire(key, timeout, unit));
    }


    public RecordId sendStreamMessage(String stream, Map<String, String> body) {
        return stringRedisTemplate.opsForStream().add(MapRecord.create(stream, body));
    }

    public StringRedisTemplate gTemplate(){
        return stringRedisTemplate;
    }
    
}
