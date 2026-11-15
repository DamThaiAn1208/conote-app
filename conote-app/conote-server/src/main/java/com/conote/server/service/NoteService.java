package com.conote.server.service;

import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.tag.TagDtos;
import com.conote.common.enums.SharePermission;
import com.conote.common.enums.ShareStatus;
import com.conote.common.model.Note;
import com.conote.common.model.NoteOrder;
import com.conote.common.model.NoteOrderId;
import com.conote.common.model.Share;
import com.conote.common.model.Tag;
import com.conote.common.model.User;
import com.conote.server.config.EntityManagerUtil;
import com.conote.server.dao.NoteDao;
import com.conote.server.dao.ShareDao;
import com.conote.server.dao.TagDao;
import com.conote.server.dao.UserNoteStateDao;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Note operations own transactions and map entities to wire DTOs before closing EntityManager. */
public final class NoteService {
    private static final Set<String> COLORS = Set.of("default", "amber", "red", "green", "blue", "purple", "slate");
    private final NoteDao notes = new NoteDao();
    private final TagDao tags = new TagDao();
    private final ShareDao shares = new ShareDao();
    private final UserNoteStateDao states = new UserNoteStateDao();

    public NoteDtos.View create(long userId, NoteDtos.Create request) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        id(request.noteId());
        validateFields(request.title(), request.content(), request.color());
        String search = NoteContent.searchText(request.noteType(), request.content());
        List<Long> tagIds = request.tagIds() == null ? List.of() : request.tagIds();
        if (tagIds.size() > 50 || new HashSet<>(tagIds).size() != tagIds.size()) {
            throw new IllegalArgumentException("Invalid tag list");
        }
        return transaction(em -> {
            if (em.find(Note.class, request.noteId()) != null) {
                throw new OperationFailure(ErrorCode.CONFLICT, "Note ID already exists");
            }
            User owner = em.find(User.class, userId);
            if (owner == null || !Boolean.TRUE.equals(owner.getActive())) {
                throw new OperationFailure(ErrorCode.UNAUTHENTICATED, "Account is unavailable");
            }
            Note note = new Note();
            LocalDateTime now = now();
            note.setNoteId(request.noteId());
            note.setOwner(owner);
            note.setNoteType(request.noteType());
            note.setTitle(request.title());
            note.setContent(request.content());
            note.setSearchText(search);
            note.setColor(request.color());
            note.setVersion(1L);
            note.setPinned(false);
            note.setDeleted(false);
            note.setShareStatus(ShareStatus.PRIVATE);
            note.setCreatedAt(now);
            note.setUpdatedAt(now);
            for (Long tagId : tagIds) {
                if (tagId == null || tagId <= 0) throw new IllegalArgumentException("Invalid tag ID");
                note.getTags().add(tags.findOwned(em, userId, tagId)
                        .orElseThrow(() -> new OperationFailure(ErrorCode.NOT_FOUND, "Tag not found")));
            }
            notes.persist(em, note);
            return view(em, userId, note);
        });
    }

    public NoteDtos.ListResult list(long userId, NoteDtos.ListQuery query) {
        NoteDtos.ListQuery filter = query == null
                ? new NoteDtos.ListQuery(null, List.of(), NoteDtos.SortBy.UPDATED_AT, true) : query;
        List<Long> tagIds = filter.tagIds() == null ? List.of() : filter.tagIds();
        if (tagIds.size() > 50 || tagIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Invalid tag filter");
        }
        return read(em -> {
            List<NoteDtos.View> result = new ArrayList<>();
            for (Note note : notes.listVisible(em, userId, filter.keyword())) {
                if (!tagIds.isEmpty()) {
                    Set<Long> ids = new HashSet<>();
                    for (Tag tag : note.getTags()) ids.add(tag.getTagId());
                    if (!ids.containsAll(tagIds)) continue;
                }
                result.add(view(em, userId, note));
            }
            NoteDtos.SortBy sort = filter.sortBy() == null ? NoteDtos.SortBy.UPDATED_AT : filter.sortBy();
            Comparator<NoteDtos.View> valueOrder = switch (sort) {
                case CREATED_AT -> Comparator.comparing(NoteDtos.View::createdAt);
                case TITLE -> Comparator.comparing(v -> v.title().toLowerCase(Locale.ROOT));
                case MANUAL -> Comparator.comparingLong(NoteDtos.View::sortOrder);
                case UPDATED_AT -> Comparator.comparing(NoteDtos.View::updatedAt);
            };
            if (filter.descending()) valueOrder = valueOrder.reversed();
            result.sort(Comparator.comparing(NoteDtos.View::pinned).reversed()
                    .thenComparing(valueOrder).thenComparing(NoteDtos.View::noteId));
            return new NoteDtos.ListResult(List.copyOf(result));
        });
    }

    public NoteDtos.View get(long userId, NoteDtos.Id request) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        id(request.noteId());
        return read(em -> view(em, userId, visible(em, userId, request.noteId())));
    }

    public NoteDtos.View update(long userId, NoteDtos.Update request) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        id(request.noteId());
        version(request.expectedVersion());
        validateFields(request.title(), request.content(), request.color());
        return transaction(em -> {
            Note existing = visible(em, userId, request.noteId());
            SharePermission permission = permission(em, userId, existing);
            if (permission == SharePermission.VIEW) {
                throw new OperationFailure(ErrorCode.FORBIDDEN, "Read-only share");
            }
            String search = NoteContent.searchText(existing.getNoteType(), request.content());
            int changed = notes.updateIfVersion(em, userId, request.noteId(), request.expectedVersion(),
                    request.title(), request.content(), request.color(), search, now());
            if (changed == 0) throw conflict(em, userId, existing);
            em.clear();
            return view(em, userId, visible(em, userId, request.noteId()));
        });
    }

    public void delete(long userId, NoteDtos.Delete request) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        id(request.noteId());
        version(request.expectedVersion());
        transaction(em -> {
            Note note = visible(em, userId, request.noteId());
            if (note.getOwner().getUserId() != userId) {
                throw new OperationFailure(ErrorCode.FORBIDDEN, "Only owner can delete note");
            }
            if (notes.softDeleteOwnedIfVersion(em, userId, request.noteId(), request.expectedVersion(), now()) == 0) {
                throw conflict(em, userId, note);
            }
            return null;
        });
    }

    public NoteDtos.View setState(long userId, NoteDtos.StateSet request) {
        if (request == null) throw new IllegalArgumentException("Object payload is required");
        id(request.noteId());
        return transaction(em -> {
            Note note = visible(em, userId, request.noteId());
            NoteOrder state = state(em, userId, note);
            state.setPinned(request.pinned());
            state.setFavorite(request.favorite());
            state.setUpdatedAt(now());
            return view(em, userId, note);
        });
    }

    public void reorder(long userId, NoteDtos.Reorder request) {
        if (request == null || request.items() == null || request.items().size() > 1000) {
            throw new IllegalArgumentException("Invalid order list");
        }
        Set<String> seen = new HashSet<>();
        for (NoteDtos.OrderItem item : request.items()) {
            if (item == null) throw new IllegalArgumentException("Invalid order item");
            id(item.noteId());
            if (!seen.add(item.noteId())) throw new IllegalArgumentException("Duplicate note ID");
        }
        transaction(em -> {
            for (NoteDtos.OrderItem item : request.items()) {
                NoteOrder state = state(em, userId, visible(em, userId, item.noteId()));
                state.setSortOrder(item.sortOrder());
                state.setUpdatedAt(now());
            }
            return null;
        });
    }

    private NoteOrder state(EntityManager em, long userId, Note note) {
        return states.find(em, userId, note.getNoteId()).orElseGet(() -> {
            NoteOrder state = new NoteOrder();
            state.setId(new NoteOrderId(userId, note.getNoteId()));
            state.setUser(em.getReference(User.class, userId));
            state.setNote(note);
            state.setSortOrder(0L);
            state.setPinned(false);
            state.setFavorite(false);
            state.setUpdatedAt(now());
            states.persist(em, state);
            return state;
        });
    }

    NoteDtos.View view(EntityManager em, long userId, Note note) {
        NoteOrder state = states.find(em, userId, note.getNoteId()).orElse(null);
        User owner = note.getOwner();
        boolean shared = owner.getUserId() != userId;
        SharePermission access = shared ? permission(em, userId, note) : null;
        List<TagDtos.View> tagViews = note.getTags().stream()
                .map(tag -> new TagDtos.View(tag.getTagId(), tag.getTagName(), iso(tag.getCreatedAt())))
                .sorted(Comparator.comparing(TagDtos.View::name).thenComparingLong(TagDtos.View::tagId))
                .toList();
        return new NoteDtos.View(note.getNoteId(), owner.getUserId(), owner.getFullName(),
                note.getNoteType(), note.getTitle(), note.getContent(), note.getColor(), note.getVersion(),
                state != null && Boolean.TRUE.equals(state.getPinned()),
                state != null && Boolean.TRUE.equals(state.getFavorite()),
                state == null ? 0L : state.getSortOrder(), shared, access, tagViews,
                iso(note.getCreatedAt()), iso(note.getUpdatedAt()));
    }

    Note visible(EntityManager em, long userId, String noteId) {
        return notes.findVisible(em, userId, noteId)
                .orElseThrow(() -> new OperationFailure(ErrorCode.NOT_FOUND, "Note not found"));
    }

    OperationFailure conflict(EntityManager em, long userId, Note note) {
        em.clear();
        Note current = notes.findVisible(em, userId, note.getNoteId()).orElse(null);
        return current == null ? new OperationFailure(ErrorCode.NOT_FOUND, "Note not found")
                : new OperationFailure(ErrorCode.CONFLICT, "Note changed", view(em, userId, current));
    }

    private SharePermission permission(EntityManager em, long userId, Note note) {
        if (note.getOwner().getUserId() == userId) return SharePermission.EDIT;
        Share share = shares.findForRecipient(em, userId, note.getNoteId())
                .orElseThrow(() -> new OperationFailure(ErrorCode.NOT_FOUND, "Note not found"));
        return share.getPermission();
    }

    static void id(String value) {
        try {
            if (value != null && UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT))) return;
        } catch (IllegalArgumentException ignored) { }
        throw new IllegalArgumentException("Invalid note ID");
    }

    static void version(long value) {
        if (value <= 0) throw new IllegalArgumentException("Invalid note version");
    }

    static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    static String iso(LocalDateTime time) { return time.toInstant(ZoneOffset.UTC).toString(); }

    private static void validateFields(String title, String content, String color) {
        if (title == null || title.isBlank() || title.codePointCount(0, title.length()) > 200) {
            throw new IllegalArgumentException("Title must contain 1-200 characters");
        }
        if (content == null) throw new IllegalArgumentException("Content is required");
        if (!COLORS.contains(color)) throw new IllegalArgumentException("Invalid color");
    }

    private static <T> T read(Function<EntityManager, T> action) {
        try (EntityManager em = EntityManagerUtil.getEntityManager()) {
            return action.apply(em);
        }
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
