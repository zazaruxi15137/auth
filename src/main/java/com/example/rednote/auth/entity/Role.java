package com.example.rednote.auth.entity;

import jakarta.persistence.*;
import lombok.Data;



@Entity
@Table(name = "role")
@Data
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String name; // 英文标识，如 ROLE_ADMIN

    @Column(nullable = false, length = 50)
    private String displayName; // 展示名

    @Column(length = 255)
    private String remark;
}

