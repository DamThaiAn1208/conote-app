package com.conote.client.controller;

import com.conote.client.app.ClientApplication;
import com.conote.client.model.AppTheme;
import com.conote.client.model.NoteModel;
import com.conote.common.enums.NoteType;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.util.HashMap;
import java.util.Map;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainWindowController {
  @FXML
  private StackPane root;

  @FXML
  private StackPane windowFrame;

  @FXML
  private VBox mainWindowShell;

  @FXML
  private MainTitleBarController titleBarController;

  @FXML
  private SearchBarController searchBarController;

  @FXML
  private FilterPanelController filterPanelController;

  @FXML
  private CreateNoteButtonController createNoteButtonController;

  @FXML
  private NoteListController noteListController;

  @FXML
  private EmptyStateController emptyStateController;

  @FXML
  private StackPane loadingStatePane;

  @FXML
  private StackPane emptyStatePane;

  @FXML
  private StackPane listStatePane;

  @FXML
  private StackPane overlayLayer;

  private final CoNoteStore store = new CoNoteStore();
  private final Map<String, Stage> openWindows = new HashMap<>();
  private final GaussianBlur overlayBlur = new GaussianBlur(14);
  private final Rectangle overlayClip = new Rectangle();

  @FXML
  private void initialize() {
    titleBarController.setContext(store, this);
    searchBarController.setContext(store, this::toggleFilters);
    filterPanelController.setContext(store);
    createNoteButtonController.setContext(store);
    noteListController.setContext(store, this);
    emptyStateController.setCreateAction(() -> store.createNote(NoteType.TEXT));

    overlayLayer.setVisible(false);
    overlayLayer.setManaged(false);
    overlayLayer.setMouseTransparent(true);
    overlayLayer.minWidthProperty().bind(windowFrame.widthProperty());
    overlayLayer.prefWidthProperty().bind(windowFrame.widthProperty());
    overlayLayer.maxWidthProperty().bind(windowFrame.widthProperty());
    overlayLayer.minHeightProperty().bind(windowFrame.heightProperty());
    overlayLayer.prefHeightProperty().bind(windowFrame.heightProperty());
    overlayLayer.maxHeightProperty().bind(windowFrame.heightProperty());
    overlayClip.widthProperty().bind(overlayLayer.widthProperty());
    overlayClip.heightProperty().bind(overlayLayer.heightProperty());
    overlayClip.setArcWidth(10.0);
    overlayClip.setArcHeight(10.0);
    overlayLayer.setClip(overlayClip);
    applyTheme();

    ChangeListener<Object> refreshListener = (obs, oldValue, newValue) -> refreshStates();
    store.loadingProperty().addListener(refreshListener);
    store.getVisibleNotes().addListener((ListChangeListener<NoteModel>) change -> refreshStates());
    store.themeProperty().addListener((obs, oldValue, newValue) -> applyTheme());

    refreshStates();
  }

  public double getInitialWindowWidth() {
    return store.getWindowWidth();
  }

  public double getInitialWindowHeight() {
    return store.getWindowHeight();
  }

  public void bindPrimaryStage(Stage stage) {
    if (stage == null) {
      return;
    }

    stage.widthProperty().addListener((obs, oldValue, newValue) ->
        store.updateWindowSize(newValue.doubleValue(), stage.getHeight()));
    stage.heightProperty().addListener((obs, oldValue, newValue) ->
        store.updateWindowSize(stage.getWidth(), newValue.doubleValue()));
  }

  public void openNoteWindow(NoteModel note) {
    if (note == null) {
      return;
    }

    Stage existing = openWindows.get(note.getId());
    if (existing != null) {
      existing.show();
      existing.toFront();
      existing.requestFocus();
      return;
    }

    LoadedView<NoteWindowController> view =
        ViewLoader.load("/fxml/note/NoteWindow.fxml");
    Scene scene = new Scene(view.root(), 940, 660);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(ClientApplication.stylesheetUrl());

    Stage stage = new Stage();
    if (currentStage() != null) {
      stage.initOwner(currentStage());
    }
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setScene(scene);
    stage.setTitle("");
    stage.setMinWidth(740);
    stage.setMinHeight(540);
    stage.setOnHidden(event -> openWindows.remove(note.getId()));

    view.controller().setContext(note, store, stage);
    openWindows.put(note.getId(), stage);
    stage.show();
    stage.toFront();
    stage.requestFocus();
  }

  public void closeNoteWindow(NoteModel note) {
    if (note == null) {
      return;
    }
    Stage stage = openWindows.remove(note.getId());
    if (stage != null) {
      stage.close();
    }
  }

  public void deleteNote(NoteModel note) {
    closeNoteWindow(note);
    store.deleteNote(note);
    closeOverlay();
  }

  public void showHelpDialog() {
    LoadedView<HelpDialogController> view =
        ViewLoader.load("/fxml/dialog/HelpDialog.fxml");
    view.controller().setOnClose(this::closeOverlay);
    showOverlay(view.root());
  }

  public void closeOverlay() {
    overlayLayer.getChildren().clear();
    overlayLayer.setVisible(false);
    overlayLayer.setManaged(false);
    overlayLayer.setMouseTransparent(true);
    if (mainWindowShell != null) {
      mainWindowShell.setEffect(null);
    }
  }

  public CoNoteStore getStore() {
    return store;
  }

  private void toggleFilters() {
    filterPanelController.setExpanded(!filterPanelController.isExpanded());
  }

  private void refreshStates() {
    boolean loading = store.loadingProperty().get();
    boolean empty = !loading && store.getVisibleNotes().isEmpty();
    setVisible(loadingStatePane, loading);
    setVisible(emptyStatePane, empty);
    setVisible(listStatePane, !loading && !empty);
  }

  private void showOverlay(Parent content) {
    overlayLayer.getChildren().setAll(content);
    overlayLayer.setVisible(true);
    overlayLayer.setManaged(true);
    overlayLayer.setMouseTransparent(false);
    if (mainWindowShell != null) {
      mainWindowShell.setEffect(overlayBlur);
    }
    StackPane.setAlignment(content, Pos.CENTER);
    if (content instanceof Region region) {
      region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }
    content.toFront();
  }

  private void applyTheme() {
    root.getStyleClass().removeAll(AppTheme.LIGHT.cssClass(), AppTheme.DARK.cssClass());
    root.getStyleClass().add(store.getTheme().cssClass());
  }

  private void setVisible(StackPane pane, boolean visible) {
    pane.setVisible(visible);
    pane.setManaged(visible);
  }

  private Stage currentStage() {
    return root.getScene() == null ? null : (Stage) root.getScene().getWindow();
  }
}
