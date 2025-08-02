package com.example.rednote.auth.dto;

import jakarta.persistence.Entity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.example.rednote.auth.entity.User;
import jakarta.persistence.Id;


@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserDto {
    @Id
    private Long id;

    @NotBlank
    @Size(min = 6, max = 20)
    private String username;
    @NotBlank
    @Size(min = 6, max = 20)
    private String password;
    
    @Email
    private String email;

    public User toUser() {
        User user = new User();
        user.setUsername(this.username);
        user.setPassword(this.password);
        user.setEmail(this.email);
        // 默认角色为 USER
        user.setRoles("USER");
        return user;
    }

}
