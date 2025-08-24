package com.example.rednote.auth.model.notes.service.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.example.rednote.auth.common.exception.CustomException;
import com.example.rednote.auth.common.service.AsyncCleanupService;
import com.example.rednote.auth.common.tool.FlieUtil;
import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.common.tool.MetricsNames;
import com.example.rednote.auth.common.tool.RedisUtil;
import com.example.rednote.auth.model.notes.entity.Note;
import com.example.rednote.auth.model.notes.repository.NoteRepository;
import com.example.rednote.auth.model.notes.service.NoteService;
import com.example.rednote.auth.model.user.entity.UserFollow;
import com.example.rednote.auth.model.user.service.UserFollowService;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;    
    private final UserFollowService userFollowService;
    @Qualifier("serviceExecutor")
    private final TaskExecutor serviceExecutor;
    private final MeterRegistry meter;
    @Value("${spring.storePath}")
    private String filePath;
    @Value("${app.feed.stream.name}")
    private String stream;
    @Value("${app.feed.bigv-threshold}") 
    private long bigvThreshold;
    @Value("${app.feed.outbox-max-size}")
    private long outboxMaxSize;
    @Value("${app.feed.mod}")
    private String mod;
    @Value("${app.feed.box-exprir-time}")
    private int exprirTime;
    @Value("${app.shard}")
    private int shard;
    
    private volatile String sha2; 
    @Qualifier("notePublishScript")
    private final DefaultRedisScript<Long> notePublishScript;
    
    private final RedisUtil redisUtil;

    private final AsyncCleanupService asyncCleanupService; // 异步清理
    @PostConstruct
    public void preloadLua() {
        this.sha2 = redisUtil.gTemplate().execute((RedisCallback<String>) con ->
                con.scriptingCommands().scriptLoad(
                        notePublishScript.getScriptAsString().getBytes(StandardCharsets.UTF_8)
                )
        );
    }
    @Override
    public Page<Note> findNoteByPage(Long userId, Pageable page) {

       return noteRepository.findByAuthor_Id(userId, page);
    }

   @Override
   @Transactional(rollbackFor = Exception.class)
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
        
        long score = newNote.getPublishTime()
        .toInstant()
        .toEpochMilli();
        Long authorId = newNote.getAuthor().getId();


        // 为每个用户维护一个outbox，储存最近发送的笔记
        long followers = userFollowService.countFollowers(authorId);
        Timer.Sample s = Timer.start(meter);

        try {
        redisUtil.gTemplate().executePipelined((RedisCallback<Object>) con -> {

                byte[] key = KeysUtil.redisOutboxKey(authorId % shard,authorId).getBytes(StandardCharsets.UTF_8);
                // ARGV 顺序要与 Lua 对齐：member, score, maxSize, expireDays, timeUnit
                byte[] member = String.valueOf(newNote.getId()).getBytes(StandardCharsets.UTF_8);
                byte[] bscore = String.valueOf(score).getBytes(StandardCharsets.UTF_8);
                byte[] maxSz  = String.valueOf(outboxMaxSize).getBytes(StandardCharsets.UTF_8);
                byte[] days   = String.valueOf(exprirTime).getBytes(StandardCharsets.UTF_8);
                byte[] unit   = "ms".getBytes(StandardCharsets.UTF_8); 
                byte[][] keysAndArgs = new byte[][]{
                    key, // inbox
                    member, // authorID
                    bscore, //当前时间ms
                    maxSz,//最大保留笔记条数
                    days,// 保留天数
                    unit// 时间单位默认 ms
                };                // 如果你的 score 用秒，传 "s"
                try{
                    con.scriptingCommands().evalSha(sha2, ReturnType.INTEGER, 1, keysAndArgs);
                }catch(Exception e){
                    log.error("笔记加入outbox失败", newNote.getId(), authorId, e.getMessage());
                    throw new CustomException("笔记发布失败，请稍后再试");
                }
                Counter.builder(MetricsNames.PIPLINE_EXEC_SUCCESS)
                .tag("scene","笔记发布推流成功").register(meter).increment();
                return null;
            });
} catch (Exception e) {
            Counter.builder(MetricsNames.PIPLINE_EXEC_FAIL)
                .tag("scene","笔记发布推流失败").register(meter).increment();
                log.warn("pipline:笔记发布失败");
            throw e;
        } finally {
            s.stop(Timer.builder(MetricsNames.PIPLINE_EXEC_TIMER)
                .tag("scene","笔记发布推流")
                .register(meter));
        }
            // log.info("笔记发布发布作者：{}，加入outbox", authorId);
         
        // }  
        // 为非大v博主的推送到stream中。
        if(followers < bigvThreshold){
        Map<String, String> body = new HashMap<>();
        body.put("authorId", String.valueOf(authorId));
        body.put("noteId", String.valueOf(note.getId()));
        body.put("tsMillis", String.valueOf(score));
        redisUtil.sendStreamMessage(stream, body);
        log.info("笔记已经推送到流中:{}", newNote.getId());
         }
        // TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            
        //     @Override public void afterCommit() {
        //         serviceExecutor.execute(()->{
        //             try{
        //               Thread.sleep(1000);  
        //             }catch(Exception ignord){

        //             }
        //             // 2) 该作者的分页缓存：从索引集合拿到所有页的 key 精准删除
        //         String idxKey = KeysUtil.redisNotePageCacheKeySet(authorId);
        //         Set<String> pageKeys = redisUtil.memberOfSet(idxKey);
        //         int page=0;
        //         while(true){
        //            Page<UserFollow> folloers=userFollowService.pageFollowers(authorId, PageRequest.of(page, 50));
        //         for(UserFollow follower: folloers){
        //             pageKeys.addAll(redisUtil.memberOfSet(KeysUtil.redisUserFeedCacheKeySet(follower.getFollowerId())));
        //         } 
        //         if(folloers.isEmpty() || !folloers.hasNext()){
        //             break;
        //         }
        //         }
        //         if (pageKeys != null && !pageKeys.isEmpty()) {
        //             // 批量删除
        //             redisUtil.delete(pageKeys);
        //             // 索引集不删，后续命中会重新 SADD；也可清空：
        //             // redis.delete(idxKey);
        //         }
        //         });
        //     }
        // });
      // 依赖脏检查提交
      return newNote;
      } catch (Exception ex) {
         // 手动清理磁盘
         asyncCleanupService.cleanupFilesAsync(writtenFiles, noteDir);
         // 尝试删除空目录
         log.error("笔记上传失败，回滚事务:{}", ex.getMessage());
         throw new RuntimeException("upload fial：笔记上传失", ex);
      }
      
   }
   @Override
   @Transactional(rollbackFor = Exception.class)
   public List<Note> findAllbyIds(List<Long> ids){
       return noteRepository.findAllById(ids);
   }

   
   @Transactional(rollbackFor = Exception.class)
    public Note updateNote(Long noteId,
                           Long currentUserId,
                            String title,
                            String content,
                            @NotNull boolean isPublic,
                            List<String> removeUrls,
                            MultipartFile[] addImages) {

        // 1) 取笔记并校验所有权
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("笔记不存在"));
        Long authorId = note.getAuthor().getId();
        if (!Objects.equals(authorId, currentUserId)) {
            throw new AccessDeniedException("无权修改他人笔记");
        }

        // 2) 解析现有图片URL列表
        
        List<String> oldUrls = note.getImagesUrls(); // List<String>
        if (oldUrls == null) oldUrls = new ArrayList<>();

        // 3) 计算要删除/保留
        Set<String> toRemove = removeUrls == null ? Set.of() : new HashSet<>(removeUrls);
        List<String> kept = oldUrls.stream()
                .filter(u -> !toRemove.contains(u))
                .toList();

        // 4) 写入新增图片
        List<String> addedUrls = new ArrayList<>();
        List<Path> writtenFiles = new ArrayList<>();
        if (addImages != null && addImages.length > 0) {
            // 可选：兜底校验
            for (MultipartFile f : addImages) {
                if (f == null || f.isEmpty()) throw new IllegalArgumentException("存在空图片");
                if (!FlieUtil.isImage(f) || !FlieUtil.hasImageMagicNumber(f)) {
                    throw new IllegalArgumentException("仅支持上传图片文件");
                }
            }

            Path noteDir = Paths.get(filePath, String.valueOf(authorId), String.valueOf(noteId));
            try {
                Files.createDirectories(noteDir);
            } catch (IOException e) {
                throw new RuntimeException("创建存储目录失败: " + noteDir, e);
            }

            try {
                for (MultipartFile file : addImages) {
                    String uuid = UUID.randomUUID().toString();
                    String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
                    String filename = uuid + (ext != null ? ("." + ext) : "");
                    Path dest = noteDir.resolve(filename);
                    file.transferTo(dest.toFile());
                    writtenFiles.add(dest);
                    // 生成静态URL
                    String url = "/images/" + authorId + "/" + noteId + "/" + filename;
                    addedUrls.add(url);
                }
            } catch (Exception ex) {
                // 失败：回滚DB + 异步清理已写文件
                asyncCleanupService.cleanupFilesAsync(writtenFiles, null);
                throw new RuntimeException("图片保存失败，事务回滚", ex);
            }
        }

        // 5) 物理删除被移除的旧图片（非必须同步执行，可改异步）
        List<Path> toDeleteFiles = mapUrlsToPaths(toRemove, filePath);
        asyncCleanupService.cleanupFilesAsync(toDeleteFiles, null); // 异步删除更稳妥；要同步就直接删

        // 6) 写回字段（依赖脏检查，不必再 save）
        if (title != null) note.setTitle(title);
        if (content != null) note.setContent(content);
        note.setPublic(isPublic);
        List<String> finalUrls = new ArrayList<>(kept);
        finalUrls.addAll(addedUrls);
        note.setImagesUrls(finalUrls);


        // TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            
        //     @Override public void afterCommit() {  
        //         try {
        //             Thread.sleep(1000);  
        //         } catch (InterruptedException ignord) {
        //             // TODO: handle exception
        //         }
        //         // 2) 该作者的分页缓存：从索引集合拿到所有页的 key 精准删除
        //         String idxKey = KeysUtil.redisNotePageCacheKeySet(authorId);
        //         Set<String> pageKeys = redisUtil.memberOfSet(idxKey);
        //         if (pageKeys != null && !pageKeys.isEmpty()) {
        //             // 批量删除
        //             redisUtil.delete(pageKeys);
        //             // 索引集不删，后续命中会重新 SADD；也可清空：
        //             // redis.delete(idxKey);
        //         }
        //     }
        // });
        return note; // 事务提交时自动 UPDATE
    }
    
    @Override
    public Optional<Note> findById(Long id) {
       return noteRepository.findById(id);
    }
    
    private List<Path> mapUrlsToPaths(Set<String> urls, String root) {
        List<Path> files = new ArrayList<>();
        for (String url : urls) {
            // 期望格式：/images/{userId}/{noteId}/{filename}
            String normalized = url.replaceFirst("^/+", "");
            if (!normalized.startsWith("images/")) continue; // 防护：仅处理受控目录
            files.add(Paths.get(root).resolve(normalized.substring("images/".length())));
        }
        return files;
    }


        @Override
    public Map<Long, Note> findReadableMap(long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();

        // 1) 一次性查库（缺失/无权限的不会返回）
        List<Note> rows = noteRepository.findAllById(ids);
        if (rows == null || rows.isEmpty()) return Collections.emptyMap();

        // 2) 先建临时 id->Note 映射（便于按输入顺序重组）
        Map<Long, Note> byId = rows.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Note::getId, n -> n, (a, b) -> a));

        // 3) 按输入顺序筛选可见并返回（保持顺序）
        LinkedHashMap<Long, Note> result = new LinkedHashMap<>(ids.size());
        for (Long id : ids) {
            Note n = byId.get(id);
            if (n == null) continue; // 缺失/无权限

            // —— 以下判断按你的实体字段名微调 —— //

            // 软删除
            // if (Boolean.TRUE.equals(n.getDeleted())) continue;

            // 仅发布可见（若没有状态字段，可删除该判断）

            // 可见性判断（二选一：有 Visibility 枚举 或 只有 isPublic 字段）
            boolean allowed=false;
            if(n.isPublic()||n.getId()==userId){
                allowed=true;
            }
            if (allowed) {
                result.put(id, n);
            }
        }
        return result;
    }

    // public boolean putBlackList(long noteId){

    // }
    // public boolean removeBlackList(long noteId){

    // }
}
