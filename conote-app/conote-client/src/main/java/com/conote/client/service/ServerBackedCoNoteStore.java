package com.conote.client.service;

import com.conote.client.cache.ChecklistContentCodec;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.cache.JsonStore;
import com.conote.client.model.ChecklistItemModel;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.client.model.SortMode;
import com.conote.client.model.ShareMember;
import com.conote.client.network.NoteClient;
import com.conote.client.network.RemoteOperationException;
import com.conote.client.network.TransportException;
import com.conote.client.util.RichTextContentCodec;
import com.conote.common.dto.auth.AuthDtos;
import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.tag.TagDtos;
import com.conote.common.dto.share.ShareDtos;
import com.conote.common.dto.sync.SyncDtos;
import com.conote.common.enums.NoteType;
import com.conote.common.enums.SharePermission;
import com.conote.common.model.Note;
import com.conote.common.model.Tag;
import java.time.Instant;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Server-authoritative store. Drafts stay in UI memory until each write is acknowledged. */
public final class ServerBackedCoNoteStore extends CoNoteStore implements AutoCloseable {
  private final NoteClient client;
  private final JsonStore jsonStore = new JsonStore();
  private final Path snapshotFile;
  private final Map<String, NoteDtos.View> accepted = new ConcurrentHashMap<>();
  private final Map<String, CompletableFuture<Void>> writes = new HashMap<>();
  private final Map<String, Long> tagIds = new ConcurrentHashMap<>();
  private final Map<String, List<ShareDtos.View>> sharesByNote = new ConcurrentHashMap<>();
  private final Set<String> conflicts = ConcurrentHashMap.newKeySet();
  private final Set<String> blockedNotes = ConcurrentHashMap.newKeySet();
  private final StringProperty serverStatus = new SimpleStringProperty("");
  private List<TagDtos.View> currentTags = List.of();
  private boolean readOnlyOffline;
  private NoteDtos.Create pendingCreate;
  private boolean createInFlight;
  private String lastBlockedNoteId;
  private volatile boolean closed;

  public ServerBackedCoNoteStore(AuthDtos.UserView user, NoteClient client) {
    super(user);
    this.client = java.util.Objects.requireNonNull(client, "client");
    snapshotFile = ClientStoragePaths.userServerSnapshotFile(user.userId());
  }

  public StringProperty serverStatusProperty() {
    return serverStatus;
  }

  public void refreshFromServer() {
    refreshFromServer(null);
  }

  public void refreshFromServer(Runnable onLoaded) {
    if (closed) return;
    if (hasUnconfirmedDrafts()) {
      serverStatus.set("Có bản nháp chưa được lưu. Chọn tải bản máy chủ hoặc giữ bản nháp trước khi làm mới.");
      return;
    }
    loadingProperty().set(true);
    client.snapshot().whenComplete((result, failure) -> Platform.runLater(() -> {
      if (closed) return;
      loadingProperty().set(false);
      if (failure != null) {
        report(failure);
        if (getNotes().isEmpty()) showReadOnlyCache();
        return;
      }
      installSnapshot(result);
      saveAcknowledgedSnapshot();
      if (onLoaded != null) onLoaded.run();
    }));
  }

  /** Caller must first confirm that discarding local drafts is intentional. */
  public void discardDraftsAndReload() {
    if (closed) return;
    if (createInFlight || writes.values().stream().anyMatch(future -> !future.isDone())) {
      serverStatus.set("Đang chờ máy chủ trả lời. Hãy thử tải lại sau khi thao tác kết thúc.");
      return;
    }
    blockedNotes.clear();
    conflicts.clear();
    writes.clear();
    pendingCreate = null;
    lastBlockedNoteId = null;
    refreshFromServer();
  }

  public boolean hasUnconfirmedDrafts() {
    return pendingCreate != null || !blockedNotes.isEmpty()
        || writes.values().stream().anyMatch(future -> !future.isDone());
  }

  public boolean canOverwriteBlockedDraft() {
    return lastBlockedNoteId != null && blockedNotes.contains(lastBlockedNoteId);
  }

