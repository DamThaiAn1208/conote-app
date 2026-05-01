package com.conote.client.controller;

import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class NoteListController {
  private static final double MAIN_LIST_SCROLLBAR_OUTSIDE_OFFSET = 8.0;
  private static final double DRAG_AUTO_SCROLL_EDGE_SIZE = 72.0;
  private static final double DRAG_AUTO_SCROLL_MIN_DELTA = 0.002;
  private static final double DRAG_AUTO_SCROLL_MAX_DELTA = 0.018;
  private static final String DRAGGING_CLASS = "note-card-dragging";
  private static final String DROP_BEFORE_CLASS = "note-card-drop-before";
  private static final String DROP_AFTER_CLASS = "note-card-drop-after";

  @FXML
  private Pane noteListHost;

  @FXML
  private ScrollPane noteListScroll;

  @FXML
  private VBox listRoot;

  private final Map<String, Parent> noteCardsById = new LinkedHashMap<>();
  private final Map<String, NoteCardController> noteCardControllersById = new LinkedHashMap<>();
  private final Rectangle hostClip = new Rectangle();
  private final AnimationTimer dragAutoScrollTimer = new AnimationTimer() {
    @Override
    public void handle(long now) {
      applyDragAutoScroll();
    }
  };
  private CoNoteStore store;
  private MainWindowController mainController;
  private String draggedNoteId;
  private double dragAutoScrollDelta;
  private boolean dragAutoScrollRunning;

  public void setContext(CoNoteStore store, MainWindowController mainController) {
    this.store = store;
    this.mainController = mainController;

    configureStableListWidth();
    store.loadingProperty().addListener((obs, oldValue, newValue) -> render());
    store.getVisibleNotes().addListener((ListChangeListener<NoteModel>) this::handleVisibleNotesChanged);
    render();
  }

  public Parent findNoteCard(String noteId) {
    if (noteId == null || noteId.isBlank()) {
      return null;
    }
    return noteCardsById.get(noteId);
  }

  public void flushPendingEdits() {
    for (NoteCardController controller : noteCardControllersById.values()) {
      if (controller != null) {
        controller.flushPendingChanges();
      }
    }
  }

  private void configureStableListWidth() {
    if (noteListHost == null || noteListScroll == null || listRoot == null) {
      return;
    }

    noteListHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    noteListHost.setMinWidth(0);
    noteListHost.setPrefWidth(0);
    noteListHost.setMinHeight(0);
    noteListHost.setPrefHeight(0);
    noteListHost.setClip(hostClip);
    noteListScroll.setMinWidth(0);
    noteListScroll.setPrefWidth(0);
    noteListScroll.setMaxWidth(Double.MAX_VALUE);
    noteListScroll.setMinHeight(0);
    noteListScroll.setPrefHeight(0);
    noteListScroll.setMaxHeight(Double.MAX_VALUE);
    listRoot.setFillWidth(true);
    noteListHost.widthProperty().addListener((obs, oldValue, newValue) -> updateLayoutBounds());
    noteListHost.heightProperty().addListener((obs, oldValue, newValue) -> updateLayoutBounds());
    noteListScroll.viewportBoundsProperty().addListener((obs, oldValue, newValue) -> updateStableListWidth());
    noteListScroll.setOnDragOver(this::handleScrollPaneDragOver);
    Platform.runLater(this::updateLayoutBounds);
  }

  private void handleVisibleNotesChanged(ListChangeListener.Change<? extends NoteModel> change) {
    boolean requiresRender = false;
    while (change.next()) {
      if (change.wasAdded() || change.wasRemoved() || change.wasPermutated() || change.wasReplaced()) {
        requiresRender = true;
        break;
      }
    }

    if (requiresRender) {
      render();
    }
  }

  private void render() {
    listRoot.getChildren().clear();
    if (store.loadingProperty().get()) {
      return;
    }

    List<Parent> orderedCards = new ArrayList<>();
    Set<String> visibleIds = new HashSet<>();

    for (NoteModel note : store.getVisibleNotes()) {
      visibleIds.add(note.getId());
      Parent card = noteCardsById.get(note.getId());
      if (card == null) {
        LoadedView<NoteCardController> view =
            ViewLoader.load("/fxml/shared/NoteCard.fxml");
        view.controller().setContext(note, store, mainController);
        card = view.root();
        noteCardsById.put(note.getId(), card);
        noteCardControllersById.put(note.getId(), view.controller());
      }
      if (card instanceof Region region) {
        region.setMaxWidth(Double.MAX_VALUE);
      }
      installDragHandlers(card, note);
      orderedCards.add(card);
    }

    noteCardsById.entrySet().removeIf(entry -> !visibleIds.contains(entry.getKey()));
    noteCardControllersById.entrySet().removeIf(entry -> !visibleIds.contains(entry.getKey()));
    listRoot.getChildren().setAll(orderedCards);
    Platform.runLater(this::updateLayoutBounds);
  }

  private void installDragHandlers(Parent card, NoteModel note) {
    card.setOnDragDetected(event -> handleNoteDragDetected(event, card, note));
    card.setOnDragOver(event -> handleNoteDragOver(event, card, note));
    card.setOnDragExited(event -> clearDropIndicators());
    card.setOnDragDropped(event -> handleNoteDragDropped(event, card, note));
    card.setOnDragDone(event -> finishNoteDrag());
  }

  private void handleNoteDragDetected(MouseEvent event, Parent card, NoteModel note) {
    if (store == null
        || note == null
        || event.getButton() != MouseButton.PRIMARY
        || isInteractiveTarget(event.getTarget())) {
      return;
    }

    Dragboard dragboard = card.startDragAndDrop(TransferMode.MOVE);
    ClipboardContent content = new ClipboardContent();
    content.putString(note.getId());
    dragboard.setContent(content);
    WritableImage dragView = card.snapshot(new SnapshotParameters(), null);
    dragboard.setDragView(
        dragView,
        Math.min(Math.max(event.getX(), 0.0), dragView.getWidth()),
        Math.min(Math.max(event.getY(), 0.0), dragView.getHeight()));
    draggedNoteId = note.getId();
    toggleStyleClass(card, DRAGGING_CLASS, true);
    card.setOpacity(0.66);
    event.consume();
  }

  private void handleNoteDragOver(DragEvent event, Parent card, NoteModel targetNote) {
    String sourceNoteId = draggedNoteId(event.getDragboard());
    if (sourceNoteId == null) {
      return;
    }

    event.acceptTransferModes(TransferMode.MOVE);
    updateDragAutoScroll(event, card);
    if (targetNote == null || sourceNoteId.equals(targetNote.getId())) {
      clearDropIndicators();
      event.consume();
      return;
    }

    showDropIndicator(card, shouldPlaceAfter(event, card));
    event.consume();
  }

  private void handleNoteDragDropped(DragEvent event, Parent card, NoteModel targetNote) {
    String sourceNoteId = draggedNoteId(event.getDragboard());
    boolean moved = false;
    if (sourceNoteId != null && targetNote != null && !sourceNoteId.equals(targetNote.getId())) {
      flushPendingEdits();
      moved = store.moveVisibleNote(sourceNoteId, targetNote.getId(), shouldPlaceAfter(event, card));
    }

    event.setDropCompleted(moved);
    finishNoteDrag();
    event.consume();
  }

  private void handleScrollPaneDragOver(DragEvent event) {
    String sourceNoteId = draggedNoteId(event.getDragboard());
    if (sourceNoteId == null) {
      return;
    }

    event.acceptTransferModes(TransferMode.MOVE);
    clearDropIndicators();
    updateDragAutoScroll(event, noteListScroll);
    event.consume();
  }

  private boolean shouldPlaceAfter(DragEvent event, Parent card) {
    return event.getY() > card.getBoundsInLocal().getHeight() / 2.0;
  }

  private String draggedNoteId(Dragboard dragboard) {
    if (dragboard == null || !dragboard.hasString()) {
      return draggedNoteId;
    }
    return dragboard.getString();
  }

  private void showDropIndicator(Parent card, boolean after) {
    clearDropIndicators();
    toggleStyleClass(card, after ? DROP_AFTER_CLASS : DROP_BEFORE_CLASS, true);
  }

  private void finishNoteDrag() {
    draggedNoteId = null;
    stopDragAutoScroll();
    clearDropIndicators();
    for (Parent card : noteCardsById.values()) {
      toggleStyleClass(card, DRAGGING_CLASS, false);
      card.setOpacity(1.0);
    }
  }

  private void updateDragAutoScroll(DragEvent event, Node eventNode) {
    if (event == null || eventNode == null || noteListScroll == null || listRoot == null) {
      stopDragAutoScroll();
      return;
    }

    double viewportHeight = noteListScroll.getViewportBounds().getHeight();
    if (viewportHeight <= 0.0 || listRoot.getBoundsInLocal().getHeight() <= viewportHeight) {
      stopDragAutoScroll();
      return;
    }

    Point2D scenePoint = eventNode.localToScene(event.getX(), event.getY());
    Point2D scrollPoint = noteListScroll.sceneToLocal(scenePoint);
    double edgeSize = Math.min(DRAG_AUTO_SCROLL_EDGE_SIZE, viewportHeight / 2.0);
    double pointerY = scrollPoint.getY();
    double delta = 0.0;

    if (pointerY < edgeSize) {
      delta = -dragScrollDelta(pointerY, edgeSize);
    } else if (pointerY > viewportHeight - edgeSize) {
      delta = dragScrollDelta(viewportHeight - pointerY, edgeSize);
    }

    if (Math.abs(delta) <= 0.0) {
      stopDragAutoScroll();
      return;
    }

    dragAutoScrollDelta = delta;
    if (!dragAutoScrollRunning) {
      dragAutoScrollRunning = true;
      dragAutoScrollTimer.start();
    }
  }

  private double dragScrollDelta(double distanceFromEdge, double edgeSize) {
    double clampedDistance = Math.max(0.0, Math.min(distanceFromEdge, edgeSize));
    double intensity = (edgeSize - clampedDistance) / edgeSize;
    return DRAG_AUTO_SCROLL_MIN_DELTA
        + ((DRAG_AUTO_SCROLL_MAX_DELTA - DRAG_AUTO_SCROLL_MIN_DELTA)
        * Math.pow(intensity, 1.35));
  }

  private void applyDragAutoScroll() {
    if (noteListScroll == null || draggedNoteId == null) {
      stopDragAutoScroll();
      return;
    }

    double min = noteListScroll.getVmin();
    double max = noteListScroll.getVmax();
    double current = noteListScroll.getVvalue();
    double next = Math.max(min, Math.min(max, current + dragAutoScrollDelta));
    if (Double.compare(current, next) == 0) {
      if ((dragAutoScrollDelta < 0.0 && current <= min)
          || (dragAutoScrollDelta > 0.0 && current >= max)) {
        stopDragAutoScroll();
      }
      return;
    }
    noteListScroll.setVvalue(next);
  }

  private void stopDragAutoScroll() {
    dragAutoScrollDelta = 0.0;
    if (dragAutoScrollRunning) {
      dragAutoScrollTimer.stop();
      dragAutoScrollRunning = false;
    }
  }

  private void clearDropIndicators() {
    for (Parent card : noteCardsById.values()) {
      toggleStyleClass(card, DROP_BEFORE_CLASS, false);
      toggleStyleClass(card, DROP_AFTER_CLASS, false);
    }
  }

  private void toggleStyleClass(Parent card, String styleClass, boolean enabled) {
    if (enabled) {
      if (!card.getStyleClass().contains(styleClass)) {
        card.getStyleClass().add(styleClass);
      }
      return;
    }
    card.getStyleClass().remove(styleClass);
  }

  private boolean isInteractiveTarget(Object target) {
    if (!(target instanceof Node node)) {
      return false;
    }

    Node current = node;
    while (current != null) {
      if (current instanceof ButtonBase
          || current instanceof ComboBoxBase<?>
          || current instanceof TextInputControl
          || current.getStyleClass().contains("text-note-editor-area")
          || current.getStyleClass().contains("quick-note-textarea")
          || current.getStyleClass().contains("inline-checklist-field")
          || current.getStyleClass().contains("checklist-item-field")
          || current.getStyleClass().contains("note-card-actions")) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private void updateLayoutBounds() {
    if (noteListHost == null) {
      return;
    }

    double hostWidth = noteListHost.getWidth();
    double hostHeight = noteListHost.getHeight();
    if (hostWidth <= 0 || hostHeight <= 0) {
      return;
    }

    hostClip.setWidth(hostWidth + MAIN_LIST_SCROLLBAR_OUTSIDE_OFFSET);
    hostClip.setHeight(hostHeight);
    updateStableListWidth();
  }

  private void updateStableListWidth() {
    if (noteListHost == null || listRoot == null) {
      return;
    }

    double width = noteListHost.getWidth()
        - noteListHost.snappedLeftInset()
        - noteListHost.snappedRightInset();
    if (width <= 0) {
      return;
    }

    listRoot.setMinWidth(width);
    listRoot.setPrefWidth(width);
    listRoot.setMaxWidth(width);
  }
}
