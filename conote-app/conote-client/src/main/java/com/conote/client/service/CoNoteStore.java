package com.conote.client.service;

import com.conote.client.model.AppTheme;
import com.conote.client.model.ChecklistItemModel;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.common.enums.NoteType;
import com.conote.common.enums.SharePermission;
import com.conote.client.model.ShareMember;
import com.conote.client.model.SortMode;
import com.conote.client.util.RichTextContentCodec;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.util.Duration;

public class CoNoteStore {
  private static final Duration EDIT_TOUCH_DELAY = Duration.millis(320);

  private final ObservableList<NoteModel> notes =
      FXCollections.observableArrayList(note -> note.extractor());
  private final FilteredList<NoteModel> filteredNotes = new FilteredList<>(notes);
  private final SortedList<NoteModel> visibleNotes = new SortedList<>(filteredNotes);
  private final StringProperty searchQuery = new SimpleStringProperty("");
  private final ObjectProperty<SortMode> sortMode = new SimpleObjectProperty<>(SortMode.NEWEST);
  private final ObjectProperty<AppTheme> theme = new SimpleObjectProperty<>(AppTheme.LIGHT);
  private final ObjectProperty<String> expandedNoteId = new SimpleObjectProperty<>();
  private final BooleanProperty loading = new SimpleBooleanProperty(true);
  private final BooleanProperty dockOnDesktop = new SimpleBooleanProperty(true);
  private final ObservableSet<String> selectedTags = FXCollections.observableSet();
  private final ObservableSet<NoteColor> selectedColors = FXCollections.observableSet();
  private final Map<String, PauseTransition> pendingEditTouches = new HashMap<>();

  public CoNoteStore() {
    bindFiltering();
    bindSorting();
  }

  private void bindFiltering() {
    filteredNotes.setPredicate(this::matchesFilters);
    searchQuery.addListener((obs, oldValue, newValue) -> refreshFilters());
    selectedTags.addListener((javafx.collections.SetChangeListener<String>) change -> refreshFilters());
    selectedColors.addListener((javafx.collections.SetChangeListener<NoteColor>) change -> refreshFilters());
    notes.addListener((ListChangeListener<NoteModel>) change -> refreshFilters());
  }

  private void bindSorting() {
    visibleNotes.setComparator(createComparator(sortMode.get()));
    sortMode.addListener((obs, oldValue, newValue) -> visibleNotes.setComparator(createComparator(newValue)));
  }

  public void bootstrapDemoData() {
    loading.set(true);
    PauseTransition transition = new PauseTransition(Duration.millis(550));
    transition.setOnFinished(event -> {
      notes.setAll(buildDemoNotes());
      loading.set(false);
    });
    transition.play();
  }

  private List<NoteModel> buildDemoNotes() {
    long now = System.currentTimeMillis();

    NoteModel productKickoff = new NoteModel(
        NoteType.TEXT,
        "Meeting Notes: Q3 Planning",
        "Discussed goals for the next quarter.\n\nFocus on onboarding, activation and the sharing flow. Coordinate with marketing before Friday.",
        NoteColor.AMBER,
        true,
        now - (long) Duration.hours(5).toMillis(),
        now - (long) Duration.minutes(14).toMillis());
    productKickoff.getTags().addAll(List.of("work", "meeting", "roadmap"));
    productKickoff.getShareMembers().addAll(List.of(
        new ShareMember("Alex Designer", "alex@noteflow.app", SharePermission.EDIT),
        new ShareMember("Jules PM", "jules@company.com", SharePermission.VIEW)));

    NoteModel groceries = new NoteModel(
        NoteType.CHECKLIST,
        "Grocery List",
        "",
        NoteColor.GREEN,
        false,
        now - (long) Duration.hours(24).toMillis(),
        now - (long) Duration.hours(3).toMillis());
    groceries.getTags().addAll(List.of("personal"));
    groceries.getChecklistItems().addAll(List.of(
        new ChecklistItemModel("Milk", true),
        new ChecklistItemModel("Eggs", false),
        new ChecklistItemModel("Bread", false),
        new ChecklistItemModel("Coffee beans", false)));
    groceries.getShareMembers().add(
        new ShareMember("Alex Designer", "alex@noteflow.app", SharePermission.EDIT));

    NoteModel designNotes = new NoteModel(
        NoteType.TEXT,
        "Focused main window",
        "Keep the main window centered on search, filters, create note and the accordion list. Open the full note only from the dedicated button.",
        NoteColor.BLUE,
        false,
        now - (long) Duration.hours(72).toMillis(),
        now - (long) Duration.hours(48).toMillis());
    designNotes.getTags().addAll(List.of("design", "window"));
    designNotes.getShareMembers().add(
        new ShareMember("Alex Designer", "alex@noteflow.app", SharePermission.EDIT));

    return List.of(productKickoff, groceries, designNotes);
  }

