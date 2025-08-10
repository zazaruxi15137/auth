package com.example.rednote.auth.model.notes.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.example.rednote.auth.common.tool.StringListJsonConverter;
import com.example.rednote.auth.model.notes.dto.NoteRespondDto;
import com.example.rednote.auth.model.user.dto.UserSimpleDto;
import com.example.rednote.auth.model.user.entity.User;

@Data
@Entity
@Table(
    name = "note")
@AllArgsConstructor
@NoArgsConstructor
public class Note implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="title", nullable=false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable=false)
    private String content;

    // 外键，关联user表
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "author_id",
        referencedColumnName = "id",
        nullable=false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User author;

    @Column(name = "publish_time",columnDefinition = "timestamp", updatable = false, nullable=false)
    @CreationTimestamp
    private LocalDateTime publishTime;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "images_urls",columnDefinition = "jsonb", nullable=false)
    private List<String> imagesUrls;


    public NoteRespondDto toNoteRespondDto(){
        return new NoteRespondDto(
            id,
            title,
            content,
            new UserSimpleDto(
                author.getId(),
                author.getUsername(),
                author.getEmail()
            ),
            publishTime.toString(),
           imagesUrls
        );
    }
}
