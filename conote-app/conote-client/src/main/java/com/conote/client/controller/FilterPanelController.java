package com.conote.client.controller;

import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.client.model.SortMode;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import java.util.Comparator;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class FilterPanelController {
  private static final Duration PANEL_ANIMATION = Duration.millis(185);

  @FXML
  private VBox root;

  @FXML
  private ComboBox<SortMode> sortCombo;

  @FXML
  private Button clearButton;

  @FXML
  private FlowPane colorFlow;

  @FXML
  private FlowPane tagFlow;

  private final Rectangle panelClip = new Rectangle();
  private CoNoteStore store;
  private Timeline panelTimeline;
  private boolean expanded;
  private Runnable onClearedAction;

  @FXML
  private void initialize() {
    sortCombo.getItems().setAll(SortMode.values());
    MotionSupport.installGentleButtonMotion(clearButton);
    clearButton.setFocusTraversable(false);
    panelClip.widthProperty().bind(root.widthProperty());
    panelClip.heightProperty().bind(root.maxHeightProperty());
    root.setClip(panelClip);
    applyExpandedState(false, false);
  }

  public void setContext(CoNoteStore store, Runnable onClearedAction) {
    this.store = store;
    this.onClearedAction = onClearedAction;
    sortCombo.valueProperty().bindBidirectional(store.sortModeProperty());
    sortCombo.setValue(store.sortModeProperty().get());
    store.getNotes().addListener((ListChangeListener<NoteModel>) change -> refreshTagChips());
    refreshColorSwatches();
    refreshTagChips();
  }

  public void setExpanded(boolean expanded) {
    applyExpandedState(expanded, true);
  }

  public boolean isExpanded() {
    return expanded;
  }

  public boolean containsNode(Node node) {
    Node current = node;
    while (current != null) {
      if (current == root) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  @FXML
  private void clearFilters() {
    if (store == null) {
      return;
    }

    store.clearAllFilters();
    refreshColorSwatches();
    refreshTagChips();
    setExpanded(false);
    if (onClearedAction != null) {
      onClearedAction.run();
    }
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
    syncExpandedHeight();
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
    syncExpandedHeight();
  }

  private void applyExpandedState(boolean expanded, boolean animate) {
    if (this.expanded == expanded && root.isVisible() == expanded) {
      return;
    }

    this.expanded = expanded;
    if (panelTimeline != null) {
      panelTimeline.stop();
      panelTimeline = null;
    }

    if (!animate) {
      root.setVisible(expanded);
      root.setManaged(expanded);
      root.setMouseTransparent(!expanded);
      root.setOpacity(expanded ? 1.0 : 0.0);
      root.setTranslateY(expanded ? 0.0 : -8.0);
      root.setMaxHeight(expanded ? measureExpandedHeight() : 0.0);
      return;
    }

    double currentHeight = Math.max(root.getMaxHeight(), 0.0);
    if (expanded) {
      root.setManaged(true);
      root.setVisible(true);
      root.setMouseTransparent(false);
      double targetHeight = measureExpandedHeight();
      root.setOpacity(0.0);
      root.setTranslateY(-8.0);
      root.setMaxHeight(currentHeight > 0.0 ? currentHeight : 0.0);
      panelTimeline = new Timeline(
          new KeyFrame(
              PANEL_ANIMATION,
              new KeyValue(root.maxHeightProperty(), targetHeight, Interpolator.EASE_BOTH),
              new KeyValue(root.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
              new KeyValue(root.translateYProperty(), 0.0, Interpolator.EASE_BOTH)));
      panelTimeline.setOnFinished(event -> {
        root.setMaxHeight(measureExpandedHeight());
        panelTimeline = null;
      });
      panelTimeline.playFromStart();
      return;
    }

    double startHeight = currentHeight > 0.0 ? currentHeight : measureExpandedHeight();
    root.setMouseTransparent(true);
    root.setMaxHeight(startHeight);
    panelTimeline = new Timeline(
        new KeyFrame(
            PANEL_ANIMATION,
            new KeyValue(root.maxHeightProperty(), 0.0, Interpolator.EASE_BOTH),
            new KeyValue(root.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
            new KeyValue(root.translateYProperty(), -8.0, Interpolator.EASE_BOTH)));
    panelTimeline.setOnFinished(event -> {
      root.setVisible(false);
      root.setManaged(false);
      panelTimeline = null;
    });
    panelTimeline.playFromStart();
  }

  private void syncExpandedHeight() {
    if (!expanded || root == null || !root.isVisible()) {
      return;
    }
    root.setMaxHeight(measureExpandedHeight());
  }

  private double measureExpandedHeight() {
    root.applyCss();
    root.layout();
    double width = root.getWidth() > 0 ? root.getWidth() : -1;
    double targetHeight = root.prefHeight(width);
    if (Double.isNaN(targetHeight) || targetHeight <= 0.0) {
      targetHeight = root.prefHeight(-1);
    }
    return Math.max(targetHeight, 0.0);
  }
}
