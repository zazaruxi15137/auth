package com.example.gateway_service.gateway_service.common.properties;



import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {
  /** 默认桶容量、速率、空闲 TTL */
  private int defaultCap = 100;
  private int defaultRatePerSec = 50;
  private int defaultIdleTtlSec = 600;

  /** 对不同 path 的 cost（支持 Ant 风格或前缀匹配，下面过滤器里简单前缀匹配示例） */
  private Map<String, Integer> pathCost = new HashMap<>();

  /** 是否按“用户+接口”维度开桶；false=只按用户/IP 维度 */
  private boolean perApiBucket = false;

  /** 对未登录接口，是否优先按 IP 限流 */
  private boolean ipFirstForAnonymous = true;

  /** 将哪些路径豁免限流 */
  private String[] ignorePaths = new String[]{"/actuator", "/swagger", "/v3/api-docs"};
}
