package com.conote.server.dao;

import com.conote.common.model.Note;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** The calling service owns the EntityManager and transaction. */
public final class NoteDao {
    public void persist(EntityManager em, Note note) {
        em.persist(note);
    }

    public Optional<Note> findOwned(EntityManager em, long ownerId, String noteId) {
        return em.createQuery("SELECT n FROM Note n WHERE n.noteId = :noteId "
                        + "AND n.owner.userId = :ownerId AND n.deleted = false", Note.class)
                .setParameter("noteId", noteId)
                .setParameter("ownerId", ownerId)
                .getResultStream().findFirst();
    }

    public Optional<Note> findVisible(EntityManager em, long userId, String noteId) {
        return em.createQuery("SELECT n FROM Note n WHERE n.noteId = :noteId "
                        + "AND n.deleted = false AND (n.owner.userId = :userId OR EXISTS "
                        + "(SELECT s.shareId FROM Share s WHERE s.note = n AND s.sharedWith.userId = :userId))",
                        Note.class)
                .setParameter("noteId", noteId)
                .setParameter("userId", userId)
                .getResultStream().findFirst();
    }

    public List<Note> listVisible(EntityManager em, long userId, String keyword) {
        String search = keyword == null || keyword.isBlank() ? null : "%"
                + keyword.toLowerCase(java.util.Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return em.createQuery("SELECT DISTINCT n FROM Note n WHERE n.deleted = false "
                        + "AND (n.owner.userId = :userId OR EXISTS "
                        + "(SELECT s.shareId FROM Share s WHERE s.note = n AND s.sharedWith.userId = :userId)) "
                        + "AND (:search IS NULL OR LOWER(n.title) LIKE :search ESCAPE '!' "
                        + "OR LOWER(n.searchText) LIKE :search ESCAPE '!') ORDER BY n.updatedAt DESC, n.noteId",
                        Note.class)
                .setParameter("userId", userId)
                .setParameter("search", search)
                .getResultList();
    }

    /** Returns 0 if the version changed or the caller lost edit access. */
    public int updateIfVersion(EntityManager em, long userId, String noteId, long expectedVersion,
                               String title, String content, String color, String searchText,
                               LocalDateTime now) {
        return em.createNativeQuery("UPDATE notes n SET n.title = :title, n.content = :content, "
                        + "n.color = :color, n.search_text = :searchText, n.updated_at = :now, "
                        + "n.version = n.version + 1 WHERE n.note_id = :noteId AND n.version = :version "
                        + "AND n.is_delete = false AND (n.owner_id = :userId OR EXISTS "
                        + "(SELECT 1 FROM shares s WHERE s.note_id = n.note_id "
                        + "AND s.share_with = :userId AND s.permission = 'EDIT'))")
                .setParameter("title", title)
                .setParameter("content", content)
                .setParameter("color", color)
                .setParameter("searchText", searchText)
                .setParameter("now", now)
                .setParameter("noteId", noteId)
                .setParameter("version", expectedVersion)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public int softDeleteOwnedIfVersion(EntityManager em, long ownerId, String noteId,
                                        long expectedVersion, LocalDateTime now) {
        return em.createNativeQuery("UPDATE notes n SET n.is_delete = true, n.updated_at = :now, "
                        + "n.version = n.version + 1 WHERE n.note_id = :noteId "
                        + "AND n.owner_id = :ownerId AND n.version = :version AND n.is_delete = false")
                .setParameter("now", now)
                .setParameter("noteId", noteId)
                .setParameter("ownerId", ownerId)
                .setParameter("version", expectedVersion)
                .executeUpdate();
    }
}
