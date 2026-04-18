package com.conote.client.model;

import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ChecklistItemModel {
  private final StringProperty text = new SimpleStringProperty(this, "text", "");
  private final BooleanProperty checked = new SimpleBooleanProperty(this, "checked", false);

  public ChecklistItemModel(String text, boolean checked) {
    setText(text);
    setChecked(checked);
  }

  public String getText() {
    return text.get();
  }

  public void setText(String value) {
    text.set(value == null ? "" : value);
  }

  public StringProperty textProperty() {
    return text;
  }

  public boolean isChecked() {
    return checked.get();
  }

  public void setChecked(boolean value) {
    checked.set(value);
  }

  public BooleanProperty checkedProperty() {
    return checked;
  }

  public Observable[] extractor() {
    return new Observable[] {text, checked};
  }
}
