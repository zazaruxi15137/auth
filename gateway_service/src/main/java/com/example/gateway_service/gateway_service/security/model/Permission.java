package com.example.gateway_service.gateway_service.security.model;
import org.hibernate.annotations.Comment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "permission")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    @Comment("权限编码")
    private String code; // 权限编码，如 sys:user:add

    @Column(name = "permission_name",nullable = false, length = 50)
    @Comment("权限名称")
    private String permissionName; // 权限名称

    @Column(length = 50, nullable=false)
    @Comment("权限所属模块")
    private String module; // 所属模块

    @Column(length = 255, nullable=false)
    @Comment("Description")
    private String remark;
}

