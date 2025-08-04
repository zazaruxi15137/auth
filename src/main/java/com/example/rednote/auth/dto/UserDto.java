package com.example.rednote.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.persistence.Id;

@Data
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

    private List<String> permssion;
}
