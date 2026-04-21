package com.conote.client.controller;

import com.conote.client.app.ClientApplication;
import com.conote.client.model.AppTheme;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.common.enums.NoteType;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
    filterPanelController.setContext(store, this::refreshFilterButtonState);
    refreshFilterButtonState();
    createNoteButtonController.setContext(store, this::openNoteWindow);
    noteListController.setContext(store, this);
    emptyStateController.setCreateAction(() -> {
      NoteModel note = store.createNote(NoteType.TEXT);
      openNoteWindow(note);
    });

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
    root.setFocusTraversable(true);
    root.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleMainWindowClick);
    root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleMainWindowKeyPressed);
    applyTheme();

    ChangeListener<Object> refreshListener = (obs, oldValue, newValue) -> refreshStates();
    store.loadingProperty().addListener(refreshListener);
    store.getVisibleNotes().addListener((ListChangeListener<NoteModel>) change -> refreshStates());
    store.themeProperty().addListener((obs, oldValue, newValue) -> applyTheme());
    store.sortModeProperty().addListener((obs, oldValue, newValue) -> refreshFilterButtonState());
    store.getSelectedTags().addListener((SetChangeListener<String>) change -> refreshFilterButtonState());
    store.getSelectedColors().addListener((SetChangeListener<NoteColor>) change -> refreshFilterButtonState());

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

  private void handleMainWindowClick(MouseEvent event) {
    Node node = resolveClickedNode(event);
    if (event.getButton() != MouseButton.PRIMARY
        || overlayLayer.isVisible()
        || node == null) {
      return;
    }

    dismissSearchAndFilters(node);

    if (isInsideNoteCard(node)) {
      return;
    }

    store.setExpandedNoteId(null);
  }

  private void handleMainWindowKeyPressed(KeyEvent event) {
    if (event.getCode() != KeyCode.ESCAPE
        || overlayLayer.isVisible()
        || store.expandedNoteIdProperty().get() == null) {
      return;
    }

    store.setExpandedNoteId(null);
    event.consume();
  }

  private void toggleFilters() {
    boolean expanded = !filterPanelController.isExpanded();
    filterPanelController.setExpanded(expanded);
    refreshFilterButtonState();
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

  private void refreshFilterButtonState() {
    searchBarController.setFilterActive(filterPanelController.isExpanded() || store.hasActiveFilters());
  }

  private void dismissSearchAndFilters(Node node) {
    if (!searchBarController.isInsideSearchArea(node)) {
      Platform.runLater(() -> {
        if (searchBarController.isSearchFocused()) {
          root.requestFocus();
        }
      });
    }

    if (filterPanelController.isExpanded()
        && !filterPanelController.containsNode(node)
        && !searchBarController.isFilterButtonTarget(node)) {
      filterPanelController.setExpanded(false);
      refreshFilterButtonState();
    }
  }

  private boolean isInsideNoteCard(Node node) {
    Node current = node;
    while (current != null) {
      if (current.getStyleClass().contains("note-card")) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private Node resolveClickedNode(MouseEvent event) {
    if (event.getPickResult() != null && event.getPickResult().getIntersectedNode() != null) {
      return event.getPickResult().getIntersectedNode();
    }
    return event.getTarget() instanceof Node node ? node : null;
  }

  private Stage currentStage() {
    return root.getScene() == null ? null : (Stage) root.getScene().getWindow();
  }
}
