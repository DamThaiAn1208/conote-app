package com.conote.client.cache;

import java.util.ArrayList;
import java.util.List;

public class UiState {
  private double windowWidth = 500.0;
  private double windowHeight = 760.0;
  private String theme = "LIGHT";
  private String latestSearchKeyword = "";
  private String selectedFilter = "";
  private String sortMode = "NEWEST";
  private List<String> selectedTags = new ArrayList<>();
  private List<String> selectedColors = new ArrayList<>();

  public double getWindowWidth() {
    return windowWidth;
  }

  public void setWindowWidth(double windowWidth) {
    this.windowWidth = windowWidth;
  }

  public double getWindowHeight() {
    return windowHeight;
  }

  public void setWindowHeight(double windowHeight) {
    this.windowHeight = windowHeight;
  }

  public String getTheme() {
    return theme;
  }

  public void setTheme(String theme) {
    this.theme = theme == null ? "LIGHT" : theme;
  }

  public String getLatestSearchKeyword() {
    return latestSearchKeyword;
  }

  public void setLatestSearchKeyword(String latestSearchKeyword) {
    this.latestSearchKeyword = latestSearchKeyword == null ? "" : latestSearchKeyword;
  }

  public String getSelectedFilter() {
    return selectedFilter;
  }

  public void setSelectedFilter(String selectedFilter) {
    this.selectedFilter = selectedFilter == null ? "" : selectedFilter;
  }

  public String getSortMode() {
    return sortMode;
  }

  public void setSortMode(String sortMode) {
    this.sortMode = sortMode == null ? "NEWEST" : sortMode;
  }

  public List<String> getSelectedTags() {
    return selectedTags;
  }

  public void setSelectedTags(List<String> selectedTags) {
    this.selectedTags = selectedTags == null ? new ArrayList<>() : new ArrayList<>(selectedTags);
  }

  public List<String> getSelectedColors() {
    return selectedColors;
  }

  public void setSelectedColors(List<String> selectedColors) {
    this.selectedColors = selectedColors == null ? new ArrayList<>() : new ArrayList<>(selectedColors);
  }
}
