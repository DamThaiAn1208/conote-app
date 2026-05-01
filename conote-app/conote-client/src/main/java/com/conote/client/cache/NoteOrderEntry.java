package com.conote.client.cache;

import java.time.LocalDateTime;

public class NoteOrderEntry {
  private String userKey;
  private String noteId;
  private long sortOrder;
  private LocalDateTime updatedAt;

  public NoteOrderEntry() {
  }

  public NoteOrderEntry(String userKey, String noteId, long sortOrder, LocalDateTime updatedAt) {
    this.userKey = userKey;
    this.noteId = noteId;
    this.sortOrder = sortOrder;
    this.updatedAt = updatedAt;
  }

  public String getUserKey() {
    return userKey;
  }

  public void setUserKey(String userKey) {
    this.userKey = userKey;
  }

  public String getNoteId() {
    return noteId;
  }

  public void setNoteId(String noteId) {
    this.noteId = noteId;
  }

  public long getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(long sortOrder) {
    this.sortOrder = sortOrder;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
