package com.example.gateway_service.gateway_service.model.user.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.gateway_service.gateway_service.model.user.entity.UserFollow;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    @Query("select f from UserFollow f where f.followeeId = :followeeId and f.active = true")
    Page<UserFollow> pageFollowers(Long followeeId, Pageable pageable);

    @Query("select count(f) from UserFollow f where f.followeeId = :followeeId and f.active = true")
    long countFollowers(Long followeeId);

    @Modifying
    @Query("update UserFollow f set f.active = :active where f.followerId=:followerId and f.followeeId=:followeeId")
    int updateActive(Long followerId, Long followeeId, Boolean active);


    boolean existsByFollowerIdAndFolloweeIdAndActive(Long followerId, Long followeeId, Boolean active);

    @Query("SELECT f.followeeId FROM UserFollow f " +
       "WHERE f.followerId = :userId AND f.active = true " +
       "AND (SELECT COUNT(*) FROM UserFollow f2 WHERE f2.followeeId = f.followeeId) >= :threshold")
    List<Long> findBigvAuthors(@Param("userId") Long userId, @Param("threshold") long threshold);
}
