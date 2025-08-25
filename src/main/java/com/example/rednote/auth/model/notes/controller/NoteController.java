package com.example.rednote.auth.model.notes.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.example.rednote.auth.common.RespondMessage;
import com.example.rednote.auth.common.aop.Idempotent;
import com.example.rednote.auth.common.tool.FlieUtil;
import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.common.tool.MetricsNames;
import com.example.rednote.auth.common.tool.RedisUtil;
import com.example.rednote.auth.common.tool.SerializaUtil;
import com.example.rednote.auth.model.notes.dto.NoteRespondDto;
import com.example.rednote.auth.model.notes.entity.Note;
import com.example.rednote.auth.model.notes.service.NoteService;
import com.example.rednote.auth.model.user.entity.User;
import com.example.rednote.auth.security.model.JwtUser;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import io.micrometer.core.instrument.Timer;



@Tag(name = "笔记管理", description = "笔记相关API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class NoteController {

    private final NoteService noteService;
    private final RedisUtil redisUtil;
    private final MeterRegistry meter;
    private final Random r;
    @Value("${jwt.header}")
    private String requestHeader;
    @Value("${jwt.prefix}")
    private String prefix;
    @Value("${spring.redis.cache_bound}") private long bound;
    @Value("${spring.redis.cache_min_Time}") private long minTime;


    @Operation(summary = "上传笔记（含图片）", description = "需要具有业务数据上传权限，同时支持图片上传")
    @PreAuthorize("hasAuthority('sys:data:upload')")
    @PostMapping(value = "/notes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Idempotent()
    public ResponseEntity<Object> publishNoteWithImages(
        @RequestParam @Positive @Parameter(description = "用户Id", required = true) Long userId,
        @RequestParam @Parameter(description = "笔记标题", required = true) String title,
        @RequestParam @Parameter(description = "笔记内容", required = true) String content,
        @RequestParam(defaultValue = "true") @Parameter(description = "是否公开", required = true) boolean isPublic,
        @RequestPart(required = true) @Parameter(description = "笔记图片") MultipartFile[] images,
        @AuthenticationPrincipal JwtUser jwtUser
    ) throws JsonProcessingException {

        // 2. 校验作者 ID
        if (!jwtUser.getId().equals(userId)) {
            return ResponseEntity.status(403).body(RespondMessage.fail("userid incorrect:非法操作：不能为其他用户上传笔记"));
        }

        // 3. 处理图片（可选）
        if (images != null && images.length > 0) {
            // 校验图片合法性
            for (MultipartFile file : images) {
                if (!FlieUtil.isImage(file) || !FlieUtil.hasImageMagicNumber(file)) {
                    log.warn("不支持的图片格式:{}", file.getContentType());
                    return ResponseEntity.badRequest().body(RespondMessage.fail("unsupport format:文件格式有误，仅支持上传图片")) ;
                }
            }
            // 4. 创建笔记对象
            Note note = new Note();
            note.setTitle(title);
            note.setAuthor(new User(userId));
            note.setContent(content);
            note.setImagesUrls(List.of());
            note.setPublic(isPublic);
            // 将图片 URL 转为 JSON 存储
            // 5. 保存笔记
            Set<String> pageKeys = redisUtil.memberOfSet(KeysUtil.redisNotePageCacheKeySet(userId));
            if(pageKeys!=null && !pageKeys.isEmpty()){
                redisUtil.delete(pageKeys);
            }
            Note newNote = Timer.builder(MetricsNames.NOTE_PUBLISH_TIMER)
                .register(meter)
                .record(() -> noteService.save(note,images));

            return ResponseEntity.ok().body(RespondMessage.success("success", newNote.toNoteRespondDto()));
        }else{
                return ResponseEntity.status(400).body(RespondMessage.fail("image required:图片不能为空"));

        }
    }
    
    
    @Operation(summary = "更新笔记（含图片增删）", description = "可选更新标题/内容，支持新增/删除图片")
    @PreAuthorize("hasAuthority('sys:data:upload')")
    @PatchMapping(value = "/notes/{noteId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Idempotent()
    public RespondMessage<NoteRespondDto> updateNote(
            @PathVariable @Positive Long noteId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) boolean isPublic,
            @RequestParam(required = false) List<String> removeUrls,            // 要删除的图片URL列表
            @RequestPart(value = "addImages", required = false) MultipartFile[] addImages, // 新增图片
            @AuthenticationPrincipal JwtUser me // 你自定义的UserDetails，带 getId()
    ) {
        
        Note updated = Timer.builder(MetricsNames.NOTE_UPDATE_TIMER)
                .register(meter)
                .record(() ->noteService.updateNote(noteId, me.getId(), title, content,isPublic, removeUrls, addImages));
        return RespondMessage.success("success", updated.toNoteRespondDto());
    }

    
    /**
     * 根据用户上传的笔记id查询
     * @param id 笔记id
     * @return 笔记详细信息
     */
    @Operation(summary = "通过Id查询笔记",description = "需要具有业务数据访问权限")
    @PreAuthorize("hasAuthority('sys:data:view')")
    @GetMapping("/notes/{noteId}")
    public ResponseEntity<Object> searchNoteById(
        @PathVariable("noteId") @Parameter(description = "笔记Id", required = true) Long id
        ){
        if(redisUtil.hasKey(KeysUtil.redisNoteNullKey(id))){
            return ResponseEntity.status(404).body(RespondMessage.withcode(404, "笔记不存在", null));
        }
        // if(redisUtil.hasKey(KeysUtil.redisNoteSingleCacheKey(id))){
        //     meter.counter(MetricsNames.IDEMPOTENT_HIT, "service","note_sigle_query").increment();
        //     log.info("缓存命中");
        //     return ResponseEntity.ok().body(RespondMessage.success(
        //         "success"
        //         ,KeysUtil.redisNoteSingleCacheKey(id)));
            
        // }
        Note note = Timer.builder(MetricsNames.NOTE_SINGLE_QUERY)
                .register(meter)
                .record(() ->noteService.findById(id).orElse(null));
        if(note!=null){
            // try{
            //    redisUtil.set(
            //     KeysUtil.redisNoteSingleCacheKey(id), 
            //     SerializaUtil.toJson(note.toNoteRespondDto()), 
            //     minTime+r.nextLong(bound), 
            //     TimeUnit.SECONDS); 
            // }catch(Exception ignored){
            //     log.warn("笔记缓存失败(单条){}",ignored.getMessage());
            // }
            
           return ResponseEntity.ok().body(RespondMessage.success("success"
           ,note.toNoteRespondDto())); 
        }else{
            redisUtil.set(
                KeysUtil.redisNoteNullKey(id), 
                "1", 
                15, 
                TimeUnit.SECONDS);
                return ResponseEntity.status(404).body(RespondMessage.withcode(404,"userid incorrect:笔记id错误，未找到对应笔记",null));
        }
    }

    @Operation(summary = "通过用户Id查询笔记，支持分页",description = "需要具有业务数据访问权限")
    @PreAuthorize("hasAuthority('sys:data:view')")
    @GetMapping("/users/{userId}/notes")
    public ResponseEntity<Object> searchNoteByPage(
        @PathVariable("userId") @Parameter(description = "用户Id", required = true) Long id,
        @RequestParam @Parameter(description = "页数", required = true) int page,
        @RequestParam @Parameter(description = "每页数据量", required = true) int size)
        {   
        // if(redisUtil.hasKey(KeysUtil.redisNotePageCacheKey(id, page, size))){
        //     meter.counter(MetricsNames.IDEMPOTENT_HIT, "service","note_page_query").increment();
        //     log.info("缓存命中");
        //     return ResponseEntity.ok().body(RespondMessage.success(
        //         "查询成功"
        //         ,redisUtil.get(KeysUtil.redisNotePageCacheKey(id, page, size))
        //         ));
        // }
        PageRequest pageable=PageRequest.of(page, size);
        Page<Note> data = Timer.builder(MetricsNames.NOTE_PAGE_QUERY)
                .register(meter)
                .record(() ->noteService.findNoteByPage(id, pageable));

            Page<NoteRespondDto> res = data.map(Note::toNoteRespondDto);
        // try{
        //        redisUtil.set(
        //         KeysUtil.redisNotePageCacheKey(id, page, size), 
        //         SerializaUtil.toJson(res), 
        //         minTime+r.nextLong(bound), 
        //         TimeUnit.SECONDS); 
        //         redisUtil.setToSet(KeysUtil.redisNotePageCacheKeySet(id), KeysUtil.redisNotePageCacheKey(id, page, size));
        //     }catch(Exception ignored){
        //         log.warn("笔记缓存失败(分页){}",ignored.getMessage());
        //     }
            return ResponseEntity.ok().body(RespondMessage.success(
                "查询成功"
                ,res
                )); 
    }

    // @Operation(summary = "拉黑笔记", description = "拉黑上传的笔记Id")
    // @PreAuthorize("hasAuthority('sys:data:upload')")
    // @PostMapping(value = "/notes/noteblacklist/{noteId}")
    // @Idempotent
    // public ResponseEntity<Object> noteblacklist(@PathVariable long noteId){
    //     noteService.putBlackList(noteId);
    //     ResponseEntity.ok().body(RespondMessage.success("拉黑成功"));
    // }
    // @Operation(summary = "解除笔记黑名单", description = "从黑名单中删除上传的笔记id")
    // @PreAuthorize("hasAuthority('sys:data:upload')")
    // @DeleteMapping(value = "/notes/noteblacklist/{noteId}")
    // @Idempotent
    // public ResponseEntity<Object> delNoteBlackList(@PathVariable long noteId){
    //     noteService.removeBlackList(noteId);
    //     ResponseEntity.ok().body(RespondMessage.success("拉黑成功"));
    // }

}
