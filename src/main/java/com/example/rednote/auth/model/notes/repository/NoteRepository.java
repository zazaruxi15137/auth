package com.example.rednote.auth.model.notes.repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.rednote.auth.model.notes.entity.Note;

public interface NoteRepository extends JpaRepository<Note,Long>{
    Page<Note> findByAuthor_Id(Long authorId, Pageable pageable);
}
