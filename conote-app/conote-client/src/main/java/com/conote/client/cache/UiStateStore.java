package com.conote.client.cache;

public class UiStateStore {
  private final JsonStore jsonStore;

  public UiStateStore() {
    this(new JsonStore());
  }

  public UiStateStore(JsonStore jsonStore) {
    ClientStoragePaths.init();
    this.jsonStore = jsonStore;
  }

  public UiState load() {
    return jsonStore.read(ClientStoragePaths.uiStateFile(), UiState.class, UiState::new);
  }

  public UiState save(UiState uiState) {
    UiState state = uiState == null ? new UiState() : uiState;
    jsonStore.write(ClientStoragePaths.uiStateFile(), state);
    return state;
  }
}
