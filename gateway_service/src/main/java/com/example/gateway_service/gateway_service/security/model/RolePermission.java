package com.example.gateway_service.gateway_service.security.model;

import lombok.Data;

import org.hibernate.annotations.Comment;

import jakarta.persistence.*;

@Entity
@Table(
    name = "role_permission",
    uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"})
    // indexes = {
    //     @Index(name = "idx_role_permission_permission", columnList = "permission_id")
    // }
)
@Data

public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 角色对象，外键关联
    @ManyToOne
    @JoinColumn(
        name = "role_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @Comment("用户角色id")
    private Role role;

    // 权限对象，外键关联
    @ManyToOne
    @JoinColumn(
        name = "permission_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @Comment("用户权限id")
    private Permission permission;
}
