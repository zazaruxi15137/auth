package com.example.rednote.auth.model.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.persistence.Id;

@Data
@AllArgsConstructor
@NoArgsConstructor
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

    private List<String> permission;
}
