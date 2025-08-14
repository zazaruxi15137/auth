package com.example.rednote.auth.model.user.service.serviceImpl;



import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.rednote.auth.common.tool.RedisUtil;
import com.example.rednote.auth.model.user.entity.UserFollow;
import com.example.rednote.auth.model.user.repository.UserFollowRepository;
import com.example.rednote.auth.model.user.service.UserFollowService;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class UserFollowServiceImpl  implements UserFollowService {
    private final UserFollowRepository userFollowRepository;

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
        // // 可选：把粉丝放入缓存集合，便于高频作者读取（只缓存活跃粉丝也行）
        // redis.setToSet(KeysUtil.redisFollowersCacheKey(followeeId), String.valueOf(followerId),7,TimeUnit.DAYS);
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followingId) {

        userFollowRepository.updateActive(followerId, followingId,false);
        // redis.deleteFromSet(KeysUtil.redisFollowersCacheKey(followerId), String.valueOf(followingId));

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
    
}
