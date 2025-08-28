package com.example.rednote.auth.model.user.entity;

import java.io.Serializable;
import org.hibernate.annotations.Comment;
import com.example.rednote.auth.model.user.dto.UserDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@AllArgsConstructor
@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_username", columnList = "username"),  
    })

public class User implements Serializable {
    public User(Long userId){
        this.id=userId;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @Comment("用户名")
    private String username;

    @Column(unique = true, nullable = false)
    @Comment("邮箱")
    private String email;

    @Column(nullable = false)
    @Comment("密码")
    private String password;

    @Column(nullable = false)
    @Comment("用户角色")
    private String roles;

    public UserDto toUserDto() {
        UserDto userDto = new UserDto();
        userDto.setId(this.id);
        userDto.setUsername(this.username);
        userDto.setEmail(this.email);
        userDto.setRoles(this.roles);
        return userDto;
    }
    // Getters and Setters
}
