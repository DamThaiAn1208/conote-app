package com.conote.server.dao;

import com.conote.common.model.NoteOrder;
import com.conote.common.model.NoteOrderId;
import jakarta.persistence.EntityManager;
import java.util.Optional;

/** Per-user pin, favorite and manual order. Caller manages transactions. */
public final class UserNoteStateDao {
    public Optional<NoteOrder> find(EntityManager em, long userId, String noteId) {
        return Optional.ofNullable(em.find(NoteOrder.class, new NoteOrderId(userId, noteId)));
    }

    public void persist(EntityManager em, NoteOrder state) {
        em.persist(state);
    }

    public void deleteForRecipient(EntityManager em, long recipientId, String noteId) {
        find(em, recipientId, noteId).ifPresent(em::remove);
    }
}
