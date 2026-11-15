package com.conote.common.model;

import com.conote.common.enums.NoteType;
import com.conote.common.enums.ShareStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notes", indexes = {
        @Index(name = "idx_notes_owner_deleted", columnList = "owner_id,is_delete"),
        @Index(name = "idx_notes_updated", columnList = "updated_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Note {
    @Id
    // Assigned UUID preserves local cache IDs during the explicit import flow.
    @Column(name = "note_id", length = 36)
    private String noteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(length = 20, nullable = false)
    private String color;

    @Column(name = "search_text", columnDefinition = "LONGTEXT")
    private String searchText;

    // Services use conditional updates against this value; every content/tag/delete write increments it.
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "is_pinned")
    private Boolean pinned;

    @Column(name = "is_delete", nullable = false)
    private Boolean deleted;

    @Enumerated(EnumType.STRING)
    @Column(name = "share_status", nullable = false, length = 16)
    private ShareStatus shareStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 16)
    private NoteType noteType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "note", fetch = FetchType.LAZY)
    private List<Share> shares = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "note_tags",
            joinColumns = @JoinColumn(name = "note_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags = new ArrayList<>();
}


