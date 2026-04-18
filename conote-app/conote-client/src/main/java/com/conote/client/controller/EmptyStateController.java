package com.conote.client.controller;

import javafx.fxml.FXML;

public class EmptyStateController {
  private Runnable createAction;

  public void setCreateAction(Runnable createAction) {
    this.createAction = createAction;
  }

  @FXML
  private void createFirstNote() {
    if (createAction != null) {
      createAction.run();
    }
  }
}
