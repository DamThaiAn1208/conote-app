package com.conote.client.controller;

import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class SearchBarController {
  @FXML
  private TextField searchField;

  @FXML
  private Button filterButton;

  private Runnable toggleFiltersAction;
  private boolean syncingQuery;

  @FXML
  private void initialize() {
    MotionSupport.installButtonMotion(filterButton);
  }

  public void setContext(CoNoteStore store, Runnable toggleFiltersAction) {
    this.toggleFiltersAction = toggleFiltersAction;
    searchField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (syncingQuery) {
        return;
      }
      syncingQuery = true;
      store.setSearchQuery(newValue);
      syncingQuery = false;
    });
    store.searchQueryProperty().addListener((obs, oldValue, newValue) -> {
      if (syncingQuery) {
        return;
      }
      String value = newValue == null ? "" : newValue;
      if (!searchField.getText().equals(value)) {
        syncingQuery = true;
        searchField.setText(value);
        syncingQuery = false;
      }
    });
    if (!searchField.getText().equals(store.searchQueryProperty().get())) {
      searchField.setText(store.searchQueryProperty().get());
    }
  }

  @FXML
  private void toggleFilters() {
    if (toggleFiltersAction != null) {
      toggleFiltersAction.run();
    }
  }
}
