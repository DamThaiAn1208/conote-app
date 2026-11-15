package com.conote.server.service;

import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.share.ShareDtos;
import com.conote.common.enums.SharePermission;
import com.conote.common.enums.ShareStatus;
import com.conote.common.model.Note;
import com.conote.common.model.Share;
import com.conote.common.model.User;
import com.conote.server.config.EntityManagerUtil;
import com.conote.server.dao.NoteDao;
import com.conote.server.dao.ShareDao;
import com.conote.server.dao.UserNoteStateDao;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.function.Function;

/** Owner-only sharing operations; recipient access is derived from current share rows. */
public final class ShareService {
    private final NoteDao notes = new NoteDao();
    private final ShareDao shares = new ShareDao();
    private final UserNoteStateDao states = new UserNoteStateDao();

    public ShareDtos.ListResult list(long actorId, ShareDtos.NoteId request) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        NoteService.id(request.noteId());
        try (EntityManager em = EntityManagerUtil.getEntityManager()) {
            owned(em, actorId, request.noteId());
            return new ShareDtos.ListResult(shares.listForOwnedNote(em, actorId, request.noteId())
                    .stream().map(ShareService::view).toList());
        }
    }

    public ShareDtos.View create(long actorId, ShareDtos.Create request) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        NoteService.id(request.noteId());
        permission(request.permission());
        String email = email(request.recipientEmail());
        return transaction(em -> {
            Note note = owned(em, actorId, request.noteId());
            User recipient = em.createQuery("SELECT u FROM User u WHERE LOWER(u.email) = :email "
                            + "AND u.active = true", User.class)
                    .setParameter("email", email)
                    .getResultStream().findFirst()
                    .orElseThrow(() -> new OperationFailure(ErrorCode.NOT_FOUND, "Recipient not found"));
            if (recipient.getUserId() == actorId) {
                throw new IllegalArgumentException("Cannot share a note with yourself");
            }
            boolean duplicate = !em.createQuery("SELECT s.shareId FROM Share s WHERE s.note.noteId = :noteId "
                            + "AND s.sharedWith.userId = :recipientId", Long.class)
                    .setParameter("noteId", note.getNoteId())
                    .setParameter("recipientId", recipient.getUserId())
                    .getResultList().isEmpty();
            if (duplicate) throw new OperationFailure(ErrorCode.CONFLICT, "Note is already shared with this user");
            Share share = new Share();
            share.setNote(note);
            share.setSharedBy(note.getOwner());
            share.setSharedWith(recipient);
            share.setPermission(request.permission());
            share.setCreatedAt(NoteService.now());
            shares.persist(em, share);
            note.setShareStatus(ShareStatus.SHARED);
            em.flush();
            return view(share);
        });
    }

    public ShareDtos.View update(long actorId, ShareDtos.Update request) {
        if (request == null || request.shareId() <= 0) {
            throw new IllegalArgumentException("Invalid share ID");
        }
        permission(request.permission());
        return transaction(em -> {
            Share share = ownedShare(em, actorId, request.shareId());
            share.setPermission(request.permission());
            return view(share);
        });
    }

    public void revoke(long actorId, ShareDtos.Revoke request) {
        if (request == null || request.shareId() <= 0) {
            throw new IllegalArgumentException("Invalid share ID");
        }
        transaction(em -> {
            Share share = ownedShare(em, actorId, request.shareId());
            Note note = share.getNote();
            long recipientId = share.getSharedWith().getUserId();
            states.deleteForRecipient(em, recipientId, note.getNoteId());
            em.remove(share);
            em.flush();
            if (shares.listForOwnedNote(em, actorId, note.getNoteId()).isEmpty()) {
                note.setShareStatus(ShareStatus.PRIVATE);
            }
            return null;
        });
    }

    private Note owned(EntityManager em, long ownerId, String noteId) {
        return notes.findOwned(em, ownerId, noteId)
                .orElseThrow(() -> new OperationFailure(ErrorCode.NOT_FOUND, "Note not found"));
    }

    private Share ownedShare(EntityManager em, long ownerId, long shareId) {
        Share share = shares.findById(em, shareId)
                .orElseThrow(() -> new OperationFailure(ErrorCode.NOT_FOUND, "Share not found"));
        if (Boolean.TRUE.equals(share.getNote().getDeleted())
                || share.getNote().getOwner().getUserId() != ownerId) {
            throw new OperationFailure(ErrorCode.NOT_FOUND, "Share not found");
        }
        return share;
    }

    private static ShareDtos.View view(Share share) {
        User recipient = share.getSharedWith();
        return new ShareDtos.View(share.getShareId(), share.getNote().getNoteId(),
                recipient.getUserId(), recipient.getEmail(), share.getPermission(),
                NoteService.iso(share.getCreatedAt()));
    }

    private static void permission(SharePermission permission) {
        if (permission == null) throw new IllegalArgumentException("Share permission is required");
    }

    private static String email(String value) {
        if (value == null || value.isBlank() || value.length() > 254 || !value.contains("@")) {
            throw new IllegalArgumentException("Invalid recipient email");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static <T> T transaction(Function<EntityManager, T> action) {
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
