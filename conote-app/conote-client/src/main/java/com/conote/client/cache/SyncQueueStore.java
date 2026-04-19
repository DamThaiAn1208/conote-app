package com.conote.client.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SyncQueueStore {
  private static final TypeReference<List<PendingSyncItem>> SYNC_ITEM_LIST_TYPE = new TypeReference<>() {
  };

  private final JsonStore jsonStore;

  public SyncQueueStore() {
    this(new JsonStore());
  }

  public SyncQueueStore(JsonStore jsonStore) {
    ClientStoragePaths.init();
    this.jsonStore = jsonStore;
  }

  public List<PendingSyncItem> findAll() {
    List<PendingSyncItem> items =
        jsonStore.read(ClientStoragePaths.syncQueueFile(), SYNC_ITEM_LIST_TYPE, ArrayList::new);
    List<PendingSyncItem> sanitized = new ArrayList<>();
    for (PendingSyncItem item : items) {
      if (item != null) {
        sanitized.add(sanitize(item));
      }
    }
    return sanitized;
  }

  public void add(PendingSyncItem item) {
    PendingSyncItem prepared = sanitize(item);
    List<PendingSyncItem> items = new ArrayList<>(findAll());

    if (prepared.getActionType() == SyncActionType.DELETE_NOTE) {
      boolean hadPendingCreate = items.removeIf(existing ->
          isSameEntity(existing, prepared) && existing.getActionType() == SyncActionType.CREATE_NOTE);
      if (hadPendingCreate) {
        items.removeIf(existing -> isSameEntity(existing, prepared));
        writeAll(items);
        return;
      }

      items.removeIf(existing ->
          isSameEntity(existing, prepared) && existing.getActionType() == SyncActionType.DELETE_NOTE);
      items.add(prepared);
      writeAll(items);
      return;
    }

    for (PendingSyncItem existing : items) {
      if (isSameEntity(existing, prepared) && existing.getActionType() == SyncActionType.CREATE_NOTE) {
        existing.setPayloadJson(prepared.getPayloadJson());
        existing.setLastError(null);
        writeAll(items);
        return;
      }
    }

    if (prepared.getActionType() == SyncActionType.PIN_NOTE
        || prepared.getActionType() == SyncActionType.UNPIN_NOTE) {
      items.removeIf(existing ->
          isSameEntity(existing, prepared)
              && (existing.getActionType() == SyncActionType.PIN_NOTE
              || existing.getActionType() == SyncActionType.UNPIN_NOTE));
    } else {
      items.removeIf(existing ->
          isSameEntity(existing, prepared) && existing.getActionType() == prepared.getActionType());
    }

    items.add(prepared);
    writeAll(items);
  }

  public void removeById(String queueId) {
    if (queueId == null || queueId.isBlank()) {
      return;
    }

    List<PendingSyncItem> items = new ArrayList<>(findAll());
    items.removeIf(item -> queueId.equals(item.getQueueId()));
    writeAll(items);
  }

  public void clear() {
    writeAll(List.of());
  }

  private void writeAll(List<PendingSyncItem> items) {
    jsonStore.write(ClientStoragePaths.syncQueueFile(), items == null ? List.of() : items);
  }

  private PendingSyncItem sanitize(PendingSyncItem item) {
    PendingSyncItem target = item == null ? new PendingSyncItem() : item;
    if (target.getQueueId() == null || target.getQueueId().isBlank()) {
      target.setQueueId(UUID.randomUUID().toString());
    }
    if (target.getCreatedAt() == null) {
      target.setCreatedAt(LocalDateTime.now());
    }
    if (target.getRetryCount() < 0) {
      target.setRetryCount(0);
    }
    if (target.getEntityId() != null && target.getEntityId().isBlank()) {
      target.setEntityId(null);
    }
    return target;
  }

  private boolean isSameEntity(PendingSyncItem left, PendingSyncItem right) {
    return left != null
        && right != null
        && Objects.equals(left.getEntityId(), right.getEntityId());
  }
}