  public void overwriteBlockedDraft() {
    String noteId = lastBlockedNoteId;
    if (closed || noteId == null || !blockedNotes.contains(noteId)) return;
    if (writes.values().stream().anyMatch(future -> !future.isDone())) {
      serverStatus.set("Đang chờ thao tác cũ kết thúc. Hãy thử lại sau.");
      return;
    }
    NoteModel draft = getNotes().stream().filter(note -> note.getId().equals(noteId))
        .findFirst().orElse(null);
    if (draft == null) return;
    String content = draft.getType() == NoteType.CHECKLIST
        ? ChecklistContentCodec.encode(new ArrayList<>(draft.getChecklistItems())) : draft.getContent();
    client.get(noteId).thenCompose(current -> client.update(new NoteDtos.Update(
        noteId, current.version(), draft.getTitle(), content,
        (draft.getColor() == null ? NoteColor.DEFAULT : draft.getColor()).cssName())))
        .whenComplete((view, failure) -> Platform.runLater(() -> {
          if (closed) return;
          if (failure != null) {
            report(failure);
            return;
          }
          blockedNotes.remove(noteId);
          conflicts.remove(noteId);
          lastBlockedNoteId = null;
          acceptMetadata(view);
          saveAcknowledgedSnapshot();
          serverStatus.set("Đã lưu bản nháp sau khi bạn xác nhận ghi đè.");
        }));
  }

  private void installSnapshot(SyncDtos.Snapshot snapshot) {
      accepted.clear();
      blockedNotes.clear();
      conflicts.clear();
      lastBlockedNoteId = null;
      readOnlyOffline = false;
      currentTags = snapshot.tags() == null ? List.of() : List.copyOf(snapshot.tags());
      tagIds.clear();
      for (TagDtos.View tag : currentTags) {
        tagIds.put(key(tag.name()), tag.tagId());
      }
      List<NoteModel> models = new ArrayList<>();
      for (NoteDtos.View view : snapshot.notes() == null ? List.<NoteDtos.View>of() : snapshot.notes()) {
        accepted.put(view.noteId(), view);
        models.add(toModel(view));
      }
      getNotes().setAll(models);
      serverStatus.set("");
  }

  private void showReadOnlyCache() {
    try {
      SyncDtos.Snapshot snapshot = jsonStore.read(snapshotFile, SyncDtos.Snapshot.class,
          () -> new SyncDtos.Snapshot(List.of(), List.of(), null));
      if (snapshot.notes() == null || snapshot.notes().isEmpty()) return;
      installSnapshot(snapshot);
      readOnlyOffline = true;
      serverStatus.set("Mất kết nối. Đang hiển thị bản đã lưu trên máy ở chế độ chỉ đọc.");
    } catch (RuntimeException ignored) {
      serverStatus.set("Mất kết nối và không đọc được bản ghi chú đã lưu trên máy.");
    }
  }

  private void saveAcknowledgedSnapshot() {
    if (closed || readOnlyOffline) return;
    jsonStore.write(snapshotFile, new SyncDtos.Snapshot(
        new ArrayList<>(accepted.values()), currentTags, Instant.now().toString()));
  }

  /** Explicit, retryable migration of legacy local notes; the old files are never modified. */
  public CompletableFuture<Integer> importLegacyNotes(List<Note> legacyNotes) {
    CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
    for (Note legacy : legacyNotes == null ? List.<Note>of() : legacyNotes) {
      if (legacy == null || Boolean.TRUE.equals(legacy.getDeleted())) continue;
      chain = chain.thenCompose(count -> importOne(legacy).thenApply(ignored -> count + 1));
    }
    return chain.whenComplete((count, failure) -> Platform.runLater(() -> {
      if (closed) return;
      if (failure != null) report(failure);
      else refreshFromServer();
    }));
  }

