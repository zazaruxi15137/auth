package com.example.rednote.auth.model.feed.dto;

import java.util.List;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FeedRespond<T> {
    private List<T> notes;
    private Boolean hasMore;
    private Long score;
    private Long nextNoteId;
    private Integer size;
}
