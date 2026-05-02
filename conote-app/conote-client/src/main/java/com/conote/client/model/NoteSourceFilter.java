package com.conote.client.model;

public enum NoteSourceFilter {
  ALL("Tất cả"),
  MINE("Của Tôi"),
  SHARED("Được Chia Sẻ");

  private final String label;

  NoteSourceFilter(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
