package com.conote.client.controller;

import com.conote.client.model.AppTheme;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class UserMenuDropdownController {
  private static final String SWITCH_KNOB_KEY = "switchKnob";
  private static final String SWITCH_TRACK_KEY = "switchTrack";
  private static final String SWITCH_SLIDE_KEY = "switchSlide";
  private static final String SWITCH_PULSE_KEY = "switchPulse";

  @FXML
  private VBox root;

  @FXML
  private ToggleButton dockToggle;

  @FXML
  private ToggleButton darkThemeToggle;

  @FXML
  private Button helpButton;

  private Runnable onClose;
  private Runnable onHoverEnter;
  private Runnable onHoverExit;
  private MainWindowController mainController;
  private boolean syncingTheme;

  @FXML
  private void initialize() {
    MotionSupport.installButtonMotion(helpButton);
    configureSwitch(darkThemeToggle);
    configureSwitch(dockToggle);
  }

  public void setContext(CoNoteStore store, MainWindowController mainController, Runnable onClose) {
    this.mainController = mainController;
    this.onClose = onClose;
    dockToggle.selectedProperty().bindBidirectional(store.dockOnDesktopProperty());
    store.themeProperty().addListener((obs, oldValue, newValue) -> {
      if (darkThemeToggle.isSelected() != (newValue == AppTheme.DARK)) {
        syncThemeToggle(newValue);
      }
      applyTheme(newValue);
    });
    darkThemeToggle.selectedProperty().addListener((obs, oldValue, newValue) -> {
      updateSwitchVisual(darkThemeToggle, root.isVisible());
      if (!syncingTheme) {
        store.setTheme(newValue ? AppTheme.DARK : AppTheme.LIGHT);
      }
    });
    dockToggle.selectedProperty().addListener((obs, oldValue, newValue) -> updateSwitchVisual(dockToggle, root.isVisible()));
    syncThemeToggle(store.getTheme());
    applyTheme(store.getTheme());
    updateSwitchVisual(dockToggle, false);
    setVisible(false);
  }

  public boolean isHovering() {
    return root.isHover();
  }

  public void setVisible(boolean visible) {
    root.setVisible(visible);
    root.setManaged(visible);
  }

  public void setHoverCallbacks(Runnable onHoverEnter, Runnable onHoverExit) {
    this.onHoverEnter = onHoverEnter;
    this.onHoverExit = onHoverExit;
  }

  @FXML
  private void openHelpDialog() {
    if (onClose != null) {
      onClose.run();
    }
    if (mainController != null) {
      Platform.runLater(mainController::showHelpDialog);
    }
  }

  @FXML
  private void handleMouseEntered() {
    if (onHoverEnter != null) {
      onHoverEnter.run();
    }
  }

  @FXML
  private void handleMouseExited() {
    if (onHoverExit != null) {
      onHoverExit.run();
    }
  }

  private void syncThemeToggle(AppTheme theme) {
    syncingTheme = true;
    boolean selected = theme == AppTheme.DARK;
    boolean changed = darkThemeToggle.isSelected() != selected;
    darkThemeToggle.setSelected(selected);
    if (!changed) {
      updateSwitchVisual(darkThemeToggle, false);
    }
    syncingTheme = false;
  }

  private void configureSwitch(ToggleButton toggle) {
    StackPane track = new StackPane();
    track.getStyleClass().add("menu-switch-track");
    track.setMinSize(36, 18);
    track.setPrefSize(36, 18);
    track.setMaxSize(36, 18);

    StackPane knob = new StackPane();
    knob.getStyleClass().add("menu-switch-knob");
    knob.setMinSize(16, 16);
    knob.setPrefSize(16, 16);
    knob.setMaxSize(16, 16);
    StackPane.setAlignment(knob, Pos.CENTER_LEFT);
    track.getChildren().add(knob);

    toggle.getProperties().put(SWITCH_TRACK_KEY, track);
    toggle.getProperties().put(SWITCH_KNOB_KEY, knob);
    toggle.setGraphic(track);
    toggle.setText(null);
    toggle.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    toggle.setAlignment(Pos.CENTER);
    toggle.setFocusTraversable(false);
    updateSwitchVisual(toggle, false);
  }

  private void updateSwitchVisual(ToggleButton toggle, boolean animate) {
    StackPane track = switchTrack(toggle);
    StackPane knob = switchKnob(toggle);
    if (track == null || knob == null) {
      return;
    }

    double targetX = toggle.isSelected() ? track.getPrefWidth() - knob.getPrefWidth() : 0.0;
    if (!animate) {
      stopSwitchAnimations(toggle);
      knob.setTranslateX(targetX);
      knob.setScaleX(1.0);
      knob.setScaleY(1.0);
      return;
    }

    animateSwitchKnob(toggle, knob, targetX);
  }

  private void animateSwitchKnob(ToggleButton toggle, StackPane knob, double targetX) {
    stopSwitchAnimations(toggle);

    knob.setScaleX(0.94);
    knob.setScaleY(0.94);

    TranslateTransition slide = new TranslateTransition(Duration.millis(170), knob);
    slide.setToX(targetX);
    slide.setInterpolator(Interpolator.SPLINE(0.22, 1.0, 0.36, 1.0));

    ScaleTransition pulse = new ScaleTransition(Duration.millis(170), knob);
    pulse.setToX(1.0);
    pulse.setToY(1.0);
    pulse.setInterpolator(Interpolator.EASE_BOTH);

    toggle.getProperties().put(SWITCH_SLIDE_KEY, slide);
    toggle.getProperties().put(SWITCH_PULSE_KEY, pulse);

    slide.playFromStart();
    pulse.playFromStart();
  }

  private void stopSwitchAnimations(ToggleButton toggle) {
    Object slide = toggle.getProperties().get(SWITCH_SLIDE_KEY);
    if (slide instanceof TranslateTransition transition) {
      transition.stop();
    }

    Object pulse = toggle.getProperties().get(SWITCH_PULSE_KEY);
    if (pulse instanceof ScaleTransition transition) {
      transition.stop();
    }
  }

  private StackPane switchTrack(ToggleButton toggle) {
    Object track = toggle.getProperties().get(SWITCH_TRACK_KEY);
    return track instanceof StackPane stackPane ? stackPane : null;
  }

  private StackPane switchKnob(ToggleButton toggle) {
    Object knob = toggle.getProperties().get(SWITCH_KNOB_KEY);
    return knob instanceof StackPane stackPane ? stackPane : null;
  }

  private void applyTheme(AppTheme theme) {
    root.getStyleClass().removeAll(AppTheme.LIGHT.cssClass(), AppTheme.DARK.cssClass());
    root.getStyleClass().add(theme == null ? AppTheme.LIGHT.cssClass() : theme.cssClass());
  }
}
