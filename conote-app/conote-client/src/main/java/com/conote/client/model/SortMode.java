package com.conote.client.model;

public enum SortMode {
  MANUAL("Custom order"),
  NEWEST("Newest first"),
  OLDEST("Oldest first");

  private final String label;

  SortMode(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
