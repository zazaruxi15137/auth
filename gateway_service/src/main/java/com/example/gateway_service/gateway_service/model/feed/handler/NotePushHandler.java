package com.example.gateway_service.gateway_service.model.feed.handler;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.example.gateway_service.gateway_service.common.exception.CustomException;
import com.example.gateway_service.gateway_service.common.tool.KeysUtil;
import com.example.gateway_service.gateway_service.common.tool.MetricsNames;
import com.example.gateway_service.gateway_service.common.tool.SerializaUtil;
import com.example.gateway_service.gateway_service.model.user.entity.UserFollow;
import com.example.gateway_service.gateway_service.model.user.service.UserFollowService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotePushHandler {

    private final StringRedisTemplate redis;
    @Qualifier("inboxUpsertScript")
    private final DefaultRedisScript<Long> inboxUpsertScript;
    private final UserFollowService userFollowService;
    private final MeterRegistry meter;

    @Value("${app.feed.batch-size}") private int batchSize;
    @Value("${app.feed.inbox-max-size}") private int inboxMaxSize;
    @Value("${app.feed.dedup-ttl-seconds}") private int dedupTtlSeconds;
    @Value("${app.feed.box-exprir-time}") private int exprirTime;  
    @Value("${app.shard}")private int shard;                                                         
    

    private volatile String sha1;

    @PostConstruct
    public void preloadLua() {
        
        this.sha1 = redis.execute((RedisCallback<String>) con ->
                con.scriptingCommands().scriptLoad(
                        inboxUpsertScript.getScriptAsString().getBytes(StandardCharsets.UTF_8)
                ));
    }

    public void handle(long authorId, long noteId, long ts) {

        int page = 0;
        while (true) {
            var p = userFollowService.pageFollowers(authorId, PageRequest.of(page, batchSize));
            if (p.isEmpty()) break;
            List<Long> uids = p.map(UserFollow::getFollowerId).toList();
            pushBatch(uids, authorId, noteId, ts);
            page++;
            if (!p.hasNext()) break;
        }
    }

    private void pushBatch(List<Long> userIds,long authorId, long noteId, long tsMillis) {
        try {
            doPushBatch(userIds, authorId, noteId, tsMillis, sha1);
        } catch (Exception ex) {
            try{
                log.error("笔记{}批量推送失败用户id为{}",noteId, SerializaUtil.toJson(userIds));
            }catch(JsonProcessingException e){
                log.error("内部错误，无法json化推送失败的用户id{}", e.getMessage());
            }
                throw new CustomException("取消继续推送，等待回收线程重试");

            
        }
    }

    private void doPushBatch(List<Long> userIds ,long authorId , long noteId, long tsMillis, String sha1) {
        Timer.Sample s = Timer.start(meter);
        try {
        redis.executePipelined((RedisCallback<Object>) con -> {
            for (Long uid : userIds) {
                byte[] k1 = KeysUtil.redisInboxKey(uid, uid % shard).getBytes(StandardCharsets.UTF_8);
                byte[] k2 = KeysUtil.redisPushDedupKey(noteId, uid).getBytes(StandardCharsets.UTF_8);
                byte[] k3 = KeysUtil.redisInboxAuthorIndex(uid, authorId).getBytes(StandardCharsets.UTF_8);
                byte[] a1 = String.valueOf(tsMillis).getBytes(StandardCharsets.UTF_8); // sorce
                byte[] a2 = String.valueOf(noteId).getBytes(StandardCharsets.UTF_8);
                byte[] a3 = String.valueOf(inboxMaxSize).getBytes(StandardCharsets.UTF_8); //box最大尺寸
                byte[] a4 = String.valueOf(dedupTtlSeconds).getBytes(StandardCharsets.UTF_8); //去重键保留时间
                byte[] days   = String.valueOf(exprirTime).getBytes(StandardCharsets.UTF_8); //过期时间以及note保留时间
                byte[] unit   = "ms".getBytes(StandardCharsets.UTF_8); //默认毫秒 可以传入s、ms

                byte[][] keysAndArgs = new byte[][]{ k1, k2, k3, a1, a2, a3, a4, days, unit};
                try{
                    con.scriptingCommands().evalSha(sha1, ReturnType.INTEGER, 3, keysAndArgs);
                }catch(Exception e){
                    log.error("push note {} to user {} failed,because:{}", noteId, uid, e.getMessage());
                }
            }
            log.info("pushed note {} to {} users", noteId, userIds.size());
            Counter.builder(MetricsNames.PIPLINE_EXEC_SUCCESS)
                .tag("scene","note推送成功").register(meter).increment();
            return null;
        });
        } catch (Exception e) {
            Counter.builder(MetricsNames.PIPLINE_EXEC_FAIL)
                .tag("scene","note推送失败").register(meter).increment();
            log.warn("Pipline执行失败，note推送失败{}", e.getMessage());
            throw e;
        } finally {
            s.stop(Timer.builder(MetricsNames.PIPLINE_EXEC_TIMER)
                .tag("scene","note推送到ibox")
                .register(meter));
        }
    }
}
