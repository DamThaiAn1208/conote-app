package com.conote.common.dto.note;

import com.conote.common.enums.NoteType;
import com.conote.common.enums.SharePermission;
import com.conote.common.dto.tag.TagDtos;
import java.util.List;

/** ISO-8601 UTC strings and UUID strings keep Gson independent of JPA/time adapters. */
public final class NoteDtos {
    private NoteDtos() { }

    public enum SortBy { UPDATED_AT, CREATED_AT, TITLE, MANUAL }

    public record Create(String noteId, NoteType noteType, String title, String content,
                         String color, List<Long> tagIds) { }

    public record ListQuery(String keyword, List<Long> tagIds, SortBy sortBy,
                            boolean descending) { }

    public record Id(String noteId) { }

    public record Update(String noteId, long expectedVersion, String title,
                         String content, String color) { }

    public record Delete(String noteId, long expectedVersion) { }

    public record StateSet(String noteId, boolean pinned, boolean favorite) { }

    public record OrderItem(String noteId, long sortOrder) { }

    public record Reorder(List<OrderItem> items) { }

    public record View(String noteId, long ownerId, String ownerName,
                       NoteType noteType, String title, String content, String color,
                       long version, boolean pinned, boolean favorite, long sortOrder,
                       boolean shared, SharePermission permission, List<TagDtos.View> tags,
                       String createdAt, String updatedAt) { }

    public record ListResult(List<View> notes) { }
}
