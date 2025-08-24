package com.example.rednote.auth.model.user.service.serviceImpl;



import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.TransactionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.*;
import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.common.tool.MetricsNames;
import com.example.rednote.auth.common.tool.RedisUtil;
import com.example.rednote.auth.model.user.entity.UserFollow;
import com.example.rednote.auth.model.user.repository.UserFollowRepository;
import com.example.rednote.auth.model.user.service.UserFollowService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Service
@Slf4j
@RequiredArgsConstructor
public class UserFollowServiceImpl  implements UserFollowService {
    private final UserFollowRepository userFollowRepository;
    private final MeterRegistry meter;

    @Qualifier("followPullScript")
    private final DefaultRedisScript<Long> followPullScript;
    private DefaultRedisScript<Long> unfollowed;
    @Value("${app.feed.outbox-max-size}")
    private long outboxMaxSize;
    @Value("${app.feed.inbox-max-size}")
    private long inboxMaxSize;
    @Value("${app.feed.box-exprir-time}")
    private int exprirTime;
    private volatile String sha2; 
    @Value("${app.feed.follow-bigV-exprir}")
    private long expiration;
    @Value("${app.feed.bigv-threshold}")
    private long bigvThreshold;
    @Value("${app.shard}")
    private int shard;
    @Value("${app.feed.follow-pull-size}")
    private int followPullSize;
    private final RedisUtil redis;

    @PostConstruct
    public void preloadLua() {
        this.sha2 = redis.gTemplate().execute((RedisCallback<String>) con ->
                con.scriptingCommands().scriptLoad(
                        followPullScript.getScriptAsString().getBytes(StandardCharsets.UTF_8)
                ));
        DefaultRedisScript<Long> unfollowed=new DefaultRedisScript<>();
        unfollowed.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/unfollowBlacklist.lua")));
        unfollowed.setResultType(Long.class);
        this.unfollowed=unfollowed;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(
        Long followerId, //用户关注者 
        Long followeeId //作者被关注者
        ) {
        if (userFollowRepository.existsByFollowerIdAndFolloweeIdAndActive(followerId, followeeId, true)) {
            return;
        }
        // 插入或更新为关注
        int updated = userFollowRepository.updateActive(followerId, followeeId, true);
        if (updated == 0) {
            UserFollow f = UserFollow.builder()
                    .followerId(followerId)
                    .followeeId(followeeId)
                    .active(true)
                    .build();
            userFollowRepository.save(f);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("{}",redis.gTemplate().opsForZSet().zCard(KeysUtil.redisInboxKey(followerId, followeeId % shard)));
                long followerCount = countFollowers(followeeId);
                if (followerCount >= bigvThreshold) {
                    // // 把作者放入粉丝的关注集合，便于拉取大v数据。
                    redis.setToSet(
                        KeysUtil.redisFollowedBigVKey(followerId), 
                        String.valueOf(followeeId), 
                        expiration, 
                        TimeUnit.HOURS
                        );
                        log.info("关注大V，将作者放入关注大V集合中");
                    }else{
                        Timer.Sample s = Timer.start(meter);
                        try {
                            //普通作者从发件箱中拉取笔记放入inbox中
                            redis.gTemplate().executePipelined((RedisCallback<Object>) con -> {
                            byte[] outboxkey = KeysUtil.redisOutboxKey(followeeId%shard, followeeId).getBytes(StandardCharsets.UTF_8);
                            byte[] inboxkey = KeysUtil.redisInboxKey(followerId, followerId%shard).getBytes(StandardCharsets.UTF_8);
                            byte[] indexKey = KeysUtil.redisInboxAuthorIndex(followerId, followeeId).getBytes(StandardCharsets.UTF_8);
                            // ARGV 顺序要与 Lua 对齐：member, score, maxSize, expireDays, timeUnit
                            byte[] fetchCount = String.valueOf(followPullSize).getBytes(StandardCharsets.UTF_8);
                            byte[] maxSz  = String.valueOf(outboxMaxSize).getBytes(StandardCharsets.UTF_8);
                            byte[] days   = String.valueOf(exprirTime).getBytes(StandardCharsets.UTF_8);
                            byte[] unit   = "ms".getBytes(StandardCharsets.UTF_8); 
                            byte[][] keysAndArgs = new byte[][]{
                                outboxkey, // inbox
                                inboxkey, // outbox
                                indexKey,
                                fetchCount, //拉取笔记数量
                                maxSz,//最大保留笔记条数
                                days,// 保留天数
                                unit// 时间单位默认 ms
                            };         // 如果你的 score 用秒，传 "s"
                                con.scriptingCommands().evalSha(sha2, ReturnType.INTEGER, 3, keysAndArgs);
                                log.info("{}",redis.gTemplate().opsForZSet().zCard(KeysUtil.redisInboxKey(followerId, followerId % shard)));
                                log.info("已经拉取消息放入inbox中");
                            return null;
                        });
                    } catch (Exception e) {
                        Counter.builder(MetricsNames.PIPLINE_EXEC_FAIL)
                            .tag("scene","关注作者拉取note")
                            .tag(sha2, sha2).register(meter).increment();
                            log.warn("Pipline:关注作者拉取note失败");
                        throw e;
                    } finally {
                        Counter.builder(MetricsNames.PIPLINE_EXEC_SUCCESS)
                            .tag("scene","笔记发布推流成功").register(meter).increment();
                        s.stop(Timer.builder(MetricsNames.PIPLINE_EXEC_TIMER)
                            .tag("scene","关注作者拉取note")
                            .register(meter));
                    }
                    }
                        }
                    });
            }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        userFollowRepository.updateActive(followerId, followingId,false);
        long followerCount = countFollowers(followingId);
        //大V删除缓存的id
        if (followerCount >= bigvThreshold) {
            redis.deleteFromSet(KeysUtil.redisFollowedBigVKey(followerId), String.valueOf(followingId));
        }else{
            //非大V将发件箱中的笔记加入黑名单按时间和长度裁剪。
            long res=redis
                .gTemplate()
                .execute(
                    unfollowed,
                    List.of(KeysUtil.redisInboxKey(followerId, followerId % shard),
                    KeysUtil.redisInboxAuthorIndex(followerId, followingId)),
                    String.valueOf(255),
                    String.valueOf(1));
            log.info("{}",redis.gTemplate().opsForZSet().zCard(KeysUtil.redisInboxKey(followerId, followerId % shard)));
            log.info("已将{}条note从inbox中删除",res);
        }
    }

    @Override
    public Long countFollowers(Long authorId) {
        return userFollowRepository.countFollowers(authorId);
    }

    @Override
    public Page<UserFollow> pageFollowers(Long authorId, Pageable page) {
        return userFollowRepository.pageFollowers(authorId, page);
    }

    @Override
    public List<Long> findBigvAuthors(Long userId, long threshold){
        return userFollowRepository.findBigvAuthors(userId, threshold);
    }
}
