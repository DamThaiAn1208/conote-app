package com.conote.client.util;

import javafx.scene.control.Labeled;
import org.kordamp.ikonli.javafx.FontIcon;

public final class IconFactory {
  private IconFactory() {
  }

  public static FontIcon icon(String literal, int size, String... styleClasses) {
    FontIcon icon = new FontIcon();
    icon.setIconLiteral(literal);
    icon.setIconSize(size);
    icon.getStyleClass().add("ui-icon");
    if (styleClasses != null) {
      icon.getStyleClass().addAll(styleClasses);
    }
    return icon;
  }

  public static void apply(Labeled labeled, String literal, int size, String... styleClasses) {
    labeled.setGraphic(icon(literal, size, styleClasses));
  }
}
