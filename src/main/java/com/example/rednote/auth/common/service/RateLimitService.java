package com.example.rednote.auth.common.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.common.tool.SerializaUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

  private final StringRedisTemplate redis;
  @Qualifier("rateLimitScript")
  private final DefaultRedisScript<List> rateLimitScript;

  public boolean isUserBlacklisted(long userId) {
    if (userId <= 0) return false;
    Boolean hit = redis.hasKey(KeysUtil.blacklistUser(userId));
    return Boolean.TRUE.equals(hit);
  }

  public boolean isIpBlacklisted(String ip) {
    Boolean hit = redis.hasKey(KeysUtil.blacklistIp(ip));
    return Boolean.TRUE.equals(hit);
  }

  public RateResult tryConsumeUser(long userId, String apiTag, int cost,
                                   int defCap, int defRate, int defIdle) {
    String bucket = KeysUtil.bucketKeyByUser(userId);
    String conf   = KeysUtil.confKeyByUser(userId);
    return eval(bucket, conf, cost, defCap, defRate, defIdle);
  }

  public RateResult tryConsumeIp(String ip, String apiTag, int cost,
                                 int defCap, int defRate, int defIdle) {
    String bucket = KeysUtil.bucketKeyByIp(ip);
    String conf   = KeysUtil.confKeyByIp(ip);
    return eval(bucket, conf, cost, defCap, defRate, defIdle);
  }

  private RateResult eval(String bucketKey, String confKey, int cost,
                          int defCap, int defRate, int defIdle) {
    long now = System.currentTimeMillis();
    List<?> r = redis.execute(rateLimitScript,
        Arrays.asList(bucketKey, confKey),
        String.valueOf(now), String.valueOf(cost),
        String.valueOf(defCap), String.valueOf(defRate), String.valueOf(defIdle));

    int allowed = parseInt(r, 0, 1);
    int waitMs  = parseInt(r, 2, 0);
    int cap     = parseInt(r, 3, defCap);
    int rate    = parseInt(r, 4, defRate);
    
    return new RateResult(allowed == 1, waitMs, cap, rate);
  }

  private int parseInt(List<?> r, int idx, int defVal) {
    if (r == null || r.size() <= idx || r.get(idx) == null) return defVal;
    try { return (int) Double.parseDouble(r.get(idx).toString()); }
    catch (Exception e) { return defVal; }
  }

  /** 结果对象 */
  public record RateResult(boolean allowed, int waitMs, int cap, int rate) {}

  /** 管理：覆盖配置（为空的字段不改） */
  public void setUserConf(long userId, Integer cap, Integer rate, Integer idleSec) {

    String conf = KeysUtil.confKeyByUser(userId);
    if (cap != null)  redis.opsForHash().put(conf, "cap", String.valueOf(cap));
    if (rate != null) redis.opsForHash().put(conf, "rate", String.valueOf(rate));
    if (idleSec != null) redis.opsForHash().put(conf, "idle_ttl", String.valueOf(idleSec));

  }

  public void setIpConf(String ip, Integer cap, Integer rate, Integer idleSec) {
    
    String conf = KeysUtil.confKeyByIp(ip);
    if (cap != null)  redis.opsForHash().put(conf, "cap", String.valueOf(cap));
    if (rate != null) redis.opsForHash().put(conf, "rate", String.valueOf(rate));
    if (idleSec != null) redis.opsForHash().put(conf, "idle_ttl", String.valueOf(idleSec));
  }

  /** 管理：清掉覆盖，回到默认 */
  public void clearUserConf(long userId) { redis.delete(KeysUtil.confKeyByUser(userId)); }
  public void clearIpConf(String ip)     { redis.delete(KeysUtil.confKeyByIp(ip)); }

  /** 管理：黑名单（秒级 TTL），ttl<=0 则永久，传 null 走默认 */
  public void blacklistUser(long userId, Integer ttlSec) {
    if (ttlSec == null || ttlSec <= 0) redis.opsForValue().set(KeysUtil.blacklistUser(userId), "1");
    else redis.opsForValue().set(KeysUtil.blacklistUser(userId), "1", java.time.Duration.ofSeconds(ttlSec));
  }
  public void unblacklistUser(long userId) { redis.delete(KeysUtil.blacklistUser(userId)); }

  public void blacklistIp(String ip, Integer ttlSec) {
    if (ttlSec == null || ttlSec <= 0) redis.opsForValue().set(KeysUtil.blacklistIp(ip), "1");
    else redis.opsForValue().set(KeysUtil.blacklistIp(ip), "1", java.time.Duration.ofSeconds(ttlSec));
  }
  public void unblacklistIp(String ip) { redis.delete(KeysUtil.blacklistIp(ip)); }
}
