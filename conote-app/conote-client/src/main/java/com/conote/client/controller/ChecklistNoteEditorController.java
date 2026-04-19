package com.conote.client.controller;

import com.conote.client.model.ChecklistItemModel;
import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.IconFactory;
import com.conote.client.util.MotionSupport;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ChecklistNoteEditorController {
  @FXML
  private VBox root;

  @FXML
  private VBox itemsBox;

  private NoteModel note;
  private CoNoteStore store;

  public void setContext(NoteModel note, CoNoteStore store) {
    this.note = note;
    this.store = store;
    note.getChecklistItems().addListener((ListChangeListener<ChecklistItemModel>) this::handleItemsChanged);
    render();
  }

  public void setVisible(boolean visible) {
    root.setVisible(visible);
    root.setManaged(visible);
  }

  @FXML
  private void addItem() {
    store.addChecklistItem(note);
    render();
  }

  private void render() {
    itemsBox.getChildren().clear();
    for (ChecklistItemModel item : note.getChecklistItems()) {
      itemsBox.getChildren().add(buildRow(item));
    }
  }

  private void handleItemsChanged(ListChangeListener.Change<? extends ChecklistItemModel> change) {
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

  private HBox buildRow(ChecklistItemModel item) {
    HBox row = new HBox(10);
    row.getStyleClass().add("checklist-row");

    CheckBox box = new CheckBox();
    box.setSelected(item.isChecked());
    box.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (item.isChecked() != newValue) {
        store.toggleChecklistItem(note, item);
      }
    });
    item.checkedProperty().addListener((obs, oldValue, newValue) -> {
      if (box.isSelected() != newValue) {
        box.setSelected(newValue);
      }
    });

    TextField textField = new TextField(item.getText());
    textField.getStyleClass().add("checklist-item-field");
    HBox.setHgrow(textField, Priority.ALWAYS);
    textField.textProperty().addListener((obs, oldValue, newValue) ->
        store.updateChecklistItemText(note, item, newValue));
    item.textProperty().addListener((obs, oldValue, newValue) -> {
      if (!textField.isFocused() && !textField.getText().equals(newValue)) {
        textField.setText(newValue);
      }
    });

    Button removeButton = new Button();
    removeButton.getStyleClass().add("mini-icon-button");
    IconFactory.apply(removeButton, "codicon-close", 12, "mini-action-icon");
    removeButton.setText(null);
    MotionSupport.installButtonMotion(removeButton);
    removeButton.setOnAction(event -> store.removeChecklistItem(note, item));

    row.getChildren().addAll(box, textField, removeButton);
    return row;
  }
}
