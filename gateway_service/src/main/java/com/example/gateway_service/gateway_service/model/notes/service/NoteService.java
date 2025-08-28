package com.example.gateway_service.gateway_service.model.notes.service;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.example.gateway_service.gateway_service.model.notes.entity.Note;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;


public interface NoteService {
    public Page<Note> findNoteByPage(Long userId, Pageable page);



    public Note save(Note note, MultipartFile[] files);

    public Optional<Note> findById(Long id);

    public List<Note> findAllbyIds(List<Long> ids);

    public Note updateNote(Long noteId,
                           Long currentUserId,
                            String title,
                            String content,
                            boolean isPublic,
                           List<String> removeUrls,
                            MultipartFile[] addImages); 

    public Map<Long, Note> findReadableMap(long userId, List<Long> ids);
    
}
