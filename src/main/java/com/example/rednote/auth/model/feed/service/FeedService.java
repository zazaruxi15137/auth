package com.example.rednote.auth.model.feed.service;

import com.example.rednote.auth.model.feed.dto.FeedRespond;
import com.example.rednote.auth.model.notes.dto.NoteRespondDto;

public interface FeedService {
    public FeedRespond<NoteRespondDto> getFeed(long userId, long cursorExclusive, int size, long noteId);
}
