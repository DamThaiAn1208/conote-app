package com.conote.client.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoteOrderCacheStore {
  private static final TypeReference<List<NoteOrderEntry>> NOTE_ORDER_LIST_TYPE =
      new TypeReference<>() {
      };

  private final JsonStore jsonStore;

  public NoteOrderCacheStore() {
    this(new JsonStore());
  }

  public NoteOrderCacheStore(JsonStore jsonStore) {
    ClientStoragePaths.init();
    this.jsonStore = jsonStore;
  }

  public List<NoteOrderEntry> findAll() {
    List<NoteOrderEntry> entries =
        jsonStore.read(ClientStoragePaths.noteOrderCacheFile(), NOTE_ORDER_LIST_TYPE, ArrayList::new);
    List<NoteOrderEntry> sanitized = new ArrayList<>();
    for (NoteOrderEntry entry : entries) {
      NoteOrderEntry clean = sanitize(entry);
      if (clean != null) {
        sanitized.add(clean);
      }
    }
    return sanitized;
  }

  public Map<String, Long> findOrderByNoteId(String userKey) {
    Map<String, Long> orderByNoteId = new LinkedHashMap<>();
    for (NoteOrderEntry entry : findByUser(userKey)) {
      orderByNoteId.put(entry.getNoteId(), entry.getSortOrder());
    }
    return orderByNoteId;
  }

  public List<NoteOrderEntry> findByUser(String userKey) {
    String normalizedUserKey = normalizeUserKey(userKey);
    return findAll().stream()
        .filter(entry -> normalizedUserKey.equals(normalizeUserKey(entry.getUserKey())))
        .sorted(Comparator.comparingLong(NoteOrderEntry::getSortOrder)
            .thenComparing(NoteOrderEntry::getNoteId))
        .toList();
  }

  public List<NoteOrderEntry> saveForUser(String userKey, Map<String, Long> orderByNoteId) {
    String normalizedUserKey = normalizeUserKey(userKey);
    List<NoteOrderEntry> nextEntries = new ArrayList<>();
    for (NoteOrderEntry entry : findAll()) {
      if (!normalizedUserKey.equals(normalizeUserKey(entry.getUserKey()))) {
        nextEntries.add(entry);
      }
    }

    LocalDateTime now = LocalDateTime.now();
    if (orderByNoteId != null) {
      orderByNoteId.entrySet().stream()
          .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
          .sorted(Comparator.comparingLong(entry -> entry.getValue() == null ? 0L : entry.getValue()))
          .forEach(entry -> nextEntries.add(new NoteOrderEntry(
              normalizedUserKey,
              entry.getKey(),
              entry.getValue() == null ? 0L : entry.getValue(),
              now)));
    }

    jsonStore.write(ClientStoragePaths.noteOrderCacheFile(), nextEntries);
    return findByUser(normalizedUserKey);
  }

  public void deleteForNote(String userKey, String noteId) {
    if (noteId == null || noteId.isBlank()) {
      return;
    }

    String normalizedUserKey = normalizeUserKey(userKey);
    List<NoteOrderEntry> entries = new ArrayList<>(findAll());
    boolean removed = entries.removeIf(entry ->
        normalizedUserKey.equals(normalizeUserKey(entry.getUserKey())) && noteId.equals(entry.getNoteId()));
    if (removed) {
      jsonStore.write(ClientStoragePaths.noteOrderCacheFile(), entries);
    }
  }

  private NoteOrderEntry sanitize(NoteOrderEntry entry) {
    if (entry == null || entry.getNoteId() == null || entry.getNoteId().isBlank()) {
      return null;
    }
    entry.setUserKey(normalizeUserKey(entry.getUserKey()));
    if (entry.getUpdatedAt() == null) {
      entry.setUpdatedAt(LocalDateTime.now());
    }
    return entry;
  }

  private String normalizeUserKey(String userKey) {
    return userKey == null || userKey.isBlank() ? "local@conote.app" : userKey.trim().toLowerCase();
  }
}
