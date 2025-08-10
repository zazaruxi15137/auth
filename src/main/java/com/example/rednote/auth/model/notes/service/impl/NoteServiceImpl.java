package com.example.rednote.auth.model.notes.service.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.Optional;
import org.springframework.data.domain.Page;

import com.example.rednote.auth.common.exception.CustomException;
import com.example.rednote.auth.common.tool.SerializaUtil;
import com.example.rednote.auth.model.notes.entity.Note;
import com.example.rednote.auth.model.notes.repository.NoteRepository;
import com.example.rednote.auth.model.notes.service.NoteService;
import com.example.rednote.auth.model.user.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;

    private final UserService userService;
    @Override
    public Page<Note> findNoteByPage(Long userId, Pageable page) {

       return noteRepository.findByAuthor_Id(userId, page);
    }

    @Override
    public Note save(Note note) {
    return noteRepository.save(note);
    }

    @Override
    public Optional<Note> findById(Long id) {
       return noteRepository.findById(id);
    }
    
}
