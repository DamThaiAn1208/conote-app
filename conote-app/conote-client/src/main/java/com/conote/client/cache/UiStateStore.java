package com.conote.client.cache;

import java.nio.file.Path;

public class UiStateStore {
  private final JsonStore jsonStore;
  private final Path filePath;

  public UiStateStore() {
    this(new JsonStore());
  }

  public UiStateStore(JsonStore jsonStore) {
    this(jsonStore, ClientStoragePaths.uiStateFile());
  }

  public UiStateStore(JsonStore jsonStore, Path filePath) {
    ClientStoragePaths.init();
    this.jsonStore = jsonStore;
    this.filePath = java.util.Objects.requireNonNull(filePath, "filePath");
  }

  public UiState load() {
    return jsonStore.read(filePath, UiState.class, UiState::new);
  }

  public UiState save(UiState uiState) {
    UiState state = uiState == null ? new UiState() : uiState;
    jsonStore.write(filePath, state);
    return state;
  }
}
