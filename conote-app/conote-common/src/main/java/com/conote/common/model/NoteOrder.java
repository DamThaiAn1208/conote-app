package com.conote.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "note_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoteOrder {
    @EmbeddedId
    private NoteOrderId id = new NoteOrderId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("noteId")
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @Column(name = "sort_order", nullable = false)
    private Long sortOrder;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
