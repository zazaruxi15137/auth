package com.example.rednote.auth.entity;

import java.io.Serializable;

import org.hibernate.resource.jdbc.LogicalConnection;

import com.example.rednote.auth.dto.LoginUserDto;
import com.example.rednote.auth.dto.UserDto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
@Table(name = "users")
public class User implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String roles;

    public UserDto toUserDto() {
        UserDto userDto = new UserDto();
        userDto.setId(this.id);
        userDto.setUsername(this.username);
        userDto.setEmail(this.email);
        userDto.setRoles(this.roles);
        return userDto;
    }
    public LoginUserDto toLoginUserDto() {
        LoginUserDto loginUserDto = new LoginUserDto();
        loginUserDto.setId(this.id);
        loginUserDto.setUsername(this.username);
        loginUserDto.setPassword(this.password);
        return loginUserDto;
    }

    // Getters and Setters
}
