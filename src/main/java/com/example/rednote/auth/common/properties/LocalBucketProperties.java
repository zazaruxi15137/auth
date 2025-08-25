package com.example.rednote.auth.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.example.rednote.auth.common.service.BlockingTokenBucket;

@Data
@Component
@ConfigurationProperties(prefix = "local.bucket")
public class LocalBucketProperties {
    /** 桶容量 */
    private int capacity = 200;
    /** 令牌速率（每秒） */
    private double ratePerSecond = 100.0;
    /** 补充周期（毫秒） */
    private long refillPeriodMs = 50;
    /** 获取令牌最长等待（毫秒，<=0 表示无限阻塞） */
    private long acquireTimeoutMs = 2000;
    /** 是否启用（方便灰度） */
    private boolean enabled = true;
}

@Configuration
class LocalBucketConfig {
    @Bean(destroyMethod = "close")
    BlockingTokenBucket blockingTokenBucket(LocalBucketProperties p) {
        return new BlockingTokenBucket(p.getCapacity(), p.getRatePerSecond(), p.getRefillPeriodMs());
    }
}
