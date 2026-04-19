package com.conote.client.controller;

import com.conote.common.enums.NoteType;
import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class CreateNoteButtonController {
  @FXML
  private VBox menu;

  @FXML
  private HBox primaryButton;

  @FXML
  private HBox textNoteRow;

  @FXML
  private HBox checklistNoteRow;

  @FXML
  private Button textNoteButton;

  @FXML
  private Button checklistNoteButton;

  private CoNoteStore store;
  private Consumer<NoteModel> onNoteCreated;

  @FXML
  private void initialize() {
    MotionSupport.installGentleButtonMotion(primaryButton);
    textNoteButton.setMouseTransparent(true);
    checklistNoteButton.setMouseTransparent(true);
    textNoteButton.setFocusTraversable(false);
    checklistNoteButton.setFocusTraversable(false);
    textNoteRow.setPickOnBounds(true);
    checklistNoteRow.setPickOnBounds(true);
    textNoteRow.setOnMouseClicked(event -> createTextNote());
    checklistNoteRow.setOnMouseClicked(event -> createChecklistNote());
    setMenuVisible(false);
  }

  public void setContext(CoNoteStore store, Consumer<NoteModel> onNoteCreated) {
    this.store = store;
    this.onNoteCreated = onNoteCreated;
    menu.prefWidthProperty().bind(primaryButton.widthProperty());
    menu.minWidthProperty().bind(primaryButton.widthProperty());
    menu.maxWidthProperty().bind(primaryButton.widthProperty());
  }

  @FXML
  private void toggleMenu() {
    setMenuVisible(!menu.isVisible());
  }

  @FXML
  private void createTextNote() {
    NoteModel note = store.createNote(NoteType.TEXT);
    setMenuVisible(false);
    openCreatedNote(note);
  }

  @FXML
  private void createChecklistNote() {
    NoteModel note = store.createNote(NoteType.CHECKLIST);
    setMenuVisible(false);
    openCreatedNote(note);
  }

  private void setMenuVisible(boolean visible) {
    menu.setVisible(visible);
    menu.setManaged(visible);
  }

  private void openCreatedNote(NoteModel note) {
    if (note != null && onNoteCreated != null) {
      onNoteCreated.accept(note);
    }
  }
}
