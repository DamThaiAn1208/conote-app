package com.conote.client.model;

public enum NoteColor {
  DEFAULT("default", "#fff8e1", "#252834"),
  AMBER("amber", "#fef3c7", "#4b3606"),
  RED("red", "#fee2e2", "#4a1d1f"),
  GREEN("green", "#dcfce7", "#173127"),
  BLUE("blue", "#dbeafe", "#162c52"),
  PURPLE("purple", "#ede9fe", "#33245d"),
  SLATE("slate", "#e2e8f0", "#243244");

  private final String cssName;
  private final String lightSurface;
  private final String darkSurface;

  NoteColor(String cssName, String lightSurface, String darkSurface) {
    this.cssName = cssName;
    this.lightSurface = lightSurface;
    this.darkSurface = darkSurface;
  }

  public String cssName() {
    return cssName;
  }

  public String surfaceForTheme(AppTheme theme) {
    return theme == AppTheme.DARK ? darkSurface : lightSurface;
  }
}
