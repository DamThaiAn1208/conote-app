package com.conote.server.dao;

import com.conote.common.model.Share;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

/** Share writes require the service to verify that the actor owns the note. */
public final class ShareDao {
    public void persist(EntityManager em, Share share) {
        em.persist(share);
    }

    public Optional<Share> findById(EntityManager em, long shareId) {
        return Optional.ofNullable(em.find(Share.class, shareId));
    }

    public List<Share> listForOwnedNote(EntityManager em, long ownerId, String noteId) {
        return em.createQuery("SELECT s FROM Share s WHERE s.note.noteId = :noteId "
                        + "AND s.note.owner.userId = :ownerId AND s.note.deleted = false "
                        + "ORDER BY s.shareId", Share.class)
                .setParameter("noteId", noteId)
                .setParameter("ownerId", ownerId)
                .getResultList();
    }

    public Optional<Share> findForRecipient(EntityManager em, long recipientId, String noteId) {
        return em.createQuery("SELECT s FROM Share s WHERE s.note.noteId = :noteId "
                        + "AND s.sharedWith.userId = :recipientId AND s.note.deleted = false",
                        Share.class)
                .setParameter("noteId", noteId)
                .setParameter("recipientId", recipientId)
                .getResultStream().findFirst();
    }
}
