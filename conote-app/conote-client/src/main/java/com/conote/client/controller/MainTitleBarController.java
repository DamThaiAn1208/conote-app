package com.conote.client.controller;

import com.conote.client.app.ClientApplication;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MainTitleBarController {
  private static final double POPUP_GAP = 10.0;

  @FXML
  private HBox titleBar;

  @FXML
  private StackPane menuButton;

  @FXML
  private StackPane minimizeButton;

  @FXML
  private StackPane closeButton;

  private CoNoteStore store;
  private MainWindowController mainController;
  private double dragOffsetX;
  private double dragOffsetY;
  private final PauseTransition menuHideDelay = new PauseTransition(Duration.millis(180));
  private Popup userMenuPopup;
  private Parent userMenuRoot;
  private UserMenuDropdownController userMenuDropdownController;

  @FXML
  private void initialize() {
    menuHideDelay.setOnFinished(event -> hideMenuIfPointerLeft());
  }

  public void setContext(CoNoteStore store, MainWindowController mainController) {
    this.store = store;
    this.mainController = mainController;
    initializeUserMenuPopup();
  }

  @FXML
  private void showMenuFromAction() {
    if (isMenuShowing()) {
      hideMenu();
      return;
    }
    showMenu();
  }

  @FXML
  private void handleMenuHoverExited() {
    if (isMenuShowing()) {
      scheduleMenuHide();
    }
  }

  @FXML
  private void rememberDragOffset(MouseEvent event) {
    Stage stage = stageFor(titleBar);
    if (stage != null) {
      dragOffsetX = event.getScreenX() - stage.getX();
      dragOffsetY = event.getScreenY() - stage.getY();
    }
  }

  @FXML
  private void dragWindow(MouseEvent event) {
    Stage stage = stageFor(titleBar);
    if (stage != null) {
      stage.setX(event.getScreenX() - dragOffsetX);
      stage.setY(event.getScreenY() - dragOffsetY);
      if (isMenuShowing()) {
        updateMenuPosition();
      }
    }
  }

  @FXML
  private void minimizeWindow() {
    Stage stage = stageFor(titleBar);
    if (stage != null) {
      stage.setIconified(true);
    }
  }

  @FXML
  private void closeWindow() {
    hideMenu();
    Stage stage = stageFor(titleBar);
    if (stage != null) {
      stage.close();
    }
  }

  public void hideMenu() {
    cancelMenuHide();
    if (userMenuDropdownController != null) {
      userMenuDropdownController.setVisible(false);
    }
    if (userMenuPopup != null) {
      userMenuPopup.hide();
    }
  }

  private Stage stageFor(Node node) {
    return node.getScene() == null ? null : (Stage) node.getScene().getWindow();
  }

  private void showMenu() {
    Stage stage = stageFor(menuButton);
    if (stage == null || userMenuPopup == null || userMenuRoot == null || userMenuDropdownController == null) {
      return;
    }

    cancelMenuHide();
    userMenuDropdownController.setVisible(true);
    userMenuRoot.applyCss();
    userMenuRoot.layout();
    double popupX = computePopupX();
    double popupY = computePopupY();

    if (userMenuPopup.isShowing()) {
      userMenuPopup.setX(popupX);
      userMenuPopup.setY(popupY);
      return;
    }

    userMenuPopup.show(stage, popupX, popupY);
  }

  private void scheduleMenuHide() {
    menuHideDelay.playFromStart();
  }

  private void cancelMenuHide() {
    menuHideDelay.stop();
  }

  private void hideMenuIfPointerLeft() {
    if (!menuButton.isHover() && !userMenuDropdownController.isHovering()) {
      hideMenu();
    }
  }

  private void initializeUserMenuPopup() {
    LoadedView<UserMenuDropdownController> view =
        ViewLoader.load("/fxml/main/UserMenuDropdown.fxml");
    userMenuRoot = view.root();
    userMenuDropdownController = view.controller();
    userMenuDropdownController.setContext(store, mainController, this::hideMenu);
    userMenuDropdownController.setHoverCallbacks(this::cancelMenuHide, this::scheduleMenuHide);

    if (userMenuRoot instanceof Region region) {
      region.setVisible(false);
      region.setManaged(false);
      region.getStylesheets().add(ClientApplication.stylesheetUrl());
    } else {
      userMenuRoot.setVisible(false);
      userMenuRoot.setManaged(false);
    }

    userMenuPopup = new Popup();
    userMenuPopup.setAutoFix(true);
    userMenuPopup.setAutoHide(true);
    userMenuPopup.setHideOnEscape(true);
    userMenuPopup.getContent().setAll(userMenuRoot);
    userMenuPopup.setOnHidden(event -> {
      cancelMenuHide();
      if (userMenuDropdownController != null) {
        userMenuDropdownController.setVisible(false);
      }
    });
  }

  private boolean isMenuShowing() {
    return userMenuPopup != null && userMenuPopup.isShowing();
  }

  private void updateMenuPosition() {
    if (!isMenuShowing()) {
      return;
    }
    userMenuRoot.applyCss();
    userMenuRoot.layout();
    userMenuPopup.setX(computePopupX());
    userMenuPopup.setY(computePopupY());
  }

  private double computePopupX() {
    Bounds buttonBounds = menuButton.localToScreen(menuButton.getBoundsInLocal());
    double popupWidth = userMenuRoot.prefWidth(-1);
    return buttonBounds == null ? 0.0 : buttonBounds.getMaxX() - popupWidth;
  }

  private double computePopupY() {
    Bounds buttonBounds = menuButton.localToScreen(menuButton.getBoundsInLocal());
    return buttonBounds == null ? 0.0 : buttonBounds.getMaxY() + POPUP_GAP;
  }
}