  private CompletableFuture<NoteDtos.View> importOne(Note legacy) {
    String id = legacy.getNoteId();
    try { UUID.fromString(id); }
    catch (RuntimeException exception) { id = UUID.randomUUID().toString(); }
    String title = legacy.getTitle() == null || legacy.getTitle().isBlank()
        ? "Untitled note" : legacy.getTitle();
    String content = legacy.getContent() == null ? "" : legacy.getContent();
    NoteType type = legacy.getNoteType() == null ? NoteType.TEXT : legacy.getNoteType();
    String color = legacy.getColor() == null ? NoteColor.DEFAULT.cssName() : legacy.getColor();
    String noteId = id;
    CompletableFuture<NoteDtos.View> chain = accepted.containsKey(noteId)
        ? CompletableFuture.completedFuture(accepted.get(noteId))
        : resolveOrCreate(new NoteDtos.Create(noteId, type, title, content, color, List.of()));
    if (Boolean.TRUE.equals(legacy.getPinned())) {
      chain = chain.thenCompose(view -> view.pinned()
          ? CompletableFuture.completedFuture(view)
          : client.setState(new NoteDtos.StateSet(view.noteId(), true, view.favorite())));
    }
    if (legacy.getTags() != null) {
      for (Tag tag : legacy.getTags()) {
        if (tag == null || tag.getTagName() == null || tag.getTagName().isBlank()) continue;
        String tagName = tag.getTagName();
        chain = chain.thenCompose(view -> {
          if (view.tags().stream().anyMatch(existing -> key(existing.name()).equals(key(tagName)))) {
            return CompletableFuture.completedFuture(view);
          }
          return ensureTag(tagName).thenCompose(tagId -> client.attachTag(
              new TagDtos.Link(view.noteId(), tagId, view.version())));
        });
      }
    }
    return chain.thenApply(view -> { accepted.put(view.noteId(), view); return view; });
  }

  public void createNoteAsync(NoteType type, Consumer<NoteModel> onCreated) {
    if (closed) return;
    if (readOnlyOffline) {
      serverStatus.set("Đang xem cache ngoại tuyến. Hãy kết nối máy chủ trước khi tạo ghi chú.");
      return;
    }
    if (createInFlight) return;
    if (pendingCreate == null) {
      NoteType actualType = type == null ? NoteType.TEXT : type;
      String title = actualType == NoteType.CHECKLIST ? "Untitled Checklist Note" : "Untitled Text Note";
      String content = actualType == NoteType.CHECKLIST
          ? ChecklistContentCodec.encode(List.of(new ChecklistItemModel("", false))) : "";
      pendingCreate = new NoteDtos.Create(UUID.randomUUID().toString(), actualType,
          title, content, NoteColor.DEFAULT.cssName(), List.of());
    }
    createInFlight = true;
    NoteDtos.Create request = pendingCreate;
    resolveOrCreate(request).whenComplete((view, failure) -> Platform.runLater(() -> {
          if (closed) return;
          createInFlight = false;
          if (failure != null) {
            report(failure);
            return;
          }
          pendingCreate = null;
          accepted.put(view.noteId(), view);
          NoteModel model = getNotes().stream().filter(n -> n.getId().equals(view.noteId()))
              .findFirst().orElse(null);
          if (model == null) {
            model = toModel(view);
            getNotes().add(model);
          }
          saveAcknowledgedSnapshot();
          serverStatus.set("");
          if (onCreated != null) onCreated.accept(model);
        }));
  }

  private CompletableFuture<NoteDtos.View> resolveOrCreate(NoteDtos.Create request) {
    return client.get(request.noteId()).handle((existing, failure) -> {
      if (failure == null) return CompletableFuture.completedFuture(existing);
      Throwable actual = failure instanceof CompletionException && failure.getCause() != null
          ? failure.getCause() : failure;
      if (actual instanceof RemoteOperationException remote && remote.code() == ErrorCode.NOT_FOUND) {
        return client.create(request).handle((created, createFailure) -> {
          if (createFailure == null) return CompletableFuture.completedFuture(created);
          Throwable createActual = createFailure instanceof CompletionException
              && createFailure.getCause() != null ? createFailure.getCause() : createFailure;
          if (createActual instanceof RemoteOperationException conflict
              && conflict.code() == ErrorCode.CONFLICT) {
            return client.get(request.noteId());
          }
          return CompletableFuture.<NoteDtos.View>failedFuture(createActual);
        }).thenCompose(Function.identity());
      }
      return CompletableFuture.<NoteDtos.View>failedFuture(actual);
    }).thenCompose(Function.identity());
  }

