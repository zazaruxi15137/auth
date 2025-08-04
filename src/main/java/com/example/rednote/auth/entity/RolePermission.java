package com.example.rednote.auth.entity;
import lombok.Data;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;

@Entity
@Table(
    name = "role_permission",
    uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"}),
    indexes = {
        @Index(name = "idx_role_permission_role", columnList = "role_id"),
        @Index(name = "idx_role_permission_permission", columnList = "permission_id")
    }
)
@Data
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 角色对象，外键关联
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_role"))
    @OnDelete(action = OnDeleteAction.CASCADE) // Hibernate注解
    private Role role;

    // 权限对象，外键关联
    @ManyToOne
    @JoinColumn(name = "permission_id", nullable = false, foreignKey = @ForeignKey(name = "fk_permission"))
    @OnDelete(action = OnDeleteAction.CASCADE) // Hibernate注解
    private Permission permission;
}
