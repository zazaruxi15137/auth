package com.example.gateway_service.gateway_service.common.properties;



import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import lombok.Data;

import java.time.Duration;
import java.util.*;

@Data
@Component
@ConfigurationProperties(prefix = "metrics.probe")
public class MetricsProperties {
    /** 是否开启 */
    private boolean enabled = true;
    /** 任务间隔 */
    private Duration interval = Duration.ofSeconds(30);

    /**
     * 需要观测的 Stream 及其消费组
     * 示例：
     * feed-stream: [ feed-group-a, feed-group-b ]
     * notify-stream: [ notify-group ]
     *
     * key 建议直接写**完整的 Redis Key**，
     * 如果你有 KeysUtil.redisStreamKey(name)，也可以先在 yml 写逻辑名，在 Job 里转成物理 key 使用。
     */
    private Map<String, List<String>> streams = new HashMap<>();

    /** 用于抽样的用户集合（SRANDMEMBER），建议维护活跃用户集 */
    private String inboxUserSetKey = "users:active";

    /** 每次抽样的用户数量 */
    private int inboxSampleUsers = 100;

    /** 每个用户从 inbox 头部取多少条计算停留时长（越大越准，越慢） */
    private int inboxTopnPerUser = 20;
}