  @Override
  public NoteModel createNote(NoteType type) {
    throw new IllegalStateException("Use createNoteAsync for authenticated notes");
  }

  @Override
  public void deleteNote(NoteModel note) {
    deleteNoteWithAck(note, null);
  }

  public void deleteNoteWithAck(NoteModel note, Runnable onDeleted) {
    if (!editable(note, true)) return;
    enqueue(note.getId(), version -> client.delete(new NoteDtos.Delete(note.getId(), version))
        .thenApply(ignored -> null), ignored -> {
      accepted.remove(note.getId());
      getNotes().remove(note);
      if (onDeleted != null) onDeleted.run();
    });
  }

  @Override
  public void updateTitle(NoteModel note, String value) {
    if (!editable(note, false)) return;
    note.setTitle(value);
    saveDraft(note);
  }

  @Override
  public void updateContent(NoteModel note, String value) {
    if (!editable(note, false)) return;
    note.setContent(value);
    saveDraft(note);
  }

  @Override
  public void updatePlainTextContent(NoteModel note, String value) {
    updateContent(note, RichTextContentCodec.plainText(value));
  }

  @Override
  public void updateNoteType(NoteModel note, NoteType type) {
    serverStatus.set("Máy chủ chưa hỗ trợ đổi loại ghi chú sau khi tạo.");
  }

  @Override
  public void togglePin(NoteModel note) {
    if (note == null || readOnlyOffline || !accepted.containsKey(note.getId())) return;
    enqueue(note.getId(), version -> {
      NoteDtos.View current = accepted.get(note.getId());
      return client.setState(new NoteDtos.StateSet(
          note.getId(), !current.pinned(), current.favorite()));
    }, this::acceptMetadata);
  }

  @Override
  public void toggleFavorite(NoteModel note) {
    if (note == null || readOnlyOffline || !accepted.containsKey(note.getId())) return;
    enqueue(note.getId(), version -> {
      NoteDtos.View current = accepted.get(note.getId());
      return client.setState(new NoteDtos.StateSet(
          note.getId(), current.pinned(), !current.favorite()));
    }, this::acceptMetadata);
  }

  @Override
  public void setColor(NoteModel note, NoteColor color) {
    if (!editable(note, false)) return;
    note.setColor(color == null ? NoteColor.DEFAULT : color);
    saveDraft(note);
  }

  @Override
  public void addTag(NoteModel note, String tag) {
    if (!editable(note, true) || tag == null || tag.isBlank()) return;
    String name = tag.trim();
    enqueue(note.getId(), version -> ensureTag(name)
        .thenCompose(tagId -> client.attachTag(new TagDtos.Link(note.getId(), tagId, version))),
        this::acceptMetadata);
  }

  @Override
  public void removeTag(NoteModel note, String tag) {
    if (!editable(note, true) || tag == null) return;
    enqueue(note.getId(), version -> findTag(tag)
        .thenCompose(tagId -> client.detachTag(new TagDtos.Link(note.getId(), tagId, version))),
        this::acceptMetadata);
  }

  @Override
  public void toggleChecklistItem(NoteModel note, ChecklistItemModel item) {
    if (!editable(note, false) || item == null) return;
    item.setChecked(!item.isChecked());
    saveDraft(note);
  }

  @Override
  public void updateChecklistItemText(NoteModel note, ChecklistItemModel item, String value) {
    if (!editable(note, false) || item == null) return;
    item.setText(value);
    saveDraft(note);
  }

  @Override
  public ChecklistItemModel addChecklistItem(NoteModel note) {
    ChecklistItemModel item = new ChecklistItemModel("", false);
    if (editable(note, false)) {
      note.getChecklistItems().add(item);
      saveDraft(note);
    }
    return item;
  }

  @Override
  public void removeChecklistItem(NoteModel note, ChecklistItemModel item) {
    if (!editable(note, false) || item == null) return;
    note.getChecklistItems().remove(item);
    saveDraft(note);
  }

  @Override
  public void addShareMember(NoteModel note, String email, SharePermission permission) {
    inviteMember(note, email, permission, null);
  }

