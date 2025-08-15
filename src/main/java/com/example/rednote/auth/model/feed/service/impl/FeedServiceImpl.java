package com.example.rednote.auth.model.feed.service.impl;

import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.model.feed.dto.FeedItemDto;
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
import java.util.stream.Stream;

@Service 
@Slf4j
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final StringRedisTemplate redis;
    private final NoteService  noteService;

    @Value("${app.feed.bigv-threshold}") private long bigvThreshold;
    @Value("${app.feed.bigV-pull-size}") private int bigVPullSize;

    /**
     * cursor 为“上次返回的最末条 score（毫秒）”，首次可传 Long.MAX_VALUE
     */
    // @Override
    @Transactional
    public FeedRespond<NoteRespondDto> getFeed(long userId, long cursorExclusive, int size) {

    String key = KeysUtil.redisInboxKey(userId);

    //拉取收件箱的笔记
    double max = cursorExclusive <= 0 ? Long.MAX_VALUE : cursorExclusive - 1; // 排除等于 cursor 的
    Set<ZSetOperations.TypedTuple<String>> inbox =
            redis.opsForZSet().reverseRangeByScoreWithScores(key, Double.NEGATIVE_INFINITY, max, 0, size);
    //获取关注大v的笔记
    List<ZSetOperations.TypedTuple<String>> outbox = new ArrayList<>();
    for (String aid : redis.opsForSet().members(KeysUtil.redisFollowedBigVKey(userId))) {
        outbox.addAll(redis.opsForZSet()
            .reverseRangeByScoreWithScores(KeysUtil.redisOutboxKey(Long.valueOf(aid)), Double.NEGATIVE_INFINITY, max, 0, bigVPullSize));
    }

    // 合并
    Map<Long, FeedItemDto> noteMap = new HashMap<>();
    Stream.concat(inbox.stream(), outbox.stream()).forEach(t -> {
        long noteId = Long.parseLong(t.getValue());
        noteMap.putIfAbsent(noteId, new FeedItemDto(noteId, t.getScore().longValue()));
    });

    List<FeedItemDto> merged = noteMap.values().stream()
            .sorted(Comparator.comparing(FeedItemDto::getScore).reversed())
            .limit(size)
            .toList();
    
    if (merged == null || merged.isEmpty()) {
    return new FeedRespond<>(List.of(), false, null,0);
    }
    
    //拉取note实体
    List<Note> notes = noteService
        .findAllbyIds(merged
            .stream()
            .map( t -> t.getNoteId())
            .toList());
    
    
    
    Map<Long, Note> map = notes.stream().collect(Collectors.toMap(Note::getId, Function.identity()));

    List<NoteRespondDto> sotednote = merged
        .stream()
        .map(t-> map.get(t.getNoteId()))
        .map(Note::toNoteRespondDto)
        .toList();
    // 使用原始数据计算是否还有更多
    boolean hasMore = merged.size() >= size;
    Long nextCursor = merged.size() >= size ? merged.get(size-1).getScore() : null;

    // 返回查询到的笔记数量（有些可能已被删除导致需求的数量和实际数量不一致）
    return new FeedRespond<>(sotednote, hasMore, nextCursor ,sotednote.size());
    }
    
}