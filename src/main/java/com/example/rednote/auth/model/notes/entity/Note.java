package com.example.rednote.auth.model.notes.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.example.rednote.auth.model.notes.dto.NoteRespondDto;
import com.example.rednote.auth.model.user.dto.UserSimpleDto;
import com.example.rednote.auth.model.user.entity.User;

@Data
@Entity
@Table(
    name = "note")
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
    private ZonedDateTime publishTime;

    // @Convert(converter = StringListJsonConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
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
