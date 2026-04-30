package com.conote.client.controller;

import com.conote.client.app.ClientApplication;
import com.conote.client.model.AppTheme;
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
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

public class CreateNoteButtonController {
  private static final Duration MENU_ANIMATION = Duration.millis(175);
  private static final double POPUP_GAP = 0.0;
  private static final double POPUP_X_OFFSET = -2.0;
  private static final double POPUP_Y_OFFSET = -24.0;

  @FXML
  private VBox root;

  @FXML
  private VBox menu;

  @FXML
  private HBox primaryButton;

  @FXML
  private HBox textNoteRow;

  @FXML
  private HBox checklistNoteRow;

  @FXML
  private StackPane textNoteButton;

  @FXML
  private StackPane checklistNoteButton;

  private CoNoteStore store;
  private Consumer<NoteModel> onNoteCreated;
  private Timeline menuTimeline;
  private Popup menuPopup;

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
    root.getChildren().remove(menu);
    menu.setVisible(false);
    menu.setManaged(false);
    menu.setLayoutX(0.0);
    menu.setLayoutY(0.0);
    menu.getStylesheets().add(ClientApplication.stylesheetUrl());
    initializePopup();
  }

  public void setContext(CoNoteStore store, Consumer<NoteModel> onNoteCreated) {
    this.store = store;
    this.onNoteCreated = onNoteCreated;
    menu.prefWidthProperty().bind(primaryButton.widthProperty());
    menu.minWidthProperty().bind(primaryButton.widthProperty());
    menu.maxWidthProperty().bind(primaryButton.widthProperty());
    store.themeProperty().addListener((obs, oldValue, newValue) -> applyTheme(newValue));
    applyTheme(store.getTheme());
  }

  @FXML
  private void toggleMenu() {
    setMenuVisible(!isMenuShowing());
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
    if (visible) {
      showMenu();
    } else {
      hideMenu();
    }
  }

  private void openCreatedNote(NoteModel note) {
    if (note != null && onNoteCreated != null) {
      onNoteCreated.accept(note);
    }
  }

  private void initializePopup() {
    menuPopup = new Popup();
    menuPopup.setAutoFix(true);
    menuPopup.setAutoHide(true);
    menuPopup.setHideOnEscape(true);
    menuPopup.getContent().setAll(menu);
    menuPopup.setOnHidden(event -> {
      stopMenuAnimation();
      menu.setVisible(false);
      menu.setManaged(false);
    });
  }

  private void showMenu() {
    Stage stage = stageFor(primaryButton);
    if (stage == null || menuPopup == null) {
      return;
    }

    stopMenuAnimation();
    menu.setVisible(true);
    menu.setManaged(true);
    menu.setMouseTransparent(false);
    menu.relocate(0.0, 0.0);
    menu.setMaxHeight(Region.USE_COMPUTED_SIZE);
    menu.applyCss();
    menu.layout();

    double popupX = computePopupX();
    double popupY = computePopupY();
    if (menuPopup.isShowing()) {
      menuPopup.setX(popupX);
      menuPopup.setY(popupY);
      return;
    }

    menu.setOpacity(0.0);
    menu.setTranslateY(-4.0);
    menuPopup.show(stage, popupX, popupY);
    menuTimeline = new Timeline(
        new KeyFrame(
            MENU_ANIMATION,
            new KeyValue(menu.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
            new KeyValue(menu.translateYProperty(), 0.0, Interpolator.EASE_BOTH)));
    menuTimeline.setOnFinished(event -> menuTimeline = null);
    menuTimeline.playFromStart();
  }

  private void hideMenu() {
    stopMenuAnimation();
    if (menuPopup != null) {
      menuPopup.hide();
    }
  }

  private void stopMenuAnimation() {
    if (menuTimeline != null) {
      menuTimeline.stop();
      menuTimeline = null;
    }
  }

  private boolean isMenuShowing() {
    return menuPopup != null && menuPopup.isShowing();
  }

  private Stage stageFor(Node node) {
    return node.getScene() == null ? null : (Stage) node.getScene().getWindow();
  }

  private double computePopupX() {
    Bounds buttonBounds = primaryButton.localToScreen(primaryButton.getBoundsInLocal());
    return buttonBounds == null ? 0.0 : buttonBounds.getMinX() + POPUP_X_OFFSET;
  }

  private double computePopupY() {
    Bounds buttonBounds = primaryButton.localToScreen(primaryButton.getBoundsInLocal());
    return buttonBounds == null ? 0.0 : buttonBounds.getMaxY() + POPUP_GAP + POPUP_Y_OFFSET;
  }

  private void applyTheme(AppTheme theme) {
    menu.getStyleClass().removeAll(AppTheme.LIGHT.cssClass(), AppTheme.DARK.cssClass());
    menu.getStyleClass().add(theme == null ? AppTheme.LIGHT.cssClass() : theme.cssClass());
  }
}
