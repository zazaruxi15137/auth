package com.example.rednote.auth.model.user.controller;

import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.rednote.auth.common.RespondMessage;
import com.example.rednote.auth.model.user.service.UserFollowService;
import com.example.rednote.auth.security.model.JwtUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户关注模块")
@RequiredArgsConstructor
public class UserController {
    private final UserFollowService userFollowService;

    // 鉴权后拿到 userId（此处用参数代替）
    @PreAuthorize("hasAuthority('sys:data:upload')")
    @Operation(summary = "关注用户", description = "关注用户")
    @PostMapping("/{targetId}/follow")
    public RespondMessage<Long> follow(@AuthenticationPrincipal JwtUser jwtUser,
                                       @PathVariable @Positive long targetId) {
        userFollowService.follow(jwtUser.getId(), targetId);
        return RespondMessage.success( "Follow success:已关注"+targetId );
    }
    @PreAuthorize("hasAuthority('sys:data:upload')")
    @Operation(summary = "取消关注用户", description = "取消关注用户")
    @DeleteMapping("/{targetId}/follow")
    public RespondMessage<Long> unfollow(@AuthenticationPrincipal JwtUser jwtUser,
                                         @PathVariable @Positive long targetId) {
        userFollowService.unfollow(jwtUser.getId(), targetId);
        return RespondMessage.success("Unfollow success:已取消关注"+targetId );
    }
}
