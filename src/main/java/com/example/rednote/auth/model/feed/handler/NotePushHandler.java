package com.example.rednote.auth.model.feed.handler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.model.user.entity.UserFollow;
import com.example.rednote.auth.model.user.service.UserFollowService;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotePushHandler {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> inboxUpsertScript;
    private final UserFollowService userFollowService;

    @Value("${app.feed.batch-size}") private int batchSize;
    @Value("${app.feed.inbox-max-size}") private int inboxMaxSize;
    @Value("${app.feed.dedup-ttl-seconds}") private int dedupTtlSeconds;
    

    private volatile String sha1;

    @PostConstruct
    public void preloadLua() {
        this.sha1 = redis.execute((RedisCallback<String>) con ->
                con.scriptingCommands().scriptLoad(
                        inboxUpsertScript.getScriptAsString().getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    public void handle(long authorId, long noteId, long ts) {

        int page = 0;
        while (true) {
            var p = userFollowService.pageFollowers(authorId, PageRequest.of(page, batchSize));
            if (p.isEmpty()) break;
            List<Long> uids = p.map(UserFollow::getFollowerId).toList();
            pushBatch(uids, noteId, ts);
            page++;
            if (!p.hasNext()) break;
        }
    }

    private void pushBatch(List<Long> userIds, long noteId, long tsMillis) {
        try {
            doPushBatch(userIds, noteId, tsMillis, sha1);
        } catch (RedisSystemException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("NOSCRIPT")) {
                String reloaded = redis.execute((RedisCallback<String>) con ->
                        con.scriptingCommands().scriptLoad(
                                inboxUpsertScript.getScriptAsString().getBytes(StandardCharsets.UTF_8)
                        )
                );
                this.sha1 = reloaded;
                doPushBatch(userIds, noteId, tsMillis, reloaded);
            } else {
                throw ex;
            }
        }
    }

    private void doPushBatch(List<Long> userIds, long noteId, long tsMillis, String sha1) {
        redis.executePipelined((RedisCallback<Object>) con -> {
            for (Long uid : userIds) {
                byte[] k1 = KeysUtil.redisInboxKey(uid).getBytes(StandardCharsets.UTF_8);
                byte[] k2 = KeysUtil.redisPushDedupKey(noteId, uid).getBytes(StandardCharsets.UTF_8);
                byte[] a1 = String.valueOf(tsMillis).getBytes(StandardCharsets.UTF_8);
                byte[] a2 = String.valueOf(noteId).getBytes(StandardCharsets.UTF_8);
                byte[] a3 = String.valueOf(inboxMaxSize).getBytes(StandardCharsets.UTF_8);
                byte[] a4 = String.valueOf(dedupTtlSeconds).getBytes(StandardCharsets.UTF_8);
                byte[][] keysAndArgs = new byte[][]{ k1, k2, a1, a2, a3, a4 };
                try{
                    con.scriptingCommands().evalSha(sha1, ReturnType.INTEGER, 2, keysAndArgs);
                }catch(Exception e){
                    log.error("push note {} to user {} failed,because:{}", noteId, uid, e.getMessage());
                }
            }
            log.info("pushed note {} to {} users", noteId, userIds.size());
            return null;
        });
    }
}
