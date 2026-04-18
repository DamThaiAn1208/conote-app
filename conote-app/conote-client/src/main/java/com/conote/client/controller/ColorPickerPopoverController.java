package com.conote.client.controller;

import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class ColorPickerPopoverController {
  @FXML
  private VBox root;

  @FXML
  private FlowPane colorFlow;

  private NoteModel note;
  private CoNoteStore store;

  public void setContext(NoteModel note, CoNoteStore store) {
    this.note = note;
    this.store = store;
    note.colorProperty().addListener((obs, oldValue, newValue) -> render());
    render();
  }

  public void setVisible(boolean visible) {
    root.setVisible(visible);
    root.setManaged(visible);
  }

  public boolean isVisible() {
    return root.isVisible();
  }

  private void render() {
    colorFlow.getChildren().clear();
    for (NoteColor color : NoteColor.values()) {
      Button swatch = new Button();
      swatch.getStyleClass().addAll("color-filter-swatch", "swatch-" + color.cssName());
      if (note.getColor() == color) {
        swatch.getStyleClass().add("swatch-selected");
      }
      MotionSupport.installButtonMotion(swatch);
      swatch.setOnAction(event -> store.setColor(note, color));
      colorFlow.getChildren().add(swatch);
    }
  }
}
