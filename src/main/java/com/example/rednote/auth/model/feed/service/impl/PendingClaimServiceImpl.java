package com.example.rednote.auth.model.feed.service.impl;


import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.common.tool.MetricsNames;
import com.example.rednote.auth.model.feed.handler.NotePushHandler;
import com.example.rednote.auth.model.feed.service.PendingClaimService;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class PendingClaimServiceImpl implements PendingClaimService {
    private final StringRedisTemplate redis;
    private final NotePushHandler handler;
    private final RedisClient redisClient;
    private final MeterRegistry meter;
    @Value("${app.feed.stream.name}") private String stream;
    @Value("${app.feed.stream-group}") private String group;

    @Value("${app.feed.reclaim.idle-seconds}") private long idleSeconds;
    @Value("${app.feed.reclaim.batch-size}") private int batchSize;
    @Value("${app.feed.reclaim.consumer}") private String reclaimerName;

    // 最大“转移次数”
    @Value("${app.feed.reclaim.max-claims}") private int maxClaims;
    @Override
    public void reclaimOnce(String startId) {
        log.info("Reclaiming pending messages...");
        // 每次从 0-0 开始扫描一页（也可以把 nextStartId 存起来做增量，这里保持和你原先一次一页的语义一致）

        // 2) 调用 XAUTOCLAIM
        // 尝试拿到redis底层的连接
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
                RedisCommands<String, String> cmd = conn.sync();
                String nextId = startId;
                while (true) {
                    ClaimedMessages res = xautoClaimAndHandle(cmd, null, nextId);
                    if (res == null || res.getMessages().isEmpty()) break;
                    nextId = incrementRecordId(res.getId()); // Redis 推荐从这个 ID 继续扫
                }
            
        } catch (Exception e) {
            log.error("xautoclaim error:{}", e.getMessage());
        }
    }

   private ClaimedMessages xautoClaimAndHandle(RedisCommands<String, String> single,
                                     RedisClusterCommands<String, String> cluster,
                                     String startId) {

        XAutoClaimArgs args = XAutoClaimArgs.Builder.xautoclaim(
            Consumer.from(group, reclaimerName),
             Duration.ofSeconds(idleSeconds),
              startId);

        // 调用 XAUTOCLAIM
        ClaimedMessages res = (single != null)
                ? single.xautoclaim(stream, args)
                : cluster.xautoclaim(stream, args);

        if (res == null || res.getMessages().isEmpty()){ log.error("sdad{}{}",res==null,res.getMessages().isEmpty()); return null;}
    
        List<StreamMessage<String, String>> list= res.getMessages();
        for (StreamMessage<String, String> msg :list) {
            String recordId = msg.getId();
            Map<String, String> m = msg.getBody();
            String cntKey = KeysUtil.redisRetryKey(stream, recordId);

            // 1) 记录“转移次数”（每次被 reclaimer 成功认领一次 +1）
            long claimCount = Optional.ofNullable(redis.opsForValue().increment(cntKey)).orElse(1L);

            if (claimCount > maxClaims) {
                // 超限：进 DLQ 并 ACK，清计数
                log.warn("Reclaim max claims, id={}, claimCount={}", recordId, claimCount);
                moveToDlqAndAck(m, recordId, cntKey);
                continue;
            }

            try {
                long authorId = Long.parseLong(Objects.toString(m.get("authorId")));
                long noteId   = Long.parseLong(Objects.toString(m.get("noteId")));
                long ts       = Long.parseLong(Objects.toString(m.get("tsMillis")));
                
                handler.handle(authorId, noteId, ts);

                // 成功 → ACK & 清计数（用 Spring 的模板 ACK 即可）
                redis.opsForStream().acknowledge(stream, group, RecordId.of(recordId));
                log.info("Reclaim success, claimCount={}", claimCount);
                redis.delete(cntKey);
            } catch (Exception ex) {
                // 失败 → 不 ACK，继续挂在 reclaimer 的 Pending，等待下一轮再认领（次数会 +1）
                Counter.builder(MetricsNames.APP_RETRY_COUNTER)
                    .tag("where","尝试处理Pending消息失败") // or "db", "pub"
                    .register(meter).increment();
                log.warn("Reclaim handle failed, id={}, claimCount={}", recordId, claimCount, ex);
            }
        }

        // 如需“续扫”，可以把 res.getNextStartId() 存起来下次用
        return res;
    }

    private void moveToDlqAndAck(Map<String, String> body, String recordId, String cntKey) {
        try {
            redis.opsForStream().add(stream + ":dlq", body);                      // 写入死信队列
            redis.opsForStream().acknowledge(stream, group, RecordId.of(recordId)); // ACK 原消息
        } finally {
            redis.delete(cntKey); // 清理转移计数
        }
        log.error("Moved to DLQ due to exceeding max claim times: {}", recordId);
    }
    private String incrementRecordId(String id) {
    // Redis RecordId 格式为 like 1692187612654-0
    String[] parts = id.split("-");
    long millis = Long.parseLong(parts[0]);
    long seq = Long.parseLong(parts[1]);

    // 递增 seq 即可，防止重复
    return millis + "-" + (seq + 1);
}
}
