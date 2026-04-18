package com.conote.client.controller;

import com.conote.client.util.MotionSupport;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class TagChipController {
  @FXML
  private HBox root;

  @FXML
  private Label textLabel;

  @FXML
  private Button removeButton;

  @FXML
  private void initialize() {
    MotionSupport.installButtonMotion(removeButton);
  }

  public void configure(String text, boolean removable, Runnable onRemove, boolean accent) {
    textLabel.setText("#" + text);
    removeButton.setVisible(removable);
    removeButton.setManaged(removable);
    removeButton.setOnAction(event -> {
      if (onRemove != null) {
        onRemove.run();
      }
    });
    if (accent) {
      root.getStyleClass().add("tag-chip-accent");
    } else {
      root.getStyleClass().remove("tag-chip-accent");
    }
  }
}
