package com.conote.client.controller;

import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.client.model.SortMode;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import java.util.Comparator;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class FilterPanelController {
  @FXML
  private VBox root;

  @FXML
  private ComboBox<SortMode> sortCombo;

  @FXML
  private FlowPane colorFlow;

  @FXML
  private FlowPane tagFlow;

  private CoNoteStore store;

  @FXML
  private void initialize() {
    sortCombo.getItems().setAll(SortMode.values());
    setExpanded(false);
  }

  public void setContext(CoNoteStore store) {
    this.store = store;
    sortCombo.valueProperty().bindBidirectional(store.sortModeProperty());
    sortCombo.setValue(store.sortModeProperty().get());
    store.getNotes().addListener((ListChangeListener<NoteModel>) change -> refreshTagChips());
    refreshColorSwatches();
    refreshTagChips();
  }

  public void setExpanded(boolean expanded) {
    root.setVisible(expanded);
    root.setManaged(expanded);
  }

  public boolean isExpanded() {
    return root.isVisible();
  }

  private void refreshColorSwatches() {
    colorFlow.getChildren().clear();
    for (NoteColor color : NoteColor.values()) {
      Button swatch = new Button();
      swatch.getStyleClass().addAll("color-filter-swatch", "swatch-" + color.cssName());
      if (store.getSelectedColors().contains(color)) {
        swatch.getStyleClass().add("swatch-selected");
      }
      MotionSupport.installButtonMotion(swatch);
      swatch.setOnAction(event -> {
        store.toggleColorFilter(color);
        refreshColorSwatches();
      });
      colorFlow.getChildren().add(swatch);
    }
  }

  private void refreshTagChips() {
    tagFlow.getChildren().clear();
    store.collectAvailableTags().stream()
        .sorted(Comparator.naturalOrder())
        .forEach(tag -> {
          Button chip = new Button("#" + tag);
          chip.getStyleClass().add("filter-chip");
          if (store.getSelectedTags().contains(tag)) {
            chip.getStyleClass().add("filter-chip-active");
          }
          MotionSupport.installButtonMotion(chip);
          chip.setOnAction(event -> {
            store.toggleTagFilter(tag);
            refreshTagChips();
          });
          tagFlow.getChildren().add(chip);
        });
  }
}
