package com.conote.client.util;

import javafx.animation.Interpolator;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.util.Duration;

public final class MotionSupport {
  private static final String MOTION_TIMELINE_KEY = "motion.timeline";

  private MotionSupport() {
  }

  public static void installButtonMotion(Node node) {
    installResponsiveMotion(node, 1.04, -0.5, 0.92, 0.98);
  }

  public static void installGentleButtonMotion(Node node) {
    installResponsiveMotion(node, 1.015, -0.25, 0.94, 0.992);
  }

  public static void installCardMotion(Node node) {
    installCardMotion(node, null);
  }

  public static void installCardMotion(Node node, ObservableBooleanValue suppressMotion) {
    installResponsiveMotion(node, 1.0, -2.5, 0.98, 0.995, suppressMotion);
  }

  private static void installResponsiveMotion(Node node, double hoverScale, double hoverTranslateY,
      double baseOpacity, double pressedScale) {
    installResponsiveMotion(node, hoverScale, hoverTranslateY, baseOpacity, pressedScale, null);
  }

  private static void installResponsiveMotion(Node node, double hoverScale, double hoverTranslateY,
      double baseOpacity, double pressedScale, ObservableBooleanValue suppressMotion) {
    node.setOpacity(baseOpacity);
    ChangeListener<Boolean> listener = (obs, oldValue, newValue) ->
        animate(node, hoverScale, hoverTranslateY, baseOpacity, pressedScale, suppressMotion);
    node.hoverProperty().addListener(listener);
    node.pressedProperty().addListener(listener);
    if (suppressMotion != null) {
      suppressMotion.addListener((obs, oldValue, newValue) ->
          animate(node, hoverScale, hoverTranslateY, baseOpacity, pressedScale, suppressMotion));
    }
    animate(node, hoverScale, hoverTranslateY, baseOpacity, pressedScale, suppressMotion);
  }

  private static void animate(Node node, double hoverScale, double hoverTranslateY,
      double baseOpacity, double pressedScale, ObservableBooleanValue suppressMotion) {
    boolean suppressed = suppressMotion != null && suppressMotion.get();
    boolean pressed = node.isPressed();
    boolean hovered = node.isHover();
    double scale = suppressed ? 1.0 : pressed ? pressedScale : hovered ? hoverScale : 1.0;
    double opacity = suppressed ? 1.0 : hovered ? 1.0 : baseOpacity;
    double translateY = suppressed ? 0.0 : hovered && !pressed ? hoverTranslateY : 0.0;

    Timeline timeline = (Timeline) node.getProperties().get(MOTION_TIMELINE_KEY);
    if (timeline != null) {
      timeline.stop();
    }

    timeline = new Timeline(
        new KeyFrame(
            Duration.millis(140),
            new KeyValue(node.scaleXProperty(), scale, Interpolator.EASE_BOTH),
            new KeyValue(node.scaleYProperty(), scale, Interpolator.EASE_BOTH),
            new KeyValue(node.opacityProperty(), opacity, Interpolator.EASE_BOTH),
            new KeyValue(node.translateYProperty(), translateY, Interpolator.EASE_BOTH)));
    node.getProperties().put(MOTION_TIMELINE_KEY, timeline);
    timeline.play();
  }
}
