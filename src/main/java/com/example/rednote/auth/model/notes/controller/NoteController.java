package com.example.rednote.auth.model.notes.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @Value("${spring.storePath}")
    private String filePath;
    @Value("${jwt.header}")
    private String requestHeader;
    @Value("${jwt.prefix}")
    private String prefix;

    /**
     * 上传笔记图片并返回图片访问地址
     * @param userId 用户id
     * @param images 图片文件
     * @return 图片url地址
     */
    @Operation(summary = "图片上传",description = "需要具有业务数据上传权限，只支持图片上传")
    @PreAuthorize("hasAuthority('sys:data:upload')")
    @PostMapping("/notes/image")
    public RespondMessage<String[]> publishNoteImage(
        @RequestParam @Parameter(description = "用户Id", required = true) Long userId,
        @RequestParam @Parameter(description = "图片文件", required = true) MultipartFile[] images
        ){
        String tempfilePath=filePath+userId;
        List<String> urls = new ArrayList<>();
        File dir = new File(tempfilePath);
        if (!dir.exists()) dir.mkdirs();

        // 第一步：校验所有图片是否合法（同时避免每个文件多次被检验）
        for (MultipartFile file : images) {
            // 这里是“不是图片”或者“magic number不通过”则拦截
            if (!FlieUtil.isImage(file) || !FlieUtil.hasImageMagicNumber(file)) {
                log.warn("不支持的图片格式:{}", file.getContentType());
                return RespondMessage.fail("文件格式有误，仅支持上传图片");
            }
        }

        // 第二步：全部校验通过后，再循环保存
        for (MultipartFile file : images) {
            String uuid = UUID.randomUUID().toString();
            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String filename = uuid + (ext != null ? ("." + ext) : "");
            File dest = new File(tempfilePath, filename);
            try {
                file.transferTo(dest);
            } catch (IOException e) {
                log.error("文件写入失败{}", filename);
                return RespondMessage.fail("服务器错误，图片上传失败");
            }
            // 图片访问接口的URL
            String url = "/api/note/image/" + userId + "/" + filename;
            urls.add(url);
        }

        return RespondMessage.success("success", urls.toArray(new String[0]));
    }

    /**
     * 接受用户上传的笔记并保存
     * @param requestDto 用户上传的笔记实体
     * @return 返回已保存的笔记详细信息(只包含用户id)
     * @throws JsonProcessingException 
     */
    @Operation(summary = "笔记上传",description = "需要具有业务数据上传权限")
    @PreAuthorize("hasAuthority('sys:data:upload')")
    @PostMapping("/notes")
    public RespondMessage<NoteRespondDto> publishNote(
        @RequestBody @Valid NoteRequestDto requestDto,
        HttpServletRequest request
        ) throws JsonProcessingException {
        //在Filter中一进行校验，故在此不进行二次校验
        String token = request.getHeader(requestHeader);
        token = token.substring(prefix.length()); // 去掉 "Bearer "

        // 2. 从 token 获取用户信息
        Claims claims = jwtUtil.parserJWT(token);
        JwtUser currentUser = SerializaUtil.fromJson(claims.getSubject(), JwtUser.class);
        // 4. 校验作者 ID 是否是当前登录用户
        if (!currentUser.getId().equals(requestDto.getAuthorId())) {
            return RespondMessage.fail("非法操作：不能为其他用户上传笔记");
        }

        Note note=new Note();
        note.setTitle(requestDto.getTitle()+"");
        note.setAuthor(new User(
            requestDto.getAuthorId()
        ));
        note.setContent(requestDto.getContent()+"");
        note.setImagesUrls(requestDto.getImagesUrls());
        Note newNote=noteService.save(note);

        return RespondMessage.success("success", newNote.toNoteRespondDto());
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
        return RespondMessage.fail("笔记id错误，未找到对应笔记");
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
            if(!data.isEmpty()){
                Page<NoteRespondDto> res=data.map(Note::toNoteRespondDto);
                return RespondMessage.success(
                    "success"
                    ,res
                    ); 
            }
            return RespondMessage.fail("未查询到结果");
    }

    @Operation(summary = "图片访问地址",description = "需要具有业务数据访问权限")
    @PreAuthorize("hasAuthority('sys:data:view')")
    @GetMapping("/notes/image/{userId}/{filename:.+}")
    public ResponseEntity<FileSystemResource> getImage(
        @PathVariable("userId")Long id,
        @PathVariable("filename")String filename
        ){
        String tempfilePath=filePath+id;

        File file = new File(tempfilePath, filename);
        if (!file.exists()) {
            log.warn("文件地址错误{}",file.getAbsolutePath());
            return ResponseEntity.notFound().build();
        }
        // 可根据实际图片格式调整Content-Type
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(FlieUtil.getMediaType(filename)) // 如需更智能可判断后缀
                .body(new FileSystemResource(file));
    }
}
