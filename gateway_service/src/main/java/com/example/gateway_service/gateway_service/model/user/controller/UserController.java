package com.example.gateway_service.gateway_service.model.user.controller;

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

import com.example.gateway_service.gateway_service.common.RespondMessage;
import com.example.gateway_service.gateway_service.common.aop.Idempotent;
import com.example.gateway_service.gateway_service.common.tool.MetricsNames;
import com.example.gateway_service.gateway_service.model.user.service.UserFollowService;
import com.example.gateway_service.gateway_service.security.model.JwtUser;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final MeterRegistry meter;
    // 鉴权后拿到 userId（此处用参数代替）
    @PreAuthorize("hasAuthority('sys:data:upload')")
    @Operation(summary = "关注用户", description = "关注用户")
    @PostMapping("/{targetId}/follow")
    @Idempotent()
    public ResponseEntity<Object> follow(@AuthenticationPrincipal JwtUser jwtUser,
                                       @PathVariable @Positive long targetId) {
                                        
        Timer.builder(MetricsNames.SOCIAL_FOLLOW_TIMER)
        .register(meter)
        .record(() ->userFollowService.follow(jwtUser.getId(), targetId));
        return ResponseEntity.ok().body(RespondMessage.success( "Follow success:已关注"+targetId ));
    }
    
    
    @PreAuthorize("hasAuthority('sys:data:upload')")
    @Operation(summary = "取消关注用户", description = "取消关注用户")
    @DeleteMapping("/{targetId}/follow")
    @Idempotent()
    public ResponseEntity<Object> unfollow(@AuthenticationPrincipal JwtUser jwtUser,
                                         @PathVariable @Positive long targetId) {
        Timer.builder(MetricsNames.SOCIAL_UNFOLLOW_TIMER)
        .register(meter)
        .record(() ->userFollowService.unfollow(jwtUser.getId(), targetId));
        return ResponseEntity.ok().body(RespondMessage.success("Unfollow success:已取消关注"+targetId ));
    }
}
