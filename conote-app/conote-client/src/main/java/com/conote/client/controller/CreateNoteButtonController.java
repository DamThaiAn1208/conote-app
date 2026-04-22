package com.conote.client.controller;

import com.conote.common.enums.NoteType;
import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class CreateNoteButtonController {
  private static final Duration MENU_ANIMATION = Duration.millis(175);
  private static final double MENU_CORNER_RADIUS = 10.0;

  @FXML
  private VBox menu;

  @FXML
  private HBox primaryButton;

  @FXML
  private HBox textNoteRow;

  @FXML
  private HBox checklistNoteRow;

  @FXML
  private Button textNoteButton;

  @FXML
  private Button checklistNoteButton;

  private final Rectangle menuClip = new Rectangle();
  private CoNoteStore store;
  private Consumer<NoteModel> onNoteCreated;
  private Timeline menuTimeline;
  private boolean menuShown;

  @FXML
  private void initialize() {
    MotionSupport.installGentleButtonMotion(primaryButton);
    textNoteButton.setMouseTransparent(true);
    checklistNoteButton.setMouseTransparent(true);
    textNoteButton.setFocusTraversable(false);
    checklistNoteButton.setFocusTraversable(false);
    textNoteRow.setPickOnBounds(true);
    checklistNoteRow.setPickOnBounds(true);
    textNoteRow.setOnMouseClicked(event -> createTextNote());
    checklistNoteRow.setOnMouseClicked(event -> createChecklistNote());
    menuClip.widthProperty().bind(menu.widthProperty());
    menuClip.heightProperty().bind(menu.maxHeightProperty());
    menuClip.setArcWidth(MENU_CORNER_RADIUS * 2.0);
    menuClip.setArcHeight(MENU_CORNER_RADIUS * 2.0);
    menu.setClip(menuClip);
    applyMenuState(false, false);
  }

  public void setContext(CoNoteStore store, Consumer<NoteModel> onNoteCreated) {
    this.store = store;
    this.onNoteCreated = onNoteCreated;
    menu.prefWidthProperty().bind(primaryButton.widthProperty());
    menu.minWidthProperty().bind(primaryButton.widthProperty());
    menu.maxWidthProperty().bind(primaryButton.widthProperty());
  }

  @FXML
  private void toggleMenu() {
    setMenuVisible(!menu.isVisible());
  }

  @FXML
  private void createTextNote() {
    NoteModel note = store.createNote(NoteType.TEXT);
    setMenuVisible(false);
    openCreatedNote(note);
  }

  @FXML
  private void createChecklistNote() {
    NoteModel note = store.createNote(NoteType.CHECKLIST);
    setMenuVisible(false);
    openCreatedNote(note);
  }

  private void setMenuVisible(boolean visible) {
    applyMenuState(visible, true);
  }

  private void openCreatedNote(NoteModel note) {
    if (note != null && onNoteCreated != null) {
      onNoteCreated.accept(note);
    }
  }

  private void applyMenuState(boolean visible, boolean animate) {
    if (menuShown == visible && menu.isVisible() == visible) {
      return;
    }

    menuShown = visible;
    if (menuTimeline != null) {
      menuTimeline.stop();
      menuTimeline = null;
    }

    if (!animate) {
      menu.setVisible(visible);
      menu.setManaged(visible);
      menu.setMouseTransparent(!visible);
      menu.setOpacity(visible ? 1.0 : 0.0);
      menu.setTranslateY(visible ? 0.0 : -8.0);
      menu.setMaxHeight(visible ? measureMenuHeight() : 0.0);
      return;
    }

    double currentHeight = Math.max(menu.getMaxHeight(), 0.0);
    if (visible) {
      menu.setManaged(true);
      menu.setVisible(true);
      menu.setMouseTransparent(false);
      double targetHeight = measureMenuHeight();
      menu.setOpacity(0.0);
      menu.setTranslateY(-8.0);
      menu.setMaxHeight(currentHeight > 0.0 ? currentHeight : 0.0);
      menuTimeline = new Timeline(
          new KeyFrame(
              MENU_ANIMATION,
              new KeyValue(menu.maxHeightProperty(), targetHeight, Interpolator.EASE_BOTH),
              new KeyValue(menu.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
              new KeyValue(menu.translateYProperty(), 0.0, Interpolator.EASE_BOTH)));
      menuTimeline.setOnFinished(event -> {
        menu.setMaxHeight(measureMenuHeight());
        menuTimeline = null;
      });
      menuTimeline.playFromStart();
      return;
    }

    double startHeight = currentHeight > 0.0 ? currentHeight : measureMenuHeight();
    menu.setMouseTransparent(true);
    menu.setMaxHeight(startHeight);
    menuTimeline = new Timeline(
        new KeyFrame(
            MENU_ANIMATION,
            new KeyValue(menu.maxHeightProperty(), 0.0, Interpolator.EASE_BOTH),
            new KeyValue(menu.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
            new KeyValue(menu.translateYProperty(), -8.0, Interpolator.EASE_BOTH)));
    menuTimeline.setOnFinished(event -> {
      menu.setVisible(false);
      menu.setManaged(false);
      menuTimeline = null;
    });
    menuTimeline.playFromStart();
  }

  private double measureMenuHeight() {
    menu.applyCss();
    menu.layout();
    double width = primaryButton.getWidth() > 0 ? primaryButton.getWidth() : -1;
    double targetHeight = menu.prefHeight(width);
    if (Double.isNaN(targetHeight) || targetHeight <= 0.0) {
      targetHeight = menu.prefHeight(-1);
    }
    return Math.max(targetHeight, 0.0);
  }
}
