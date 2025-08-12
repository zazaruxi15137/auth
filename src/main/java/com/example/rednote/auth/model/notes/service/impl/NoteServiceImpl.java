package com.example.rednote.auth.model.notes.service.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.beans.Transient;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.management.RuntimeOperationsException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;

import com.example.rednote.auth.common.RespondMessage;
import com.example.rednote.auth.common.exception.CustomException;
import com.example.rednote.auth.common.service.AsyncCleanupService;
import com.example.rednote.auth.common.tool.FlieUtil;
import com.example.rednote.auth.common.tool.SerializaUtil;
import com.example.rednote.auth.model.notes.entity.Note;
import com.example.rednote.auth.model.notes.repository.NoteRepository;
import com.example.rednote.auth.model.notes.service.NoteService;
import com.example.rednote.auth.model.user.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;
    @Value("${spring.storePath}")
    private String filePath;
    private final AsyncCleanupService asyncCleanupService; // 异步清理
    @Override
    public Page<Note> findNoteByPage(Long userId, Pageable page) {

       return noteRepository.findByAuthor_Id(userId, page);
    }

@Override
@Transactional(rollbackOn = Exception.class)
public Note save(Note note, MultipartFile[] files) {
    // 1) 先持久化，拿到 noteId，方便组织存储目录
    Note newNote = noteRepository.save(note);

    // 2) 目录准备（userId/noteId）
    Long userId = newNote.getAuthor().getId();
    Objects.requireNonNull(userId, "author.id不能为空");
    Path noteDir = Paths.get(filePath, String.valueOf(userId), String.valueOf(newNote.getId()));
    try {
        Files.createDirectories(noteDir);
    } catch (IOException e) {
      log.error("derictory create fail:{}", e.getMessage());
        throw new RuntimeException("derictory create fail:创建存储目录失败: " + noteDir, e);
    }

    // 3) 保存文件，失败即清理
    List<String> urls = new ArrayList<>();
    List<Path> writtenFiles = new ArrayList<>();

    try {
        for (MultipartFile file : files) {
            String uuid = UUID.randomUUID().toString();
            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String filename = uuid + (ext != null ? ("." + ext) : "");
            Path dest = noteDir.resolve(filename);

            // 写入磁盘
            file.transferTo(dest.toFile());
            writtenFiles.add(dest);

            // 静态资源映射URL（确保你已配置 /images/** -> filePath）
            String url = "/images/" + userId + "/" + newNote.getId() + "/" + filename;
            urls.add(url);
        }
        // 4) 回写图片URL到实体
    newNote.setImagesUrls(urls);
    
      //   if(true){
      //            throw new IOException("test exciption");
      //   }
    // 依赖脏检查提交
    return newNote;
    } catch (Exception ex) {
        // 手动清理磁盘
        asyncCleanupService.cleanupFilesAsync(writtenFiles, noteDir);
        // 尝试删除空目录
      log.error("图片储存时发生错误:{}", ex.getMessage());
      throw new RuntimeException("upload fial:图片保存失败，事务回滚", ex);
    }

    
}


    @Override
    public Optional<Note> findById(Long id) {
       return noteRepository.findById(id);
    }
    
}
