package com.example.rednote.auth.model.feed.controller;

import com.example.rednote.auth.common.RespondMessage;
import com.example.rednote.auth.model.feed.dto.FeedRespond;
import com.example.rednote.auth.model.feed.service.FeedService;
import com.example.rednote.auth.model.notes.dto.NoteRespondDto;
import com.example.rednote.auth.security.model.JwtUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController 
@Tag(name = "Feed")
@Slf4j
@RequestMapping("/api") 
@RequiredArgsConstructor
public class FeedController {

        private final FeedService feedService;
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
    public RespondMessage<FeedRespond<NoteRespondDto>> getFeed(
            @AuthenticationPrincipal JwtUser jwtUser,
            @RequestParam(name = "cursor", required = false)
            @Parameter(description = "上次返回的最末条 score（毫秒）", required = false)
            Long cursor,
            @RequestParam(name = "size", defaultValue = "20")
            @Parameter(description = "每页数据量", required = true)
            int size
    ) {
        long c = (cursor == null) ? Long.MAX_VALUE : cursor;
        return RespondMessage.success("拉取成功", feedService.getFeed(jwtUser.getId(), c, size));
    }
}