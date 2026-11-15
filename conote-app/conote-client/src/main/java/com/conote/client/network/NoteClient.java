package com.conote.client.network;

import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.dto.tag.TagDtos;
import com.conote.common.dto.share.ShareDtos;
import com.conote.common.dto.sync.SyncDtos;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Typed note and tag operations for one authenticated session. */
public final class NoteClient {
  private static final Gson GSON = new Gson();
  private final SocketClient socket;
  private final String token;

  public NoteClient(SocketClient socket, String token) {
    this.socket = Objects.requireNonNull(socket, "socket");
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Session token is required");
    }
    this.token = token;
  }

  public CompletableFuture<NoteDtos.ListResult> list() {
    return request(Operation.NOTE_LIST, null, NoteDtos.ListResult.class);
  }

  public CompletableFuture<SyncDtos.Snapshot> snapshot() {
    return request(Operation.SYNC_SNAPSHOT, null, SyncDtos.Snapshot.class);
  }

  public CompletableFuture<NoteDtos.View> create(NoteDtos.Create value) {
    return request(Operation.NOTE_CREATE, value, NoteDtos.View.class);
  }

  public CompletableFuture<NoteDtos.View> get(String noteId) {
    return request(Operation.NOTE_GET, new NoteDtos.Id(noteId), NoteDtos.View.class);
  }

  public CompletableFuture<NoteDtos.View> update(NoteDtos.Update value) {
    return request(Operation.NOTE_UPDATE, value, NoteDtos.View.class);
  }

  public CompletableFuture<Void> delete(NoteDtos.Delete value) {
    return socket.send(Operation.NOTE_DELETE, token, value).thenApply(ignored -> null);
  }

  public CompletableFuture<NoteDtos.View> setState(NoteDtos.StateSet value) {
    return request(Operation.NOTE_STATE_SET, value, NoteDtos.View.class);
  }

  public CompletableFuture<Void> reorder(NoteDtos.Reorder value) {
    return socket.send(Operation.NOTE_REORDER, token, value).thenApply(ignored -> null);
  }

  public CompletableFuture<TagDtos.ListResult> listTags() {
    return request(Operation.TAG_LIST, null, TagDtos.ListResult.class);
  }

  public CompletableFuture<TagDtos.View> createTag(String name) {
    return request(Operation.TAG_CREATE, new TagDtos.Create(name), TagDtos.View.class);
  }

  public CompletableFuture<NoteDtos.View> attachTag(TagDtos.Link value) {
    return request(Operation.TAG_ATTACH, value, NoteDtos.View.class);
  }

  public CompletableFuture<NoteDtos.View> detachTag(TagDtos.Link value) {
    return request(Operation.TAG_DETACH, value, NoteDtos.View.class);
  }

  public CompletableFuture<ShareDtos.ListResult> listShares(String noteId) {
    return request(Operation.SHARE_LIST, new ShareDtos.NoteId(noteId), ShareDtos.ListResult.class);
  }

  public CompletableFuture<ShareDtos.View> createShare(ShareDtos.Create value) {
    return request(Operation.SHARE_CREATE, value, ShareDtos.View.class);
  }

  public CompletableFuture<ShareDtos.View> updateShare(ShareDtos.Update value) {
    return request(Operation.SHARE_UPDATE, value, ShareDtos.View.class);
  }

  public CompletableFuture<Void> revokeShare(long shareId) {
    return socket.send(Operation.SHARE_REVOKE, token, new ShareDtos.Revoke(shareId))
        .thenApply(ignored -> null);
  }

  private <T> CompletableFuture<T> request(Operation operation, Object payload, Class<T> type) {
    return socket.send(operation, token, payload).thenApply(response -> decode(response, type));
  }

  private static <T> T decode(ProtocolResponse response, Class<T> type) {
    JsonElement data = response.data();
    if (data == null || data.isJsonNull()) {
      throw new TransportException(TransportException.Reason.PROTOCOL,
          "Server returned empty note data", null);
    }
    try {
      T decoded = GSON.fromJson(data, type);
      if (decoded == null) {
        throw new IllegalArgumentException("Empty note data");
      }
      return decoded;
    } catch (RuntimeException exception) {
      throw new TransportException(TransportException.Reason.PROTOCOL,
          "Server returned invalid note data", exception);
    }
  }
}
