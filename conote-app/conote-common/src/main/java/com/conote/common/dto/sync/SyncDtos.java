package com.conote.common.dto.sync;

import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.tag.TagDtos;
import java.util.List;

/** Full authorized snapshot; the server remains the source of truth. */
public final class SyncDtos {
    private SyncDtos() { }

    public record SnapshotRequest() { }

    public record Snapshot(List<NoteDtos.View> notes, List<TagDtos.View> tags,
                           String serverTime) { }
}
