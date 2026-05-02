package com.conote.client.controller;

import com.conote.client.model.NoteModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class ConfirmDeleteDialogController {
  @FXML
  private Label copyLabel;

  private Runnable onClose;
  private Runnable onConfirm;
  private NoteModel note;

  public void setOnClose(Runnable onClose) {
    this.onClose = onClose;
  }

  public void setOnConfirm(Runnable onConfirm) {
    this.onConfirm = onConfirm;
  }

  public void setNote(NoteModel note) {
    this.note = note;
    String title = note.getTitle() == null || note.getTitle().isBlank() ? "this note" : note.getTitle();
    copyLabel.setText("Delete \"" + title + "\" permanently? This action cannot be undone.");
  }

  @FXML
  private void confirmDelete(ActionEvent event) {
    event.consume();
    if (onConfirm != null) {
      onConfirm.run();
    }
  }

  @FXML
  private void closeDialog(ActionEvent event) {
    event.consume();
    if (onClose != null) {
      onClose.run();
    }
  }
}
