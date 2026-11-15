package com.conote.server.service;

import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.tag.TagDtos;
import com.conote.common.model.Note;
import com.conote.common.model.Tag;
import com.conote.common.model.User;
import com.conote.server.config.EntityManagerUtil;
import com.conote.server.dao.NoteDao;
import com.conote.server.dao.TagDao;
import jakarta.persistence.EntityManager;

/** Tag ownership and note version checks are enforced in the same transaction as each change. */
public final class TagService {
    private final TagDao tags = new TagDao();
    private final NoteDao notes = new NoteDao();
    private final NoteService noteService = new NoteService();

    public TagDtos.View create(long userId, TagDtos.Create request) {
        String name = name(request == null ? null : request.name());
        return transaction(em -> {
            if (exists(em, userId, name, null)) throw new OperationFailure(ErrorCode.CONFLICT, "Tag already exists");
            Tag tag = new Tag();
            tag.setUser(em.getReference(User.class, userId));
            tag.setTagName(name);
            tag.setCreatedAt(NoteService.now());
            tags.persist(em, tag);
            em.flush();
            return view(tag);
        });
    }

    public TagDtos.ListResult list(long userId) {
        try (EntityManager em = EntityManagerUtil.getEntityManager()) {
            return new TagDtos.ListResult(tags.listOwned(em, userId).stream().map(TagService::view).toList());
        }
    }

    public TagDtos.View rename(long userId, TagDtos.Rename request) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        long tagId = tagId(request.tagId());
        String name = name(request.name());
        return transaction(em -> {
            Tag tag = owned(em, userId, tagId);
            if (exists(em, userId, name, tagId)) throw new OperationFailure(ErrorCode.CONFLICT, "Tag already exists");
            if (!tag.getTagName().equals(name)) {
                bumpTaggedNotes(em, tagId);
                tag.setTagName(name);
            }
            return view(tag);
        });
    }

    public void delete(long userId, TagDtos.Id request) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        long tagId = tagId(request.tagId());
        transaction(em -> {
            Tag tag = owned(em, userId, tagId);
            bumpTaggedNotes(em, tagId);
            em.remove(tag);
            return null;
        });
    }

    public NoteDtos.View attach(long userId, TagDtos.Link request) { return link(userId, request, true); }
    public NoteDtos.View detach(long userId, TagDtos.Link request) { return link(userId, request, false); }

    private NoteDtos.View link(long userId, TagDtos.Link request, boolean attach) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        NoteService.id(request.noteId());
        NoteService.version(request.expectedVersion());
        long tagId = tagId(request.tagId());
        return transaction(em -> {
            Note note = notes.findOwned(em, userId, request.noteId())
                    .orElseThrow(() -> new OperationFailure(ErrorCode.NOT_FOUND, "Note not found"));
            Tag tag = owned(em, userId, tagId);
            if (note.getVersion() != request.expectedVersion()) {
                throw noteService.conflict(em, userId, note);
            }
            boolean alreadyLinked = note.getTags().stream().anyMatch(t -> t.getTagId().equals(tagId));
            if (attach == alreadyLinked) return noteService.view(em, userId, note);
            if (attach && note.getTags().size() >= 50) {
                throw new IllegalArgumentException("Note cannot have more than 50 tags");
            }
            int changed = em.createNativeQuery("UPDATE notes SET version = version + 1, updated_at = :now "
                            + "WHERE note_id = :noteId AND owner_id = :ownerId AND version = :version "
                            + "AND is_delete = false")
                    .setParameter("now", NoteService.now())
                    .setParameter("noteId", request.noteId())
                    .setParameter("ownerId", userId)
                    .setParameter("version", request.expectedVersion())
                    .executeUpdate();
            if (changed == 0) throw noteService.conflict(em, userId, note);
            em.clear();
            note = noteService.visible(em, userId, request.noteId());
            tag = owned(em, userId, tagId);
            if (attach) note.getTags().add(tag);
            else note.getTags().removeIf(t -> t.getTagId().equals(tagId));
            em.flush();
            em.refresh(note);
            return noteService.view(em, userId, note);
        });
    }

    private void bumpTaggedNotes(EntityManager em, long tagId) {
        em.createNativeQuery("UPDATE notes SET version = version + 1, updated_at = :now "
                        + "WHERE is_delete = false AND note_id IN "
                        + "(SELECT note_id FROM note_tags WHERE tag_id = :tagId)")
                .setParameter("now", NoteService.now())
                .setParameter("tagId", tagId)
                .executeUpdate();
    }

    private boolean exists(EntityManager em, long userId, String name, Long exceptTagId) {
        return !em.createQuery("SELECT t.tagId FROM Tag t WHERE t.user.userId = :userId "
                        + "AND LOWER(t.tagName) = LOWER(:name)", Long.class)
                .setParameter("userId", userId)
                .setParameter("name", name)
                .getResultList().stream().filter(id -> !id.equals(exceptTagId)).toList().isEmpty();
    }

    private Tag owned(EntityManager em, long userId, long tagId) {
        return tags.findOwned(em, userId, tagId)
                .orElseThrow(() -> new OperationFailure(ErrorCode.NOT_FOUND, "Tag not found"));
    }

    private static String name(String name) {
        if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 50) {
            throw new IllegalArgumentException("Tag name must contain 1-50 characters");
        }
        return name.trim();
    }

    private static long tagId(long tagId) {
        if (tagId <= 0) throw new IllegalArgumentException("Invalid tag ID");
        return tagId;
    }

    private static TagDtos.View view(Tag tag) {
        return new TagDtos.View(tag.getTagId(), tag.getTagName(), NoteService.iso(tag.getCreatedAt()));
    }

    private static <T> T transaction(java.util.function.Function<EntityManager, T> action) {
        try (EntityManager em = EntityManagerUtil.getEntityManager()) {
            em.getTransaction().begin();
            try {
                T result = action.apply(em);
                em.getTransaction().commit();
                return result;
            } catch (RuntimeException exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw exception;
            }
        }
    }
}
