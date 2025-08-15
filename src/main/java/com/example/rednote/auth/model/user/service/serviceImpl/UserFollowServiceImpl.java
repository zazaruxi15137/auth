package com.example.rednote.auth.model.user.service.serviceImpl;



import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.common.tool.RedisUtil;
import com.example.rednote.auth.model.user.entity.UserFollow;
import com.example.rednote.auth.model.user.repository.UserFollowRepository;
import com.example.rednote.auth.model.user.service.UserFollowService;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class UserFollowServiceImpl  implements UserFollowService {
    private final UserFollowRepository userFollowRepository;
    @Value("${app.feed.follow-bigV-exprir}")
    private long expiration;
    @Value("${app.feed.bigv-threshold}")
    private long bigvThreshold;
    private final RedisUtil redis;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long followerId, Long followeeId) {
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
        // // 可选：把粉丝放入缓存集合，便于高频作者读取（只缓存活跃粉丝也行）
        redis.setToSet(
            KeysUtil.redisFollowedBigVKey(followerId), 
            String.valueOf(followeeId), 
            expiration, 
            TimeUnit.HOURS
            );
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
