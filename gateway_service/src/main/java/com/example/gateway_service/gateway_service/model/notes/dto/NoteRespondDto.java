package com.example.gateway_service.gateway_service.model.notes.dto;

import java.io.Serializable;
import java.util.List;

import com.example.gateway_service.gateway_service.model.user.dto.UserSimpleDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteRespondDto implements Serializable {

    private Long id;


    private String title;


    private String content;

    // 外键，关联user表

    private UserSimpleDto userSimpleDto;

    private String publishTime;

    private List<String> imagesUrls;



}

