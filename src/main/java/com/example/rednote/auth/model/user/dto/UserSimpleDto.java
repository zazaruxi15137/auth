package com.example.rednote.auth.model.user.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class UserSimpleDto implements Serializable {
        
    private Long id;
    private String username;
    private String email;
    // ...
}
