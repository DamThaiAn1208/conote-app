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
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class NoteListController {
  private static final double MAIN_LIST_SCROLLBAR_OUTSIDE_OFFSET = 8.0;

  @FXML
  private Pane noteListHost;

  @FXML
  private ScrollPane noteListScroll;

  @FXML
  private VBox listRoot;

  private final Map<String, Parent> noteCardsById = new LinkedHashMap<>();
  private final Rectangle hostClip = new Rectangle();
  private CoNoteStore store;
  private MainWindowController mainController;

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
      }
      if (card instanceof Region region) {
        region.setMaxWidth(Double.MAX_VALUE);
      }
      orderedCards.add(card);
    }

    noteCardsById.entrySet().removeIf(entry -> !visibleIds.contains(entry.getKey()));
    listRoot.getChildren().setAll(orderedCards);
    Platform.runLater(this::updateLayoutBounds);
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
