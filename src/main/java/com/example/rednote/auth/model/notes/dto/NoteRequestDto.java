package com.example.rednote.auth.model.notes.dto;


import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteRequestDto implements Serializable {
    private Long id;

    @Schema(description = "笔记标题", example = "text")
    private String title;

    @Schema(description = "笔记内容", example = "text")
    private String content;

    @Schema(description = "用户Id", example = "zhangsan")
    // 外键，关联user表
    @NotNull
    private Long authorId;

    @Schema(description = "图片地址list", example = "[url1,url2]")
    @NotNull
    private List<String> imagesUrls;


}