package com.conote.client.controller;

import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.util.Comparator;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class TagSelectorController {
  @FXML
  private VBox root;

  @FXML
  private TextField tagField;

  @FXML
  private FlowPane currentTagsFlow;

  @FXML
  private FlowPane availableTagsFlow;

  private NoteModel note;
  private CoNoteStore store;

  public void setContext(NoteModel note, CoNoteStore store) {
    this.note = note;
    this.store = store;
    note.revisionProperty().addListener((obs, oldValue, newValue) -> render());
    render();
  }

  public void setVisible(boolean visible) {
    root.setVisible(visible);
    root.setManaged(visible);
  }

  public boolean isVisible() {
    return root.isVisible();
  }

  @FXML
  private void addTag() {
    store.addTag(note, tagField.getText());
    tagField.clear();
    render();
  }

  private void render() {
    currentTagsFlow.getChildren().clear();
    availableTagsFlow.getChildren().clear();

    for (String tag : note.getTags()) {
      LoadedView<TagChipController> view =
          ViewLoader.load("/fxml/shared/TagChip.fxml");
      view.controller().configure(tag, true, () -> {
        store.removeTag(note, tag);
        render();
      }, true);
      currentTagsFlow.getChildren().add(view.root());
    }

    store.collectAvailableTags().stream()
        .filter(tag -> !note.getTags().contains(tag))
        .sorted(Comparator.naturalOrder())
        .forEach(tag -> {
          javafx.scene.control.Button chip = new javafx.scene.control.Button("#" + tag);
          chip.getStyleClass().add("filter-chip");
          MotionSupport.installButtonMotion(chip);
          chip.setOnAction(event -> {
            store.addTag(note, tag);
            render();
          });
          availableTagsFlow.getChildren().add(chip);
        });
  }
}
