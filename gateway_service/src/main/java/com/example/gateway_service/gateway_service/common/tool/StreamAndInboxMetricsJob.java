package com.example.gateway_service.gateway_service.common.tool;


import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.gateway_service.gateway_service.common.properties.MetricsProperties;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class StreamAndInboxMetricsJob {

    private final StringRedisTemplate redis;
    private final MeterRegistry meter;
    private final MetricsProperties props;

    /** 用于 Gauge 的稳定持有对象，避免重复注册导致的指标泄漏 */
    private final Map<String, AtomicLong> gaugeHolders = new ConcurrentHashMap<>();

    /** 收件箱停留时长（毫秒）的分布指标（直方图） */
    private volatile DistributionSummary inboxStayMsSummary;

    private AtomicLong gauge(String name, Iterable<Tag> tags) {
        final String key = name + "|" + Tags.of(tags).stream()
                .map(t -> t.getKey() + "=" + t.getValue()).reduce((a,b)->a+"|"+b).orElse("");
        return gaugeHolders.computeIfAbsent(key, k -> {
            AtomicLong holder = new AtomicLong(0);
            Gauge.builder(name, holder, AtomicLong::get)
                    .tags(tags)
                    .register(meter);
            return holder;
        });
    }

    // private DistributionSummary inboxStaySummary() {
    //     if (inboxStayMsSummary == null) {
    //         synchronized (this) {
    //             if (inboxStayMsSummary == null) {
    //                 inboxStayMsSummary = DistributionSummary.builder(MetricsNames.INBOX_STAY_MS)
    //                         .baseUnit("milliseconds")
    //                         .publishPercentileHistogram() // 导出 _bucket，Grafana 用 histogram_quantile
    //                         .register(meter);
    //             }
    //         }
    //     }
    //     return inboxStayMsSummary;
    // }

    /** 主任务：固定间隔执行（见 yml 配置） */
    @Scheduled(fixedDelayString = "${metrics.probe.interval}") // 默认30s
    public void run() {
        if (!props.isEnabled()) return;
        // Timer.Sample sample = Timer.start(meter);
        try {
            probeStreams();
            // probeInboxLatency();
        } catch (Exception e) {
            // meter.counter(MetricsNames.METRICS_PROBE_ERRORS_TOTAL).increment();
            log.warn("metrics probe error", e);
        } finally {
            // sample.stop(Timer.builder(MetricsNames.METRICS_PROBE_TIMER).register(meter));
        }
    }

    /** 1) 统计 Redis Stream 的堆积：XLEN + 各消费组 XPENDING */
    private void probeStreams() {
        if (props.getStreams() == null || props.getStreams().isEmpty()) return;

        props.getStreams().forEach((streamKey, groups) -> {
            // 如果你有 KeysUtil 的 streamKey 包装，这里可替换为 KeysUtil.redisStreamKey(streamKey)
            final String key = streamKey;

            long xlen = 0L;
            try {
                Long size = redis.opsForStream().size(key);
                xlen = (size != null ? size : 0L);
            } catch (Exception ignore) {}
            gauge(MetricsNames.STREAM_LENGTH, Tags.of("stream", key)).set(xlen);

            if (groups != null) {
                for (String group : groups) {
                    long pending = xPendingCount(key, group);
                    gauge(MetricsNames.STREAM_PENDING, Tags.of("stream", key, "group", group)).set(pending);
                }
            }
        });
    }

    /** 低层执行 XPENDING 统计总数（不同 Spring Data 版本返回结构不一，统一成 long） */
    private long xPendingCount(String stream, String group) {
        PendingMessagesSummary sum =redis.opsForStream().pending(stream, group);
        return sum==null? 0L:sum.getTotalPendingMessages();
    }

//     /** 2) 随机抽样用户，估算收件箱停留时长（毫秒） */
//     private void probeInboxLatency() {
//         List<Long> uids = sampleUsers(props.getInboxUserSetKey(), props.getInboxSampleUsers());
//         gauge(MetricsNames.INBOX_SAMPLED_USERS, Tags.empty()).set(uids.size());

//         if (uids.isEmpty()) return;

//         long now = System.currentTimeMillis();
//         int perUserTopN = Math.max(1, props.getInboxTopnPerUser());

//         long totalMsgs = 0;
//         long totalStayMs = 0;

//         for (Long uid : uids) {
//             // 使用 KeysUtil 生成 inbox key（请确认你项目里的方法名；没有就按下面新增）
//             String inboxKey = KeysUtil.redisFeedInboxKey(uid); // 需在 KeysUtil 中提供

//             Set<ZSetOperations.TypedTuple<String>> tuples =
//                     redis.opsForZSet().rangeWithScores(inboxKey, 0, perUserTopN - 1);
//             if (tuples == null || tuples.isEmpty()) continue;

//             for (ZSetOperations.TypedTuple<String> t : tuples) {
//                 Double score = t.getScore();
//                 if (score == null) continue;
//                 long enqueuedMs = score.longValue(); // 你的 score 就是毫秒时间戳
//                 long stay = Math.max(0L, now - enqueuedMs);
//                 inboxStaySummary().record(stay); // 记录到直方图分布
//                 totalMsgs++;
//                 totalStayMs += stay;
//             }
//         }

//         gauge(MetricsNames.INBOX_SAMPLED_MESSAGES, Tags.empty()).set(totalMsgs);
//         long avg = (totalMsgs > 0 ? Math.round((double) totalStayMs / totalMsgs) : 0L);
//         gauge(MetricsNames.INBOX_AVG_STAY_MS, Tags.empty()).set(avg);
//     }

//     /** 从 Redis Set 随机抽用户ID（用 SRANDMEMBER） */
//     private List<Long> sampleUsers(String setKey, int count) {
//         try {
//             List<String> items = redis.opsForSet().randomMembers(setKey, count);
//             if (items == null || items.isEmpty()) return List.of();
//             List<Long> ids = new ArrayList<>(items.size());
//             for (String s : items) {
//                 try { ids.add(Long.parseLong(s)); } catch (NumberFormatException ignore) {}
//             }
//             return ids;
//         } catch (Exception e) {
//             log.debug("sample users failed", e);
//             return List.of();
//         }
//     }
}