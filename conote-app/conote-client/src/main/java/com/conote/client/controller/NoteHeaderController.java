package com.conote.client.controller;

import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;

public class NoteHeaderController {
  @FXML
  private ToggleButton pinButton;

  @FXML
  private Button colorButton;

  @FXML
  private Button tagButton;

  @FXML
  private Button shareButton;

  @FXML
  private Button deleteButton;

  @FXML
  private ColorPickerPopoverController colorPickerPopoverController;

  @FXML
  private TagSelectorController tagSelectorController;

  private NoteModel note;
  private CoNoteStore store;
  private NoteWindowController noteWindowController;

  @FXML
  private void initialize() {
    MotionSupport.installButtonMotion(pinButton);
    MotionSupport.installButtonMotion(colorButton);
    MotionSupport.installButtonMotion(tagButton);
    MotionSupport.installButtonMotion(shareButton);
    MotionSupport.installButtonMotion(deleteButton);
    colorPickerPopoverController.setVisible(false);
    tagSelectorController.setVisible(false);
  }

  public void setContext(NoteModel note, CoNoteStore store, NoteWindowController noteWindowController) {
    this.note = note;
    this.store = store;
    this.noteWindowController = noteWindowController;
    pinButton.setSelected(note.isPinned());
    note.pinnedProperty().addListener((obs, oldValue, newValue) -> pinButton.setSelected(newValue));
    colorPickerPopoverController.setContext(note, store);
    tagSelectorController.setContext(note, store);
  }

  @FXML
  private void togglePin() {
    store.togglePin(note);
  }

  @FXML
  private void toggleColorPicker() {
    boolean nextVisible = !colorPickerPopoverController.isVisible();
    colorPickerPopoverController.setVisible(nextVisible);
    if (nextVisible) {
      tagSelectorController.setVisible(false);
    }
  }

  @FXML
  private void toggleTagSelector() {
    boolean nextVisible = !tagSelectorController.isVisible();
    tagSelectorController.setVisible(nextVisible);
    if (nextVisible) {
      colorPickerPopoverController.setVisible(false);
    }
  }

  @FXML
  private void openShareDialog() {
    if (noteWindowController != null) {
      noteWindowController.showShareDialog();
    }
  }

  @FXML
  private void confirmDelete() {
    if (noteWindowController != null) {
      noteWindowController.showConfirmDeleteDialog();
    }
  }
}
