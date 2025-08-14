package com.example.rednote.auth.model.feed.service.impl;

import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.model.feed.dto.FeedRespond;
import com.example.rednote.auth.model.feed.service.FeedService;
import com.example.rednote.auth.model.notes.dto.NoteRespondDto;
import com.example.rednote.auth.model.notes.entity.Note;
import com.example.rednote.auth.model.notes.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service 
@Slf4j
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final StringRedisTemplate redis;
    private final NoteService  noteService;

    @Value("${app.feed.bigv-threshold}") private long bigvThreshold;

    /**
     * cursor 为“上次返回的最末条 score（毫秒）”，首次可传 Long.MAX_VALUE
     */
    // @Override
    @Transactional
    public FeedRespond<NoteRespondDto> getFeed(long userId, long cursorExclusive, int size) {

    String key = KeysUtil.redisInboxKey(userId);
    double max = cursorExclusive <= 0 ? Double.POSITIVE_INFINITY : cursorExclusive - 1; // 排除等于 cursor 的
    Set<ZSetOperations.TypedTuple<String>> tuples =
            redis.opsForZSet().reverseRangeByScoreWithScores(key, Double.NEGATIVE_INFINITY, max, 0, size);
    
    if (tuples == null || tuples.isEmpty()) {
    return new FeedRespond<>(List.of(), false, null,0);
    }
    
    //拉取note实体
    List<Note> notes = noteService
        .findAllbyIds(tuples
            .stream()
            .map(t -> Long.valueOf(t.getValue()))
            .toList());
    
    
    
    Map<Long, Note> map = notes.stream().collect(Collectors.toMap(Note::getId, Function.identity()));

    List<NoteRespondDto> sotednote = tuples
        .stream()
        .map(t-> map.get(Long.valueOf(t.getValue())))
        .map(Note::toNoteRespondDto)
        .toList();


    boolean hasMore = sotednote.size() >= size;
    Long lastScore = tuples
        .stream()
        .skip(sotednote.size() - 1)
        .findFirst()
        .map(t -> t.getScore().longValue())
        .orElse(null);

    // 如果结果不足 size，可以做“超大V回补”
    // if (sotednote.size() < size) {
        // 找到用户关注的作者里是超大V的（这里简化：遍历用户关注的作者分页或缓存名单——实际可做缓存）
        // 为了演示，省略“查询用户关注列表”这一步，你可以在 FollowRepository 补充相应接口。
        // 假设我们有一个作者列表 bigAuthors（建议缓存），从各自 authorKey 里按时间取少量合并：
        // List<Long> bigAuthors = ...
        // for (Long aid : bigAuthors) {
        //   var add = redis.opsForZSet().reverseRangeByScoreWithScores(RedisKeys.authorKey(aid),
        //       Double.NEGATIVE_INFINITY, max, 0, size);
        //   // merge 到 list（注意去重 noteId）
        // }
        // 这里给出一个占位注释：生产上强烈建议将“用户关注作者列表”缓存成 SET，
        // 并维护一个“bigv:authors”集合；取交集后再从各作者时间线少量取若干条并 merge。
    // }
    return new FeedRespond<>(sotednote, hasMore, lastScore,sotednote.size());
    }
    
}