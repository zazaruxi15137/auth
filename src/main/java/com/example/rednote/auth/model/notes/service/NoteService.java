package com.example.rednote.auth.model.notes.service;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;

import com.example.rednote.auth.model.notes.entity.Note;

public interface NoteService {
    public Page<Note> findNoteByPage(Long userId, Pageable page);



    public Note save(Note note, MultipartFile[] files);

    public Optional<Note> findById(Long id);

    public List<Note> findAllbyIds(List<Long> ids);

    public Note updateNote(Long noteId,
                           Long currentUserId,
                            String title,
                            String content,
                           List<String> removeUrls,
                            MultipartFile[] addImages);
    
}
