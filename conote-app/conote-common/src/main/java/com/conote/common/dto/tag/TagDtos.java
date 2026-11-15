package com.conote.common.dto.tag;

import java.util.List;

public final class TagDtos {
    private TagDtos() { }

    public record Create(String name) { }

    public record Rename(long tagId, String name) { }

    public record Id(long tagId) { }

    public record Link(String noteId, long tagId, long expectedVersion) { }

    public record View(long tagId, String name, String createdAt) { }

    public record ListResult(List<View> tags) { }
}
