package com.example.rednote.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Email;
import jakarta.persistence.Id;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
public class UserDto implements Serializable {
    @Id
    private Long id;

    @NotBlank
    private String username;

    @NotBlank
    @Email  
    private String email;

    @NotBlank
    private String roles;

    private String token;
}
