package com.conote.client.controller;

import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

public class SearchBarController {
  private static final String FILTER_ACTIVE_CLASS = "filter-toggle-button-active";
  private static final String SEARCH_FOCUS_CLASS = "search-shell-focused";
  private static final String DEFAULT_FILTER_ICON = "codicon-filter:17";
  private static final String ACTIVE_FILTER_ICON = "codicon-list-filter:17";

  @FXML
  private HBox searchShell;

  @FXML
  private TextField searchField;

  @FXML
  private Button filterButton;

  @FXML
  private FontIcon filterIcon;

  private Runnable toggleFiltersAction;
  private boolean filterActive;
  private boolean syncingQuery;

  @FXML
  private void initialize() {
    MotionSupport.installButtonMotion(filterButton);
    searchField.focusedProperty().addListener((obs, oldValue, newValue) -> applySearchFocusState(newValue));
    applySearchFocusState(searchField.isFocused());
    applyFilterButtonState();
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

  public void setFilterActive(boolean active) {
    if (filterActive == active) {
      return;
    }
    filterActive = active;
    applyFilterButtonState();
  }

  public boolean isInsideSearchArea(Node node) {
    return isInside(node, searchShell);
  }

  public boolean isFilterButtonTarget(Node node) {
    return isInside(node, filterButton);
  }

  public boolean isSearchFocused() {
    return searchField != null && searchField.isFocused();
  }

  private void applyFilterButtonState() {
    filterButton.getStyleClass().remove(FILTER_ACTIVE_CLASS);
    if (filterActive) {
      filterButton.getStyleClass().add(FILTER_ACTIVE_CLASS);
    }
    if (filterIcon != null) {
      filterIcon.setIconLiteral(filterActive ? ACTIVE_FILTER_ICON : DEFAULT_FILTER_ICON);
    }
  }

  private void applySearchFocusState(boolean focused) {
    searchShell.getStyleClass().remove(SEARCH_FOCUS_CLASS);
    if (focused) {
      searchShell.getStyleClass().add(SEARCH_FOCUS_CLASS);
    }
  }

  private boolean isInside(Node node, Node container) {
    Node current = node;
    while (current != null) {
      if (current == container) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }
}
