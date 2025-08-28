package com.example.gateway_service.gateway_service.model.feed.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeedItemDto {
    private Long noteId;
    private Long score; // 毫秒时间戳，用于 cursor
}
