package com.example.gateway_service.gateway_service.model.feed.service;
import com.example.gateway_service.gateway_service.model.feed.dto.FeedRespond;
import com.example.gateway_service.gateway_service.model.notes.dto.NoteRespondDto;


public interface FeedService {
    public FeedRespond<NoteRespondDto> getFeed(long userId, long cursorExclusive, int size, long noteId);
}
