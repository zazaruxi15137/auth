package com.example.gateway_service.gateway_service.model.user.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.gateway_service.gateway_service.model.user.entity.UserFollow;


public interface UserFollowService {

    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    public Long countFollowers(Long authorId);

    public Page<UserFollow> pageFollowers(Long followeeId, Pageable pageRequest);
    
    List<Long> findBigvAuthors(Long userId, long threshold);


}