  private boolean matchesFilters(NoteModel note) {
    String query = searchQuery.get() == null ? "" : searchQuery.get().trim().toLowerCase(Locale.ROOT);
    if (!query.isBlank()) {
      boolean matchText = safe(note.getTitle()).contains(query)
          || safe(note.getPlainTextContent()).contains(query)
          || note.getChecklistItems().stream()
              .map(ChecklistItemModel::getText)
              .map(this::safe)
              .anyMatch(text -> text.contains(query));
      if (!matchText) {
        return false;
      }
    }

    if (!selectedColors.isEmpty() && !selectedColors.contains(note.getColor())) {
      return false;
    }

    if (!selectedTags.isEmpty()) {
      boolean tagMatch = note.getTags().stream().anyMatch(selectedTags::contains);
      if (!tagMatch) {
        return false;
      }
    }
    return true;
  }

  private Comparator<NoteModel> createComparator(SortMode mode) {
    Comparator<NoteModel> byUpdated = Comparator.comparingLong(NoteModel::getUpdatedAt);
    if (mode == SortMode.NEWEST) {
      byUpdated = byUpdated.reversed();
    }
    return Comparator
        .comparing(NoteModel::isPinned, Comparator.reverseOrder())
        .thenComparing(byUpdated);
  }

  private String safe(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  public NoteModel createNote(NoteType type) {
    long now = System.currentTimeMillis();
    NoteModel note = new NoteModel(
        type,
        type == NoteType.TEXT ? "Untitled Text Note" : "Untitled Checklist Note",
        "",
        NoteColor.DEFAULT,
        false,
        now,
        now);
    note.getShareMembers().add(new ShareMember("Alex Designer", "alex@noteflow.app", SharePermission.EDIT));
    if (type == NoteType.CHECKLIST) {
      note.getChecklistItems().add(new ChecklistItemModel("", false));
    }
    notes.add(0, note);
    expandedNoteId.set(note.getId());
    return note;
  }

  public void deleteNote(NoteModel note) {
    cancelPendingTouch(note);
    notes.remove(note);
    if (note != null && note.getId().equals(expandedNoteId.get())) {
      expandedNoteId.set(null);
    }
  }

  public void refreshFilters() {
    filteredNotes.setPredicate(this::matchesFilters);
  }

  public void setSearchQuery(String value) {
    searchQuery.set(value);
  }

  public void toggleTagFilter(String tag) {
    if (selectedTags.contains(tag)) {
      selectedTags.remove(tag);
    } else {
      selectedTags.add(tag);
    }
  }

  public void toggleColorFilter(NoteColor color) {
    if (selectedColors.contains(color)) {
      selectedColors.remove(color);
    } else {
      selectedColors.add(color);
    }
  }

  public void setSortMode(SortMode value) {
    sortMode.set(value);
  }

  public void setTheme(AppTheme value) {
    theme.set(value == null ? AppTheme.LIGHT : value);
  }

  public void setExpandedNoteId(String noteId) {
    expandedNoteId.set(noteId);
  }

  public void updateTitle(NoteModel note, String value) {
    if (note != null && !safeEquals(note.getTitle(), value)) {
      note.setTitle(value);
      scheduleTouch(note);
    }
  }

  public void updateContent(NoteModel note, String value) {
    if (note != null && !safeEquals(note.getContent(), value)) {
      note.setContent(value);
      scheduleTouch(note);
    }
  }

  public void updatePlainTextContent(NoteModel note, String value) {
    updateContent(note, RichTextContentCodec.plainText(value));
  }

  public void togglePin(NoteModel note) {
    if (note != null) {
      note.setPinned(!note.isPinned());
      touchImmediately(note);
    }
  }

  public void setColor(NoteModel note, NoteColor color) {
    if (note != null && note.getColor() != color) {
      note.setColor(color);
      touchImmediately(note);
    }
  }

  public void addTag(NoteModel note, String tag) {
    if (note == null) {
      return;
    }
    String normalized = tag == null ? "" : tag.trim();
    if (!normalized.isBlank() && !note.getTags().contains(normalized)) {
      note.getTags().add(normalized);
      touchImmediately(note);
    }
  }

  public void removeTag(NoteModel note, String tag) {
    if (note != null && note.getTags().remove(tag)) {
      touchImmediately(note);
    }
  }

  public void toggleChecklistItem(NoteModel note, ChecklistItemModel item) {
    if (note != null && item != null) {
      item.setChecked(!item.isChecked());
      touchImmediately(note);
    }
  }

  public void updateChecklistItemText(NoteModel note, ChecklistItemModel item, String value) {
    if (note != null && item != null && !safeEquals(item.getText(), value)) {
      item.setText(value);
      scheduleTouch(note);
    }
  }

  public ChecklistItemModel addChecklistItem(NoteModel note) {
    ChecklistItemModel item = new ChecklistItemModel("", false);
    if (note != null) {
      note.getChecklistItems().add(item);
      touchImmediately(note);
    }
    return item;
  }

  public void removeChecklistItem(NoteModel note, ChecklistItemModel item) {
    if (note != null && item != null && note.getChecklistItems().remove(item)) {
      touchImmediately(note);
    }
  }

  public void addShareMember(NoteModel note, String email, SharePermission permission) {
    if (note == null || email == null || email.isBlank()) {
      return;
    }
    String normalized = email.trim();
    boolean exists = note.getShareMembers().stream()
        .anyMatch(member -> member.getEmail().equalsIgnoreCase(normalized));
    if (!exists) {
      String displayName = normalized.substring(0, normalized.indexOf('@') > 0 ? normalized.indexOf('@') : normalized.length());
      note.getShareMembers().add(new ShareMember(displayName, normalized, permission));
      touchImmediately(note);
    }
  }

  public void removeShareMember(NoteModel note, ShareMember member) {
    if (note != null && member != null) {
      note.getShareMembers().remove(member);
      touchImmediately(note);
    }
  }

  public Set<String> collectAvailableTags() {
    Set<String> result = new LinkedHashSet<>();
    for (NoteModel note : notes) {
      result.addAll(note.getTags());
    }
    return result;
  }

  public ObservableList<NoteModel> getNotes() {
    return notes;
  }

  public SortedList<NoteModel> getVisibleNotes() {
    return visibleNotes;
  }

  public StringProperty searchQueryProperty() {
    return searchQuery;
  }

  public ObjectProperty<SortMode> sortModeProperty() {
    return sortMode;
  }

  public AppTheme getTheme() {
    return theme.get();
  }

  public ObjectProperty<AppTheme> themeProperty() {
    return theme;
  }

  public ObjectProperty<String> expandedNoteIdProperty() {
    return expandedNoteId;
  }

  public BooleanProperty loadingProperty() {
    return loading;
  }

  public BooleanProperty dockOnDesktopProperty() {
    return dockOnDesktop;
  }

  public ObservableSet<String> getSelectedTags() {
    return selectedTags;
  }

  public ObservableSet<NoteColor> getSelectedColors() {
    return selectedColors;
  }

  private boolean safeEquals(String left, String right) {
    return (left == null ? "" : left).equals(right == null ? "" : right);
  }

  private void scheduleTouch(NoteModel note) {
    if (note == null) {
      return;
    }

    cancelPendingTouch(note);
    PauseTransition transition = new PauseTransition(EDIT_TOUCH_DELAY);
    transition.setOnFinished(event -> {
      pendingEditTouches.remove(note.getId());
      note.touch();
    });
    pendingEditTouches.put(note.getId(), transition);
    transition.playFromStart();
  }

  private void touchImmediately(NoteModel note) {
    if (note == null) {
      return;
    }
    cancelPendingTouch(note);
    note.touch();
  }

  private void cancelPendingTouch(NoteModel note) {
    if (note == null) {
      return;
    }

    PauseTransition pendingTransition = pendingEditTouches.remove(note.getId());
    if (pendingTransition != null) {
      pendingTransition.stop();
    }
  }
}
