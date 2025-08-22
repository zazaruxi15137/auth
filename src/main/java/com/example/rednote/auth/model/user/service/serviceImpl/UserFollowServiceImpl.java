package com.example.rednote.auth.model.user.service.serviceImpl;



import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    @Value("${app.feed.outbox-max-size}")
    private long outboxMaxSize;
    @Value("${app.feed.box-exprir-time}")
    private int exprirTime;
    private volatile String sha2; 
    @Value("${app.feed.follow-bigV-exprir}")
    private long expiration;
    @Value("${app.feed.bigv-threshold}")
    private long bigvThreshold;

    private int followPullSize;
    private final RedisUtil redis;

    @PostConstruct
    public void preloadLua() {
        this.sha2 = redis.gTemplate().execute((RedisCallback<String>) con ->
                con.scriptingCommands().scriptLoad(
                        followPullScript.getScriptAsString().getBytes(StandardCharsets.UTF_8)
                ));
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

                byte[] outboxkey = KeysUtil.redisOutboxKey(followeeId).getBytes(StandardCharsets.UTF_8);
                byte[] inboxkey = KeysUtil.redisInboxKey(followerId).getBytes(StandardCharsets.UTF_8);
                // ARGV 顺序要与 Lua 对齐：member, score, maxSize, expireDays, timeUnit
                byte[] fetchCount = String.valueOf(followPullSize).getBytes(StandardCharsets.UTF_8);
                byte[] maxSz  = String.valueOf(outboxMaxSize).getBytes(StandardCharsets.UTF_8);
                byte[] days   = String.valueOf(exprirTime).getBytes(StandardCharsets.UTF_8);
                byte[] unit   = "ms".getBytes(StandardCharsets.UTF_8); 
                byte[][] keysAndArgs = new byte[][]{
                    outboxkey, // inbox
                    inboxkey, // outbox
                    fetchCount, //拉取笔记数量
                    maxSz,//最大保留笔记条数
                    days,// 保留天数
                    unit// 时间单位默认 ms
                };         // 如果你的 score 用秒，传 "s"
                try{
                    con.scriptingCommands().evalSha(sha2, ReturnType.INTEGER, 2, keysAndArgs);
                    log.info("已经拉取消息放入inbox中");
                }catch(Exception e){
                    log.warn("关注时拉取关注作者作品失败user:{};author:{};err:{}", followerId, followeeId, e.getMessage());
                    // throw new CustomException("笔记发布失败，请稍后再试");
                }
                Counter.builder(MetricsNames.PIPLINE_EXEC_SUCCESS)
                .tag("scene","笔记发布推流成功").register(meter).increment();
                return null;
            });
        } catch (Exception e) {
            Counter.builder(MetricsNames.PIPLINE_EXEC_FAIL)
                .tag("scene","关注作者拉取note")
                .tag(sha2, sha2).register(meter).increment();
                
                log.warn("Pipline:关注作者拉取note失败");
            throw e;
        } finally {
            s.stop(Timer.builder(MetricsNames.PIPLINE_EXEC_TIMER)
                .tag("scene","关注作者拉取note")
                .register(meter));
        }
        }
}

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followingId) {

        userFollowRepository.updateActive(followerId, followingId,false);
        redis.deleteFromSet(KeysUtil.redisFollowedBigVKey(followerId), String.valueOf(followingId));

    }

    @Override
    @Transactional
    public Long countFollowers(Long authorId) {
        return userFollowRepository.countFollowers(authorId);
    }

    @Override
    @Transactional
    public Page<UserFollow> pageFollowers(Long authorId, Pageable page) {
        return userFollowRepository.pageFollowers(authorId, page);
    }

    @Override
    public List<Long> findBigvAuthors(Long userId, long threshold){
        return userFollowRepository.findBigvAuthors(userId, threshold);
    }
}
