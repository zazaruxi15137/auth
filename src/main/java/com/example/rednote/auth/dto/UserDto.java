package com.example.rednote.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Email;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class UserDto {

    private Long id;

    @NotBlank
    @Size(min = 3, max = 200)
    private String password;

    @NotBlank
    @Size(min = 3, max = 20)
    private String username;

    @NotBlank
    @Email  
    private String email;



}
