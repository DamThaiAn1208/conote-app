package com.conote.client.controller;

import com.conote.client.app.ClientApplication;
import com.conote.client.model.AppTheme;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.common.enums.NoteType;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

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
  private final Map<String, NoteWindowController> openWindowControllers = new HashMap<>();
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

    stage.addEventHandler(WindowEvent.WINDOW_HIDING, event -> flushPendingChanges());
    stage.widthProperty().addListener((obs, oldValue, newValue) ->
        store.updateWindowSize(newValue.doubleValue(), stage.getHeight()));
    stage.heightProperty().addListener((obs, oldValue, newValue) ->
        store.updateWindowSize(stage.getWidth(), newValue.doubleValue()));
  }

  public void openNoteWindow(NoteModel note) {
    if (note == null) {
      return;
    }

    flushPendingChanges();

    Stage existing = openWindows.get(note.getId());
    if (existing != null) {
      existing.show();
      existing.toFront();
      existing.requestFocus();
      return;
    }

    LoadedView<NoteWindowController> view =
        ViewLoader.load("/fxml/note/NoteWindow.fxml");
    Scene scene = new Scene(
        view.root(),
        NoteWindowController.DEFAULT_WINDOW_WIDTH,
        NoteWindowController.DEFAULT_WINDOW_HEIGHT);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(ClientApplication.stylesheetUrl());

    Stage stage = new Stage();
    if (currentStage() != null) {
      stage.initOwner(currentStage());
    }
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setScene(scene);
    stage.setTitle("");
    stage.setMinWidth(NoteWindowController.MIN_WINDOW_WIDTH);
    stage.setMinHeight(NoteWindowController.MIN_WINDOW_HEIGHT);
    stage.setResizable(true);
    stage.setOnHidden(event -> {
      openWindows.remove(note.getId());
      openWindowControllers.remove(note.getId());
    });

    view.controller().setContext(note, store, stage);
    openWindows.put(note.getId(), stage);
    openWindowControllers.put(note.getId(), view.controller());
    stage.show();
    stage.toFront();
    stage.requestFocus();
  }

  public void closeNoteWindow(NoteModel note) {
    if (note == null) {
      return;
    }
    Stage stage = openWindows.remove(note.getId());
    openWindowControllers.remove(note.getId());
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

  public void flushPendingChanges() {
    if (noteListController != null) {
      noteListController.flushPendingEdits();
    }
    for (NoteWindowController controller : new ArrayList<>(openWindowControllers.values())) {
      if (controller != null) {
        controller.flushPendingEdits();
      }
    }
  }

  public Parent findNoteCard(String noteId) {
    return noteListController == null ? null : noteListController.findNoteCard(noteId);
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
    if (handleSelectedNoteTabTraversal(event)) {
      return;
    }

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

  private boolean handleSelectedNoteTabTraversal(KeyEvent event) {
    if (event.getCode() != KeyCode.TAB || overlayLayer.isVisible()) {
      return false;
    }
    Parent selectedNoteCard = noteListController.findNoteCard(store.expandedNoteIdProperty().get());
    if (selectedNoteCard == null) {
      Node selectedNode = root.lookup(".note-card-selected");
      selectedNoteCard = selectedNode instanceof Parent parent ? parent : null;
    }
    if (selectedNoteCard == null) {
      return false;
    }

    List<Node> focusTargets = collectSelectedNoteFocusTargets(selectedNoteCard);
    if (focusTargets.isEmpty()) {
      return false;
    }

    Node focusOwner = root.getScene() == null ? null : root.getScene().getFocusOwner();
    if (isFocusOwnerOnSelectedNoteControl(focusTargets, focusOwner)) {
      return false;
    }

    int currentIndex = indexOfFocusedTarget(focusTargets, focusOwner);
    int targetIndex = currentIndex < 0
        ? (event.isShiftDown() ? focusTargets.size() - 1 : 0)
        : Math.floorMod(currentIndex + (event.isShiftDown() ? -1 : 1), focusTargets.size());
    Platform.runLater(() -> focusTargets.get(targetIndex).requestFocus());
    boolean moved = true;
    if (moved) {
      event.consume();
    }
    return moved;
  }

  boolean moveFocusWithinSelectedNote(boolean backward) {
    Node target = nextFocusWithinSelectedNote(backward);
    if (target == null) {
      return false;
    }
    Platform.runLater(target::requestFocus);
    return true;
  }

  Node nextFocusWithinSelectedNote(boolean backward) {
    Parent selectedNoteCard = noteListController.findNoteCard(store.expandedNoteIdProperty().get());
    if (selectedNoteCard == null) {
      Node selectedNode = root.lookup(".note-card-selected");
      selectedNoteCard = selectedNode instanceof Parent parent ? parent : null;
    }
    if (selectedNoteCard == null) {
      return null;
    }

    List<Node> focusTargets = collectSelectedNoteFocusTargets(selectedNoteCard);
    if (focusTargets.isEmpty()) {
      return null;
    }

    Node focusOwner = root.getScene() == null ? null : root.getScene().getFocusOwner();
    int currentIndex = indexOfFocusedTarget(focusTargets, focusOwner);
    int targetIndex = currentIndex < 0
        ? (backward ? focusTargets.size() - 1 : 0)
        : Math.floorMod(currentIndex + (backward ? -1 : 1), focusTargets.size());
    return focusTargets.get(targetIndex);
  }

  private List<Node> collectSelectedNoteFocusTargets(Parent selectedNoteCard) {
    List<Node> targets = new ArrayList<>();
    appendFocusableTarget(targets, selectedNoteCard.lookup("#titleField"));
    appendFocusableTarget(targets, selectedNoteCard.lookup("#pinButton"));
    appendFocusableTarget(targets, selectedNoteCard.lookup("#openWindowButton"));
    appendFocusableTarget(targets, selectedNoteCard.lookup("#quickTextArea"));
    appendFocusableTarget(targets, selectedNoteCard.lookup(".text-note-editor-area"));

    Node checklistItems = selectedNoteCard.lookup("#checklistItemsBox");
    if (checklistItems instanceof VBox checklistItemsBox
        && checklistItemsBox.isVisible()
        && checklistItemsBox.isManaged()) {
      for (Node rowNode : checklistItemsBox.getChildren()) {
        if (!(rowNode instanceof HBox row) || !row.isVisible() || !row.isManaged()) {
          continue;
        }
        for (Node rowChild : row.getChildren()) {
          appendFocusableTarget(targets, rowChild);
        }
      }
    }

    appendFocusableTarget(targets, selectedNoteCard.lookup("#addChecklistItemButton"));
    return targets;
  }

  private void appendFocusableTarget(List<Node> targets, Node candidate) {
    if (candidate != null
        && isEffectivelyVisible(candidate)
        && candidate.isFocusTraversable()
        && !candidate.isDisabled()
        && !candidate.isMouseTransparent()) {
      targets.add(candidate);
    }
  }

  private boolean isEffectivelyVisible(Node node) {
    if (node == null || node.getScene() == null) {
      return false;
    }

    Node current = node;
    while (current != null) {
      if (!current.isVisible() || !current.isManaged()) {
        return false;
      }
      current = current.getParent();
    }
    return true;
  }

  private int indexOfFocusedTarget(List<Node> focusTargets, Node focusOwner) {
    for (int index = 0; index < focusTargets.size(); index++) {
      if (isSameNodeOrDescendant(focusTargets.get(index), focusOwner)) {
        return index;
      }
    }
    return -1;
  }

  private boolean isFocusOwnerOnSelectedNoteControl(List<Node> focusTargets, Node focusOwner) {
    if (focusOwner == null) {
      return false;
    }

    for (Node focusTarget : focusTargets) {
      if (isSameNodeOrDescendant(focusTarget, focusOwner)) {
        return true;
      }
    }
    return false;
  }

  private boolean isSameNodeOrDescendant(Node ancestor, Node node) {
    Node current = node;
    while (current != null) {
      if (current == ancestor) {
        return true;
      }
      current = current.getParent();
    }
    return false;
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
