package com.example.gateway_service.gateway_service.model.feed.controller;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.gateway_service.gateway_service.common.RespondMessage;
import com.example.gateway_service.gateway_service.common.tool.KeysUtil;
import com.example.gateway_service.gateway_service.common.tool.MetricsNames;
import com.example.gateway_service.gateway_service.common.tool.RedisUtil;
import com.example.gateway_service.gateway_service.model.feed.dto.FeedRespond;
import com.example.gateway_service.gateway_service.model.feed.service.FeedService;
import com.example.gateway_service.gateway_service.model.notes.dto.NoteRespondDto;
import com.example.gateway_service.gateway_service.model.user.service.serviceImpl.UserFollowServiceImpl;
import com.example.gateway_service.gateway_service.security.model.JwtUser;

import io.micrometer.core.instrument.*;

@RestController 
@Tag(name = "Feed")
@Slf4j
@RequestMapping("/api") 
@RequiredArgsConstructor
public class FeedController {

        private final FeedService feedService;
        private final MeterRegistry meter;
        private final UserFollowServiceImpl userFollowRepository;
        private final RedisUtil redisTemplate;
        private final Random r;
        @Value("${app.feed.bigv-threshold}")
        private Long bigvThreshold;
        @Value("${app.feed.follow-bigV-exprir}")
        private Long expiration;
        @Value("${spring.redis.cache_bound}") private long bound;
        @Value("${spring.redis.cache_min_Time}") private long minTime;
/**
 * Retrieves a feed for the authenticated user.
 *
 * @param jwtUser the authenticated user
 * @param cursor the score of the last item from the previous fetch, or null for first fetch
 * @param size the number of items to fetch per page
 * @return a response message containing the feed data
 */

    @Operation(summary = "获取feed", description = "获取feed")
    @PreAuthorize("hasAuthority('sys:data:view')")
    @GetMapping("/feed")
    
    public ResponseEntity<Object> getFeed(
        @AuthenticationPrincipal JwtUser jwtUser,
        @RequestParam(required = false,defaultValue = "0")
        @Parameter(description = "上次返回的最末条 score（毫秒）", required = false)
        @PositiveOrZero
        Long cursor,
        @RequestParam(defaultValue = "20")
        @Parameter(description = "每页数据量", required = true)
        int size,
        @RequestParam(defaultValue = "0")
        @Parameter(description = "下条笔记Id", required = false)
        int noteId
        ) {
        long userId = jwtUser.getId();
        //检查缓存中是否存在

        if (!redisTemplate.hasKey(KeysUtil.redisFollowedBigVKey(userId))) {
                List<Long> bigvAuthors = userFollowRepository.findBigvAuthors(userId, bigvThreshold);
                if (!bigvAuthors.isEmpty()) {
                        redisTemplate.setAllToSet(
                                KeysUtil.redisFollowedBigVKey(userId),
                                bigvAuthors.stream().map(String::valueOf).toArray(String[]::new),
                                expiration,
                                TimeUnit.HOURS);
                log.info("已刷新粉丝关注的大V:{}", bigvAuthors.size());
                        }}
        //缓存查到的数据
        FeedRespond<NoteRespondDto> res = Timer.builder(MetricsNames.FEED_TIMELINE_TIMER)
                                .register(meter)
                                .record(() ->feedService.getFeed(userId, cursor, size, noteId));
        return ResponseEntity.ok(RespondMessage.success(
                "拉取成功", 
                res
                ));
    }
}