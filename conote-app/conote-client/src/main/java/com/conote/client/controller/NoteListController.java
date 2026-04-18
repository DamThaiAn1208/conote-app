package com.conote.client.controller;

import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

public class NoteListController {
  @FXML
  private VBox listRoot;

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
    for (NoteModel note : store.getVisibleNotes()) {
      LoadedView<NoteCardController> view =
          ViewLoader.load("/fxml/shared/NoteCard.fxml");
      view.controller().setContext(note, store, mainController);
      listRoot.getChildren().add(view.root());
    }
  }
}
