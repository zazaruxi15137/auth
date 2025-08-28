package com.example.rednote.auth.security.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JwtUser implements UserDetails{
    @Id
    private Long id;

    @NotBlank
    private String username;

    @NotBlank
    private String roles;

    private String password;


    private List<? extends GrantedAuthority> authorities;

}
