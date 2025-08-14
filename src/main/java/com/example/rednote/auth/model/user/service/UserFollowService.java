package com.example.rednote.auth.model.user.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.rednote.auth.model.user.entity.UserFollow;

public interface UserFollowService {

    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    public Long countFollowers(Long authorId);

    public Page<UserFollow> pageFollowers(Long followeeId, Pageable pageRequest);


}
