package com.conote.client.cache;

import com.conote.common.enums.NoteType;
import com.conote.common.model.Note;
import com.conote.common.model.Share;
import com.conote.common.model.Tag;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class NoteCacheStore {
  private static final TypeReference<List<Note>> NOTE_LIST_TYPE = new TypeReference<>() {
  };

  private final JsonStore jsonStore;

  public NoteCacheStore() {
    this(new JsonStore());
  }

  public NoteCacheStore(JsonStore jsonStore) {
    ClientStoragePaths.init();
    this.jsonStore = jsonStore;
  }

  public List<Note> findAll() {
    List<Note> notes = jsonStore.read(ClientStoragePaths.noteCacheFile(), NOTE_LIST_TYPE, ArrayList::new);
    List<Note> sanitized = new ArrayList<>();
    for (Note note : notes) {
      if (note != null) {
        sanitized.add(sanitizeLoadedNote(note));
      }
    }
    return sanitized;
  }

  public Optional<Note> findById(String noteId) {
    if (noteId == null || noteId.isBlank()) {
      return Optional.empty();
    }

    return findAll().stream()
        .filter(note -> noteId.equals(note.getNoteId()))
        .findFirst();
  }

  public Note save(Note note) {
    List<Note> notes = findAll();
    Map<String, Integer> indexById = new LinkedHashMap<>();
    for (int index = 0; index < notes.size(); index++) {
      Note current = notes.get(index);
      if (current.getNoteId() != null) {
        indexById.put(current.getNoteId(), index);
      }
    }

    Note existing = note == null || note.getNoteId() == null ? null : findExisting(notes, note.getNoteId());
    Note prepared = prepareForSave(note, existing);
    Integer existingIndex = prepared.getNoteId() == null ? null : indexById.get(prepared.getNoteId());
    if (existingIndex == null) {
      notes.add(prepared);
    } else {
      notes.set(existingIndex, prepared);
    }

    writeNotes(notes);
    return prepared;
  }

  public List<Note> saveAll(List<Note> notes) {
    List<Note> existingNotes = findAll();
    Map<String, Note> existingById = new LinkedHashMap<>();
    for (Note existing : existingNotes) {
      if (existing.getNoteId() != null) {
        existingById.put(existing.getNoteId(), existing);
      }
    }

    List<Note> prepared = new ArrayList<>();
    if (notes != null) {
      for (Note note : notes) {
        if (note == null) {
          continue;
        }
        prepared.add(prepareForSave(note, existingById.get(note.getNoteId())));
      }
    }

    writeNotes(prepared);
    return prepared;
  }

  public boolean deleteById(String noteId) {
    if (noteId == null || noteId.isBlank()) {
      return false;
    }

    List<Note> notes = findAll();
    boolean removed = notes.removeIf(note -> noteId.equals(note.getNoteId()));
    if (removed) {
      writeNotes(notes);
    }
    return removed;
  }

  private Note findExisting(List<Note> notes, String noteId) {
    for (Note note : notes) {
      if (noteId.equals(note.getNoteId())) {
        return note;
      }
    }
    return null;
  }

  private void writeNotes(List<Note> notes) {
    jsonStore.write(ClientStoragePaths.noteCacheFile(), notes == null ? List.of() : notes);
  }

  private Note sanitizeLoadedNote(Note note) {
    if (note.getPinned() == null) {
      note.setPinned(false);
    }
    if (note.getDeleted() == null) {
      note.setDeleted(false);
    }
    if (note.getNoteType() == null) {
      note.setNoteType(NoteType.TEXT);
    }
    if (note.getShares() == null) {
      note.setShares(new ArrayList<>());
    }
    if (note.getTags() == null) {
      note.setTags(new ArrayList<>());
    }
    return note;
  }

  private Note prepareForSave(Note note, Note existing) {
    Note target = note == null ? new Note() : note;
    LocalDateTime now = LocalDateTime.now();

    if (target.getNoteId() == null || target.getNoteId().isBlank()) {
      target.setNoteId(UUID.randomUUID().toString());
    }
    if (target.getCreatedAt() == null) {
      target.setCreatedAt(existing != null && existing.getCreatedAt() != null ? existing.getCreatedAt() : now);
    }
    target.setUpdatedAt(now);
    if (target.getPinned() == null) {
      target.setPinned(existing != null && existing.getPinned() != null ? existing.getPinned() : false);
    }
    if (target.getDeleted() == null) {
      target.setDeleted(existing != null && existing.getDeleted() != null ? existing.getDeleted() : false);
    }
    if (target.getNoteType() == null) {
      target.setNoteType(existing != null && existing.getNoteType() != null ? existing.getNoteType() : NoteType.TEXT);
    }
    if (target.getOwner() == null && existing != null) {
      target.setOwner(existing.getOwner());
    }
    if (target.getShareStatus() == null && existing != null) {
      target.setShareStatus(existing.getShareStatus());
    }
    if (target.getShares() == null) {
      target.setShares(existing != null && existing.getShares() != null
          ? new ArrayList<>(existing.getShares())
          : new ArrayList<>());
    }
    if (target.getTags() == null) {
      target.setTags(existing != null && existing.getTags() != null
          ? new ArrayList<>(existing.getTags())
          : new ArrayList<>());
    }

    for (Share share : target.getShares()) {
      if (share != null) {
        share.setNote(target);
      }
    }
    for (Tag tag : target.getTags()) {
      if (tag != null && tag.getNotes() == null) {
        tag.setNotes(new ArrayList<>());
      }
    }

    return sanitizeLoadedNote(target);
  }
}
