package com.example.rednote.auth.entity;

import jakarta.persistence.*;
import lombok.Data;


@Entity
@Table(name = "permission")
@Data
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String code; // 权限编码，如 sys:user:add

    @Column(nullable = false, length = 50)
    private String name; // 权限名称

    @Column(length = 50)
    private String module; // 所属模块

    @Column(length = 255)
    private String remark;
}

