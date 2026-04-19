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
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;

public class NoteListController {
  @FXML
  private VBox listRoot;

  private final Map<String, Parent> noteCardsById = new LinkedHashMap<>();
  private CoNoteStore store;
  private MainWindowController mainController;

  public void setContext(CoNoteStore store, MainWindowController mainController) {
    this.store = store;
    this.mainController = mainController;

    store.loadingProperty().addListener((obs, oldValue, newValue) -> render());
    store.getVisibleNotes().addListener((ListChangeListener<NoteModel>) this::handleVisibleNotesChanged);
    render();
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
      orderedCards.add(card);
    }

    noteCardsById.entrySet().removeIf(entry -> !visibleIds.contains(entry.getKey()));
    listRoot.getChildren().setAll(orderedCards);
  }
}
