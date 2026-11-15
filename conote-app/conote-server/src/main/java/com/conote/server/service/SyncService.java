package com.conote.server.service;

import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.sync.SyncDtos;
import com.conote.common.dto.tag.TagDtos;
import com.conote.server.config.EntityManagerUtil;
import com.conote.server.dao.NoteDao;
import com.conote.server.dao.TagDao;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Comparator;

/** Full current authorized state for a client's refresh and revoked-share cleanup. */
public final class SyncService {
    private final NoteService noteService = new NoteService();
    private final NoteDao notes = new NoteDao();
    private final TagDao tags = new TagDao();

    public SyncDtos.Snapshot snapshot(long userId) {
        try (EntityManager em = EntityManagerUtil.getEntityManager()) {
            em.getTransaction().begin();
            try {
                var visible = notes.listVisible(em, userId, null).stream()
                        .map(note -> noteService.view(em, userId, note))
                        .sorted(Comparator.comparing(NoteDtos.View::pinned).reversed()
                                .thenComparing(NoteDtos.View::updatedAt, Comparator.reverseOrder())
                                .thenComparing(NoteDtos.View::noteId))
                        .toList();
                var ownedTags = tags.listOwned(em, userId).stream()
                        .map(tag -> new TagDtos.View(tag.getTagId(),
                                tag.getTagName(), NoteService.iso(tag.getCreatedAt())))
                        .toList();
                var result = new SyncDtos.Snapshot(visible, ownedTags, Instant.now().toString());
                em.getTransaction().commit();
                return result;
            } catch (RuntimeException exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw exception;
            }
        }
    }
}
