package com.example.rednote.auth.model.notes.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.rednote.auth.common.RespondMessage;
import com.example.rednote.auth.common.tool.FlieUtil;
import com.example.rednote.auth.common.tool.SerializaUtil;
import com.example.rednote.auth.model.notes.dto.NoteRequestDto;
import com.example.rednote.auth.model.notes.dto.NoteRespondDto;
import com.example.rednote.auth.model.notes.entity.Note;
import com.example.rednote.auth.model.notes.service.NoteService;
import com.example.rednote.auth.model.user.entity.User;
import com.example.rednote.auth.security.model.JwtUser;
import com.example.rednote.auth.security.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties.Jwt;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;




@Tag(name = "笔记管理", description = "笔记相关API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/")
public class NoteController {

    private final NoteService noteService;
    private final JwtUtil jwtUtil;
    @Value("${jwt.header}")
    private String requestHeader;
    @Value("${jwt.prefix}")
    private String prefix;


    @Operation(summary = "上传笔记（含图片）", description = "需要具有业务数据上传权限，同时支持图片上传")
@PreAuthorize("hasAuthority('sys:data:upload')")
@PostMapping(value = "/notes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public RespondMessage<NoteRespondDto> publishNoteWithImages(
    @RequestParam @Parameter(description = "用户Id", required = true) Long userId,
    @RequestParam @Parameter(description = "笔记标题", required = true) String title,
    @RequestParam @Parameter(description = "笔记内容", required = true) String content,
    @RequestPart(required = true) @Parameter(description = "笔记图片") MultipartFile[] images,
    HttpServletRequest request
) throws JsonProcessingException {

    // 1. 从 token 获取当前登录用户
    String token = request.getHeader(requestHeader);
    token = token.substring(prefix.length());
    Claims claims = jwtUtil.parserJWT(token);
    JwtUser currentUser = SerializaUtil.fromJson(claims.getSubject(), JwtUser.class);

    // 2. 校验作者 ID
    if (!currentUser.getId().equals(userId)) {
        return RespondMessage.fail("userid incorrect:非法操作：不能为其他用户上传笔记");
    }

    // 3. 处理图片（可选）
    if (images != null && images.length > 0) {
        // 校验图片合法性
        for (MultipartFile file : images) {
            if (!FlieUtil.isImage(file) || !FlieUtil.hasImageMagicNumber(file)) {
                log.warn("不支持的图片格式:{}", file.getContentType());
                return RespondMessage.fail("unsupport format:文件格式有误，仅支持上传图片");
            }
        }
        // 4. 创建笔记对象
        Note note = new Note();
        note.setTitle(title);
        note.setAuthor(new User(userId));
        note.setContent(content);
        note.setImagesUrls(List.of());
        // 将图片 URL 转为 JSON 存储

        // 5. 保存笔记
        Note newNote = noteService.save(note,images);

        return RespondMessage.success("success", newNote.toNoteRespondDto());
    }else{
            return RespondMessage.fail("image required:图片不能为空");

    }

    
}
    /**
     * 根据用户上传的笔记id查询
     * @param id 笔记id
     * @return 笔记详细信息
     */
    @Operation(summary = "通过Id查询笔记",description = "需要具有业务数据访问权限")
    @PreAuthorize("hasAuthority('sys:data:view')")
    @GetMapping("/notes/{noteId}")
    public RespondMessage<NoteRespondDto> searchNoteById(
        @PathVariable("noteId") @Parameter(description = "笔记Id", required = true) Long id
        ){
        Note note =noteService.findById(id).orElse(null);
        if(note!=null){
           return RespondMessage.success("success"
           ,note.toNoteRespondDto()); 
        }
        return RespondMessage.fail("userid incorrect:笔记id错误，未找到对应笔记");
    }

    @Operation(summary = "通过用户Id查询笔记，支持分页",description = "需要具有业务数据访问权限")
    @PreAuthorize("hasAuthority('sys:data:view')")
    @GetMapping("/users/{userId}/notes")
    public RespondMessage<Page<NoteRespondDto>> searchNoteByPage(
        @PathVariable("userId") @Parameter(description = "用户Id", required = true) Long id,
        @RequestParam @Parameter(description = "页数", required = true) int page,
        @RequestParam @Parameter(description = "每页数据量", required = true) int size)
        {   
        PageRequest pageable=PageRequest.of(page, size);
        Page<Note> data =noteService.findNoteByPage(id, pageable);
            Page<NoteRespondDto> res=data.map(Note::toNoteRespondDto);
            return RespondMessage.success(
                "已查询到"+data.getNumberOfElements()+"条笔记"
                ,res
                ); 
    }

    
}
