package com.conote.common.dto.share;

import com.conote.common.enums.SharePermission;
import java.util.List;

public final class ShareDtos {
    private ShareDtos() { }

    public record NoteId(String noteId) { }

    public record Create(String noteId, String recipientEmail,
                         SharePermission permission) { }

    public record Update(long shareId, SharePermission permission) { }

    public record Revoke(long shareId) { }

    public record View(long shareId, String noteId, long recipientId,
                       String recipientEmail, SharePermission permission,
                       String createdAt) { }

    public record ListResult(List<View> shares) { }
}