  @Override
  public void removeShareMember(NoteModel note, ShareMember member) {
    if (note == null || member == null) return;
    ShareDtos.View share = sharesByNote.getOrDefault(note.getId(), List.of()).stream()
        .filter(item -> item.recipientEmail().equalsIgnoreCase(member.getEmail()))
        .findFirst().orElse(null);
    if (share == null) return;
    client.revokeShare(share.shareId()).whenComplete((ignored, failure) -> Platform.runLater(() -> {
      if (closed) return;
      if (failure != null) report(failure);
      else loadShares(note);
    }));
  }

  public void loadShares(NoteModel note) {
    if (note == null || closed) return;
    client.listShares(note.getId()).whenComplete((result, failure) -> Platform.runLater(() -> {
      if (closed) return;
      if (failure != null) {
        report(failure);
        return;
      }
      sharesByNote.put(note.getId(), result.shares());
      List<ShareMember> members = new ArrayList<>();
      NoteDtos.View view = accepted.get(note.getId());
      if (view != null) {
        members.add(new ShareMember(view.ownerName(), getCurrentUserEmail(), SharePermission.EDIT));
      }
      for (ShareDtos.View share : result.shares()) {
        members.add(new ShareMember(share.recipientEmail(), share.recipientEmail(), share.permission()));
      }
      note.getShareMembers().setAll(members);
      serverStatus.set("");
    }));
  }

  public void inviteMember(NoteModel note, String email, SharePermission permission, Runnable onSuccess) {
    if (!editable(note, true) || email == null || email.isBlank()) return;
    client.createShare(new ShareDtos.Create(note.getId(), email.trim(), permission))
        .whenComplete((view, failure) -> Platform.runLater(() -> {
          if (closed) return;
          if (failure != null) report(failure);
          else {
            loadShares(note);
            if (onSuccess != null) onSuccess.run();
          }
        }));
  }

  public void changeSharePermission(NoteModel note, ShareMember member, SharePermission permission) {
    if (note == null || member == null || permission == null) return;
    ShareDtos.View share = sharesByNote.getOrDefault(note.getId(), List.of()).stream()
        .filter(item -> item.recipientEmail().equalsIgnoreCase(member.getEmail()))
        .findFirst().orElse(null);
    if (share == null) return;
    client.updateShare(new ShareDtos.Update(share.shareId(), permission))
        .whenComplete((view, failure) -> Platform.runLater(() -> {
          if (closed) return;
          if (failure != null) report(failure);
          else loadShares(note);
        }));
  }

  @Override
  public Set<String> collectAvailableTags() {
    Set<String> names = super.collectAvailableTags();
    names.addAll(tagIds.keySet());
    return names;
  }

  @Override
  public boolean moveVisibleNote(String draggedId, String targetId, boolean after) {
    if (readOnlyOffline || closed) return false;
    NoteModel dragged = getNotes().stream().filter(n -> n.getId().equals(draggedId)).findFirst().orElse(null);
    NoteModel target = getNotes().stream().filter(n -> n.getId().equals(targetId)).findFirst().orElse(null);
    if (dragged == null || target == null || dragged == target) return false;
    List<NoteModel> order = new ArrayList<>(getNotes());
    order.remove(dragged);
    int index = order.indexOf(target) + (after ? 1 : 0);
    order.add(Math.min(index, order.size()), dragged);
    List<NoteDtos.OrderItem> items = new ArrayList<>();
    long position = 1000;
    for (NoteModel item : order) {
      items.add(new NoteDtos.OrderItem(item.getId(), position));
      position += 1000;
    }
    client.reorder(new NoteDtos.Reorder(items)).whenComplete((ignored, failure) -> Platform.runLater(() -> {
      if (closed) return;
      if (failure != null) {
        report(failure);
        return;
      }
      getNotes().setAll(order);
      for (NoteDtos.OrderItem item : items) {
        getNotes().stream().filter(n -> n.getId().equals(item.noteId()))
            .findFirst().ifPresent(n -> n.setSortOrder(item.sortOrder()));
        accepted.computeIfPresent(item.noteId(), (id, old) -> withOrder(old, item.sortOrder()));
      }
      setSortMode(SortMode.MANUAL);
      saveAcknowledgedSnapshot();
      serverStatus.set("");
    }));
    return true;
  }

