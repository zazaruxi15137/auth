package com.example.gateway_service.gateway_service.common.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AsyncCleanupService {

    @Async("cleanupExecutor")
    public void cleanupFilesAsync(List<Path> writtenFiles, Path noteDir) {
        Thread thread=Thread.currentThread();
        // 删除已写入的文件
        for (Path p : writtenFiles) {
            try {
                log.info("{}正在清理文件{}",thread.getName(),p.toString());
                Files.deleteIfExists(p);
                
            } catch (IOException e) {
                log.warn("清理文件失败: {}", p, e);
            }
        }
        if (Objects.isNull(noteDir)) {
            return;
        }
        // 如目录已空，尝试删目录（非必须）
        try {
            if (Files.isDirectory(noteDir) && isDirEmpty(noteDir)) {
                Files.deleteIfExists(noteDir);
            }
        } catch (IOException e) {
            log.warn("清理目录失败: {}", noteDir, e);
        }
    }

    private boolean isDirEmpty(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return !stream.findAny().isPresent();
        }
    }
}
