package com.conote.client.model;

public enum AppTheme {
  LIGHT("theme-light"),
  DARK("theme-dark");

  private final String cssClass;

  AppTheme(String cssClass) {
    this.cssClass = cssClass;
  }

  public String cssClass() {
    return cssClass;
  }
}
