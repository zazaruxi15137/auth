package com.example.rednote.auth.model.user.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_follow",
    uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(columnNames = {"follower_id", "followee_id"})
    },
    indexes = {
        @jakarta.persistence.Index(columnList = "followee_id")
    })
@Builder
public class UserFollow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关注发起者（粉丝） */
    @Column(nullable = false)
    private Long followerId;

    /** 被关注者（偶像） */
    @Column(nullable = false)
    private Long followeeId;

    /** 关注时间戳 */
    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime followTime;

    /** 是否已取消关注 */
    @Column(nullable = false)
    private Boolean active;

}
