package com.conote.server.dao;

import com.conote.common.model.Tag;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

/** Tag ownership is checked in each lookup. Caller manages transactions. */
public final class TagDao {
    public void persist(EntityManager em, Tag tag) {
        em.persist(tag);
    }

    public Optional<Tag> findOwned(EntityManager em, long userId, long tagId) {
        return em.createQuery("SELECT t FROM Tag t WHERE t.tagId = :tagId "
                        + "AND t.user.userId = :userId", Tag.class)
                .setParameter("tagId", tagId)
                .setParameter("userId", userId)
                .getResultStream().findFirst();
    }

    public List<Tag> listOwned(EntityManager em, long userId) {
        return em.createQuery("SELECT t FROM Tag t WHERE t.user.userId = :userId "
                        + "ORDER BY LOWER(t.tagName), t.tagId", Tag.class)
                .setParameter("userId", userId)
                .getResultList();
    }
}
