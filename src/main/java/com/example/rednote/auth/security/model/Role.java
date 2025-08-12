package com.example.rednote.auth.security.model;

import org.hibernate.annotations.Comment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(
    name = "role"
    )
@Data
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_name", unique = true, nullable = false, length = 50)
    @Comment("角色标识")
    private String roleName; // 英文标识，如 ROLE_ADMIN

    @Column(nullable = false, length = 50)
    @Comment("角色展示名")
    private String displayName; // 展示名

    @Column(length = 255, nullable=false)
    @Comment("Description")
    private String remark;
}