  private void saveDraft(NoteModel note) {
    String content = note.getType() == NoteType.CHECKLIST
        ? ChecklistContentCodec.encode(new ArrayList<>(note.getChecklistItems())) : note.getContent();
    String title = note.getTitle();
    String color = (note.getColor() == null ? NoteColor.DEFAULT : note.getColor()).cssName();
    enqueue(note.getId(), version -> client.update(new NoteDtos.Update(
        note.getId(), version, title, content, color)), this::acceptMetadata);
  }

  private <T> void enqueue(String noteId, Function<Long, CompletableFuture<T>> action,
                           Consumer<T> onSuccess) {
    if (closed || blockedNotes.contains(noteId)) return;
    CompletableFuture<Void> previous = writes.getOrDefault(noteId, CompletableFuture.completedFuture(null));
    CompletableFuture<Void> next = previous.handle((ignored, failure) -> null)
        .thenCompose(ignored -> {
          if (closed || blockedNotes.contains(noteId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unconfirmed note draft"));
          }
          NoteDtos.View view = accepted.get(noteId);
          if (view == null) return CompletableFuture.failedFuture(new IllegalStateException("Note unavailable"));
          return action.apply(view.version()).thenAccept(result -> {
            if (result instanceof NoteDtos.View changed) {
              accepted.put(changed.noteId(), changed);
            }
            Platform.runLater(() -> {
            if (closed) return;
            onSuccess.accept(result);
            saveAcknowledgedSnapshot();
            serverStatus.set("");
            });
          });
        }).exceptionally(failure -> {
          Throwable actual = failure instanceof CompletionException && failure.getCause() != null
              ? failure.getCause() : failure;
          if (actual instanceof IllegalStateException
              && "Unconfirmed note draft".equals(actual.getMessage())) return null;
          if (actual instanceof RemoteOperationException remote && remote.code() == ErrorCode.CONFLICT) {
            conflicts.add(noteId);
          }
          blockedNotes.add(noteId);
          lastBlockedNoteId = noteId;
          Platform.runLater(() -> { if (!closed) report(failure); });
          return null;
        });
    writes.put(noteId, next);
  }

  private CompletableFuture<Long> ensureTag(String name) {
    Long known = tagIds.get(key(name));
    if (known != null) return CompletableFuture.completedFuture(known);
    return client.createTag(name).handle((tag, failure) -> {
      if (failure == null) {
        tagIds.put(key(tag.name()), tag.tagId());
        return CompletableFuture.completedFuture(tag.tagId());
      }
      Throwable actual = failure instanceof CompletionException && failure.getCause() != null
          ? failure.getCause() : failure;
      if (actual instanceof RemoteOperationException remote && remote.code() == ErrorCode.CONFLICT) {
        return findTag(name);
      }
      return CompletableFuture.<Long>failedFuture(actual);
    }).thenCompose(Function.identity());
  }

  private CompletableFuture<Long> findTag(String name) {
    Long known = tagIds.get(key(name));
    if (known != null) return CompletableFuture.completedFuture(known);
    return client.listTags().thenApply(result -> {
      TagDtos.View tag = result.tags().stream()
          .filter(item -> key(item.name()).equals(key(name)))
          .findFirst().orElseThrow(() -> new IllegalArgumentException("Tag unavailable"));
      tagIds.put(key(tag.name()), tag.tagId());
      return tag.tagId();
    });
  }

  private void acceptMetadata(NoteDtos.View view) {
    accepted.put(view.noteId(), view);
    for (TagDtos.View tag : view.tags()) {
      tagIds.put(key(tag.name()), tag.tagId());
      if (currentTags.stream().noneMatch(existing -> existing.tagId() == tag.tagId())) {
        List<TagDtos.View> expanded = new ArrayList<>(currentTags);
        expanded.add(tag);
        currentTags = List.copyOf(expanded);
      }
    }
    NoteModel model = getNotes().stream().filter(n -> n.getId().equals(view.noteId())).findFirst().orElse(null);
    if (model == null) return;
    model.setPinned(view.pinned());
    model.setFavorite(view.favorite());
    model.setSortOrder(view.sortOrder());
    model.setUpdatedAt(epoch(view.updatedAt()));
    model.getTags().setAll(view.tags().stream().map(TagDtos.View::name).toList());
  }

  private boolean editable(NoteModel note, boolean ownerOnly) {
    if (note == null || closed) return false;
    if (readOnlyOffline) {
      serverStatus.set("Đang xem cache ngoại tuyến. Hãy kết nối máy chủ trước khi sửa.");
      return false;
    }
    NoteDtos.View view = accepted.get(note.getId());
    if (view == null) return false;
    if (view.shared() && (ownerOnly || view.permission() != SharePermission.EDIT)) {
      serverStatus.set("Bạn không có quyền sửa ghi chú này.");
      return false;
    }
    if (blockedNotes.contains(note.getId())) {
      serverStatus.set("Bản nháp chưa được xác nhận. Hãy tải bản máy chủ hoặc giữ bản nháp để xử lý.");
      return false;
    }
    return true;
  }

  private void report(Throwable failure) {
    Throwable actual = failure instanceof CompletionException && failure.getCause() != null
        ? failure.getCause() : failure;
    if (actual instanceof RemoteOperationException remote) {
      if (remote.code() == ErrorCode.CONFLICT) {
        serverStatus.set("Ghi chú đã thay đổi trên máy khác. Bản nháp chưa được lưu; hãy tải lại để xem bản mới.");
        return;
      }
      if (remote.code() == ErrorCode.FORBIDDEN) {
        serverStatus.set("Bạn không có quyền thực hiện thao tác này.");
        return;
      }
      if (remote.code() == ErrorCode.UNAUTHENTICATED) {
        serverStatus.set("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
        return;
      }
      serverStatus.set("Máy chủ từ chối thao tác: " + remote.code());
    } else if (actual instanceof TransportException) {
      serverStatus.set("Mất kết nối máy chủ. Bản nháp chưa được lưu.");
    } else {
      serverStatus.set("Không thể lưu ghi chú. Bản nháp chưa được lưu.");
    }
  }

  private static NoteModel toModel(NoteDtos.View view) {
    NoteType type = view.noteType() == null ? NoteType.TEXT : view.noteType();
    NoteColor color = java.util.Arrays.stream(NoteColor.values())
        .filter(value -> value.cssName().equals(view.color())).findFirst().orElse(NoteColor.DEFAULT);
    NoteModel model = new NoteModel(view.noteId(), type, view.title(),
        type == NoteType.CHECKLIST ? ChecklistContentCodec.toPlainText(view.content()) : view.content(),
        color, view.pinned(), epoch(view.createdAt()), epoch(view.updatedAt()), view.sortOrder());
    if (type == NoteType.CHECKLIST) {
      model.getChecklistItems().setAll(ChecklistContentCodec.decode(view.content()));
    }
    model.setOwnerId(Long.toString(view.ownerId()));
    model.setOwnerName(view.ownerName());
    model.setShared(view.shared());
    model.setFavorite(view.favorite());
    model.setSharePermission(view.permission());
    model.setSharedById(view.shared() ? Long.toString(view.ownerId()) : "");
    model.setSharedByName(view.shared() ? view.ownerName() : "");
    model.getTags().setAll(view.tags().stream().map(TagDtos.View::name).toList());
    return model;
  }

  private static NoteDtos.View withOrder(NoteDtos.View old, long order) {
    return new NoteDtos.View(old.noteId(), old.ownerId(), old.ownerName(), old.noteType(),
        old.title(), old.content(), old.color(), old.version(), old.pinned(), old.favorite(),
        order, old.shared(), old.permission(), old.tags(), old.createdAt(), old.updatedAt());
  }

  private static long epoch(String value) {
    try { return Instant.parse(value).toEpochMilli(); }
    catch (RuntimeException ignored) { return 0L; }
  }

  private static String key(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  @Override
  public void close() {
    closed = true;
    getNotes().clear();
    accepted.clear();
    sharesByNote.clear();
    writes.clear();
    blockedNotes.clear();
    pendingCreate = null;
    lastBlockedNoteId = null;
  }
}
