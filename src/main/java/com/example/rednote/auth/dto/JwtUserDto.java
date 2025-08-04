package com.example.rednote.auth.dto;

import java.io.Serializable;
import java.util.List;

import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class JwtUserDto implements Serializable{
    @Id
    private Long id;

    @NotBlank
    private String username;

    @NotBlank
    private String roles;

    private List<String> permission;
}
