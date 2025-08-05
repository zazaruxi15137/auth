package com.example.rednote.auth.security.model;

import java.io.Serializable;
import java.util.List;

import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class JwtUser implements Serializable{
    @Id
    private Long id;

    @NotBlank
    private String username;

    @NotBlank
    private String roles;

    private List<String> permission;
}
