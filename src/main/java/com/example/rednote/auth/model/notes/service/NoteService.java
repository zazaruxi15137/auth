package com.example.rednote.auth.model.notes.service;

import org.springframework.data.domain.Pageable;

import java.util.Optional;

import org.springframework.data.domain.Page;

import com.example.rednote.auth.model.notes.entity.Note;

public interface NoteService {
    public Page<Note> findNoteByPage(Long userId, Pageable page);

    public Note save(Note note);

    public Optional<Note> findById(Long id);
    
}
