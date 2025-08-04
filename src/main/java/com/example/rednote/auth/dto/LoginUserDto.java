package com.example.rednote.auth.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import jakarta.persistence.Id;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class LoginUserDto implements Serializable {

    @Id
    private Long id;
    @Schema(description = "用户名", example = "zhangsan")
    @NotBlank
    @Size(min = 6, max = 20)
    private String username;
    @Schema(description = "密码", example = "******")
    @NotBlank
    @Size(min = 6, max = 20)
    private String password;
}
