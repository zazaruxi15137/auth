package com.example.rednote.auth.model.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.example.rednote.auth.model.user.entity.UserFollow;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    @Query("select f from UserFollow f where f.followeeId = :followeeId and f.active = true")
    Page<UserFollow> pageFollowers(Long followeeId, Pageable pageable);

    @Query("select count(f) from UserFollow f where f.followeeId = :followeeId and f.active = true")
    long countFollowers(Long followeeId);

    @Modifying
    @Query("update UserFollow f set f.active = :active where f.followerId=:followerId and f.followeeId=:followeeId")
    int updateActive(Long followerId, Long followeeId, Boolean active);


    boolean existsByFollowerIdAndFolloweeIdAndActive(Long followerId, Long followeeId, Boolean active);
}
