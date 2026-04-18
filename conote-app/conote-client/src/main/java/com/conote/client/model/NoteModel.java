package com.conote.client.model;

import com.conote.common.enums.NoteType;
import java.util.UUID;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

public class NoteModel {
  private final String id = UUID.randomUUID().toString();
  private final ObjectProperty<NoteType> type = new SimpleObjectProperty<>(this, "type");
  private final StringProperty title = new SimpleStringProperty(this, "title", "");
  private final StringProperty content = new SimpleStringProperty(this, "content", "");
  private final ObjectProperty<NoteColor> color = new SimpleObjectProperty<>(this, "color", NoteColor.DEFAULT);
  private final BooleanProperty pinned = new SimpleBooleanProperty(this, "pinned", false);
  private final LongProperty createdAt = new SimpleLongProperty(this, "createdAt", 0L);
  private final LongProperty updatedAt = new SimpleLongProperty(this, "updatedAt", 0L);
  private final IntegerProperty revision = new SimpleIntegerProperty(this, "revision", 0);
  private final ObservableList<String> tags = FXCollections.observableArrayList();
  private final ObservableList<ChecklistItemModel> checklistItems =
      FXCollections.observableArrayList(ChecklistItemModel::extractor);
  private final ObservableList<ShareMember> shareMembers = FXCollections.observableArrayList();

  public NoteModel(
      NoteType type,
      String title,
      String content,
      NoteColor color,
      boolean pinned,
      long createdAt,
      long updatedAt) {
    setType(type);
    setTitle(title);
    setContent(content);
    setColor(color);
    setPinned(pinned);
    setCreatedAt(createdAt);
    setUpdatedAt(updatedAt);
    installChangeTracking();
  }

  public String getId() {
    return id;
  }

  public NoteType getType() {
    return type.get();
  }

  public void setType(NoteType value) {
    type.set(value == null ? NoteType.TEXT : value);
  }

  public ObjectProperty<NoteType> typeProperty() {
    return type;
  }

  public String getTitle() {
    return title.get();
  }

  public void setTitle(String value) {
    title.set(value == null ? "" : value);
  }

  public StringProperty titleProperty() {
    return title;
  }

  public String getContent() {
    return content.get();
  }

  public void setContent(String value) {
    content.set(value == null ? "" : value);
  }

  public StringProperty contentProperty() {
    return content;
  }

  public NoteColor getColor() {
    return color.get();
  }

  public void setColor(NoteColor value) {
    color.set(value == null ? NoteColor.DEFAULT : value);
  }

  public ObjectProperty<NoteColor> colorProperty() {
    return color;
  }

  public boolean isPinned() {
    return pinned.get();
  }

  public void setPinned(boolean value) {
    pinned.set(value);
  }

  public BooleanProperty pinnedProperty() {
    return pinned;
  }

  public long getCreatedAt() {
    return createdAt.get();
  }

  public void setCreatedAt(long value) {
    createdAt.set(value);
  }

  public LongProperty createdAtProperty() {
    return createdAt;
  }

  public long getUpdatedAt() {
    return updatedAt.get();
  }

  public void setUpdatedAt(long value) {
    updatedAt.set(value);
  }

  public LongProperty updatedAtProperty() {
    return updatedAt;
  }

  public int getRevision() {
    return revision.get();
  }

  public IntegerProperty revisionProperty() {
    return revision;
  }

  public ObservableList<String> getTags() {
    return tags;
  }

  public ObservableList<ChecklistItemModel> getChecklistItems() {
    return checklistItems;
  }

  public ObservableList<ShareMember> getShareMembers() {
    return shareMembers;
  }

  public String getPreviewText() {
    String collapsed = getContent().replaceAll("\\s+", " ").trim();
    if (collapsed.isBlank()) {
      return "";
    }
    return collapsed.length() <= 120 ? collapsed : collapsed.substring(0, 117) + "...";
  }

  public void touch() {
    long now = System.currentTimeMillis();
    if (updatedAt.get() != now) {
      updatedAt.set(now);
    } else {
      bumpRevision();
    }
  }

  public Observable[] extractor() {
    return new Observable[] {type, title, content, color, pinned, createdAt, updatedAt, revision};
  }

  private void installChangeTracking() {
    type.addListener((obs, oldValue, newValue) -> bumpRevision());
    title.addListener((obs, oldValue, newValue) -> bumpRevision());
    content.addListener((obs, oldValue, newValue) -> bumpRevision());
    color.addListener((obs, oldValue, newValue) -> bumpRevision());
    pinned.addListener((obs, oldValue, newValue) -> bumpRevision());
    createdAt.addListener((obs, oldValue, newValue) -> bumpRevision());
    updatedAt.addListener((obs, oldValue, newValue) -> bumpRevision());
    tags.addListener((ListChangeListener<String>) change -> bumpRevision());
    checklistItems.addListener((ListChangeListener<ChecklistItemModel>) change -> bumpRevision());
    shareMembers.addListener((ListChangeListener<ShareMember>) change -> bumpRevision());
  }

  private void bumpRevision() {
    revision.set(revision.get() + 1);
  }
}
