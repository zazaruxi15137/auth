package com.example.rednote.auth.model.user.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.example.rednote.auth.model.user.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Id;



@Data
public class RegisterUserDto {
    @Schema(description = "用户名", example = "zhangsan")
    @NotBlank
    @Size(min = 6, max = 20)
    private String username;
    @NotBlank
    @Schema(description = "密码", example = "******")
    @Size(min = 6, max = 20)
    private String password;
    @Schema(description = "邮箱", example = "test@domain.com")
    @Email
    private String email;

    public User toUser() {
        User user = new User();
        user.setUsername(this.username);
        user.setPassword(this.password);
        user.setEmail(this.email);
        return user;
    }

}
