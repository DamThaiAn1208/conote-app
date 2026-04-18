package com.conote.client.controller;

import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

public class TextNoteEditorController {
  @FXML
  private VBox root;

  @FXML
  private TextArea editorArea;

  private NoteModel note;
  private CoNoteStore store;

  public void setContext(NoteModel note, CoNoteStore store) {
    this.note = note;
    this.store = store;
    editorArea.setText(note.getContent());
    editorArea.textProperty().addListener((obs, oldValue, newValue) -> store.updateContent(note, newValue));
    note.contentProperty().addListener((obs, oldValue, newValue) -> {
      if (!editorArea.isFocused() && !editorArea.getText().equals(newValue)) {
        editorArea.setText(newValue);
      }
    });
  }

  public void setVisible(boolean visible) {
    root.setVisible(visible);
    root.setManaged(visible);
  }

  public void applyTypography(boolean bold, boolean italic, boolean underline) {
    StringBuilder builder = new StringBuilder("-fx-font-size: 17px; -fx-line-spacing: 7px;");
    builder.append(" -fx-text-fill: #1e3a5f;");
    builder.append(bold ? " -fx-font-weight: 700;" : " -fx-font-weight: 400;");
    builder.append(italic ? " -fx-font-style: italic;" : " -fx-font-style: normal;");
    if (underline) {
      builder.append(" -fx-text-fill: #0f172a;");
    }
    editorArea.setStyle(builder.toString());
  }
}
