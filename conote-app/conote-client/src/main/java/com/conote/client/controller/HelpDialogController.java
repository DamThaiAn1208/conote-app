package com.conote.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class HelpDialogController {
  @FXML
  private VBox dialogCard;

  private Runnable onClose;

  @FXML
  private void initialize() {
    if (dialogCard != null) {
      dialogCard.setMinHeight(Region.USE_COMPUTED_SIZE);
      dialogCard.setPrefHeight(Region.USE_COMPUTED_SIZE);
      dialogCard.setMaxHeight(Region.USE_PREF_SIZE);
    }
  }

  public void setOnClose(Runnable onClose) {
    this.onClose = onClose;
  }

  @FXML
  private void closeDialog(ActionEvent event) {
    event.consume();
    close();
  }

  @FXML
  private void closeOnBackdrop(MouseEvent event) {
    if (event.getTarget() == event.getSource()) {
      close();
    }
  }

  @FXML
  private void consume(MouseEvent event) {
    event.consume();
  }

  private void close() {
    if (onClose != null) {
      onClose.run();
    }
  }
}
