package com.conote.client.service;

import com.conote.client.cache.ChecklistContentCodec;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.cache.JsonStore;
import com.conote.client.cache.NoteCacheStore;
import com.conote.client.cache.PendingSyncItem;
import com.conote.client.cache.SyncActionType;
import com.conote.client.cache.SyncQueueStore;
import com.conote.client.cache.UiState;
import com.conote.client.cache.UiStateStore;
import com.conote.client.model.AppTheme;
import com.conote.client.model.ChecklistItemModel;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.client.model.ShareMember;
import com.conote.client.model.SortMode;
import com.conote.client.util.RichTextContentCodec;
import com.conote.common.enums.NoteType;
import com.conote.common.enums.SharePermission;
import com.conote.common.enums.ShareStatus;
import com.conote.common.model.Note;
import com.conote.common.model.Share;
import com.conote.common.model.Tag;
import com.conote.common.model.User;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import javafx.collections.SetChangeListener;
import javafx.collections.transformation.FilteredList;

public class CoNoteStore {
  private static final double DEFAULT_WINDOW_WIDTH = 480.0;
  private static final double DEFAULT_WINDOW_HEIGHT = 760.0;
  private static final String DEFAULT_LOCAL_USER_NAME = "CoNote Local User";
  private static final String DEFAULT_LOCAL_USER_EMAIL = "local@conote.app";

  private final ObservableList<NoteModel> notes =
      FXCollections.observableArrayList(note -> note.extractor());
  private final FilteredList<NoteModel> visibleNotes = new FilteredList<>(notes);
  private final StringProperty searchQuery = new SimpleStringProperty("");
  private final ObjectProperty<SortMode> sortMode = new SimpleObjectProperty<>(SortMode.NEWEST);
  private final ObjectProperty<AppTheme> theme = new SimpleObjectProperty<>(AppTheme.LIGHT);
  private final ObjectProperty<String> expandedNoteId = new SimpleObjectProperty<>();
  private final BooleanProperty loading = new SimpleBooleanProperty(true);
  private final BooleanProperty dockOnDesktop = new SimpleBooleanProperty(true);
  private final ObservableSet<String> selectedTags = FXCollections.observableSet();
  private final ObservableSet<NoteColor> selectedColors = FXCollections.observableSet();

  private final JsonStore jsonStore;
  private final NoteCacheStore noteCacheStore;
  private final UiStateStore uiStateStore;
  private final SyncQueueStore syncQueueStore;
  private final Map<String, Note> noteEntitiesById = new LinkedHashMap<>();

  private UiState uiState;

  public CoNoteStore() {
    ClientStoragePaths.init();
    jsonStore = new JsonStore();
    noteCacheStore = new NoteCacheStore(jsonStore);
    uiStateStore = new UiStateStore(jsonStore);
    syncQueueStore = new SyncQueueStore(jsonStore);

    bindFiltering();
    bindSorting();
    loadUiState();
    loadNotesFromLocalCache();
    bindUiStatePersistence();
  }

  private void bindFiltering() {
    visibleNotes.setPredicate(this::matchesFilters);
    searchQuery.addListener((obs, oldValue, newValue) -> refreshFilters());
    selectedTags.addListener((SetChangeListener<String>) change -> refreshFilters());
    selectedColors.addListener((SetChangeListener<NoteColor>) change -> refreshFilters());
    notes.addListener((ListChangeListener<NoteModel>) change -> refreshFilters());
  }

  private void bindSorting() {
    sortMode.addListener((obs, oldValue, newValue) -> applySortModeOrdering(newValue));
  }

  private void loadUiState() {
    uiState = uiStateStore.load();
    theme.set(resolveTheme(uiState.getTheme()));
    searchQuery.set(uiState.getLatestSearchKeyword() == null ? "" : uiState.getLatestSearchKeyword());
    sortMode.set(resolveSortMode(uiState.getSortMode()));

    selectedTags.clear();
    if (uiState.getSelectedTags() != null) {
      selectedTags.addAll(uiState.getSelectedTags());
    }

    selectedColors.clear();
    if (uiState.getSelectedColors() != null) {
      for (String colorName : uiState.getSelectedColors()) {
        NoteColor color = findNoteColor(colorName);
        if (color != null) {
          selectedColors.add(color);
        }
      }
    }
  }

  private void loadNotesFromLocalCache() {
    loading.set(true);
    noteEntitiesById.clear();

    List<NoteModel> loadedModels = new ArrayList<>();
    for (Note note : noteCacheStore.findAll()) {
      if (note == null || Boolean.TRUE.equals(note.getDeleted())) {
        continue;
      }
      noteEntitiesById.put(note.getNoteId(), note);
      loadedModels.add(toNoteModel(note));
    }

    notes.setAll(loadedModels);
    applySortModeOrdering(sortMode.get());
    loading.set(false);
  }

  private void bindUiStatePersistence() {
    searchQuery.addListener((obs, oldValue, newValue) -> saveUiState());
    sortMode.addListener((obs, oldValue, newValue) -> saveUiState());
    theme.addListener((obs, oldValue, newValue) -> saveUiState());
    selectedTags.addListener((SetChangeListener<String>) change -> saveUiState());
    selectedColors.addListener((SetChangeListener<NoteColor>) change -> saveUiState());
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
    Comparator<NoteModel> byCreatedAt = Comparator.comparingLong(NoteModel::getCreatedAt);
    if (mode == SortMode.NEWEST) {
      byCreatedAt = byCreatedAt.reversed();
    }
    return Comparator
        .comparing(NoteModel::isPinned, Comparator.reverseOrder())
        .thenComparing(byCreatedAt)
        .thenComparing(NoteModel::getId);
  }

  private String safe(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  public NoteModel createNote(NoteType type) {
    Note note = new Note();
    note.setOwner(buildDefaultOwner());
    note.setTitle(type == NoteType.CHECKLIST ? "Untitled Checklist Note" : "Untitled Text Note");
    note.setContent(type == NoteType.CHECKLIST
        ? ChecklistContentCodec.encode(List.of(new ChecklistItemModel("", false)))
        : "");
    note.setColor(NoteColor.DEFAULT.cssName());
    note.setPinned(false);
    note.setDeleted(false);
    note.setShareStatus(ShareStatus.PRIVATE);
    note.setNoteType(type == null ? NoteType.TEXT : type);
    note.setShares(new ArrayList<>());
    note.setTags(new ArrayList<>());

    Note saved = noteCacheStore.save(note);
    noteEntitiesById.put(saved.getNoteId(), saved);

    NoteModel model = toNoteModel(saved);
    insertCreatedNote(model);
    expandedNoteId.set(model.getId());
    enqueueSync(saved, SyncActionType.CREATE_NOTE);
    return model;
  }

  public void deleteNote(NoteModel note) {
    if (note == null) {
      return;
    }

    Note deletedSnapshot = toNoteEntity(note);
    deletedSnapshot.setDeleted(true);

    notes.remove(note);
    noteEntitiesById.remove(note.getId());
    noteCacheStore.deleteById(note.getId());
    if (note.getId().equals(expandedNoteId.get())) {
      expandedNoteId.set(null);
    }

    enqueueSync(deletedSnapshot, SyncActionType.DELETE_NOTE);
  }

  public void refreshFilters() {
    visibleNotes.setPredicate(this::matchesFilters);
  }

  public void setSearchQuery(String value) {
    searchQuery.set(value == null ? "" : value);
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
    sortMode.set(value == null ? SortMode.NEWEST : value);
  }

  public boolean hasActiveFilters() {
    SortMode activeSort = sortMode.get() == null ? SortMode.NEWEST : sortMode.get();
    return activeSort != SortMode.NEWEST
        || !selectedTags.isEmpty()
        || !selectedColors.isEmpty();
  }

  public void clearAllFilters() {
    setSortMode(SortMode.NEWEST);
    selectedTags.clear();
    selectedColors.clear();
    refreshFilters();
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
      persistNote(note, SyncActionType.UPDATE_NOTE);
    }
  }

  public void updateContent(NoteModel note, String value) {
    if (note != null && !safeEquals(note.getContent(), value)) {
      note.setContent(value);
      persistNote(note, SyncActionType.UPDATE_NOTE);
    }
  }

  public void updatePlainTextContent(NoteModel note, String value) {
    updateContent(note, RichTextContentCodec.plainText(value));
  }

  public void updateNoteType(NoteModel note, NoteType type) {
    if (note == null) {
      return;
    }

    NoteType nextType = type == null ? NoteType.TEXT : type;
    if (note.getType() == nextType) {
      return;
    }

    if (nextType == NoteType.CHECKLIST) {
      List<ChecklistItemModel> convertedItems = checklistItemsFromText(note.getPlainTextContent());
      note.getChecklistItems().setAll(convertedItems.isEmpty()
          ? List.of(new ChecklistItemModel("", false))
          : convertedItems);
      note.setContent(ChecklistContentCodec.toPlainText(note.getChecklistItems()));
    } else {
      note.setContent(RichTextContentCodec.plainText(
          ChecklistContentCodec.toPlainText(note.getChecklistItems())));
    }

    note.setType(nextType);
    persistNote(note, SyncActionType.UPDATE_NOTE);
  }

  public void togglePin(NoteModel note) {
    if (note != null) {
      note.setPinned(!note.isPinned());
      moveNoteForPinState(note);
      persistNote(
          note,
          note.isPinned() ? SyncActionType.PIN_NOTE : SyncActionType.UNPIN_NOTE,
          true);
    }
  }

  public void setColor(NoteModel note, NoteColor color) {
    if (note != null && note.getColor() != color) {
      note.setColor(color);
      persistNote(note, SyncActionType.UPDATE_NOTE);
    }
  }

  public void addTag(NoteModel note, String tag) {
    if (note == null) {
      return;
    }

    String normalized = tag == null ? "" : tag.trim();
    if (!normalized.isBlank() && !note.getTags().contains(normalized)) {
      note.getTags().add(normalized);
      persistNote(note, SyncActionType.UPDATE_NOTE);
    }
  }

  public void removeTag(NoteModel note, String tag) {
    if (note != null && note.getTags().remove(tag)) {
      persistNote(note, SyncActionType.UPDATE_NOTE);
    }
  }

  public void toggleChecklistItem(NoteModel note, ChecklistItemModel item) {
    if (note != null && item != null) {
      item.setChecked(!item.isChecked());
      persistNote(note, SyncActionType.UPDATE_NOTE);
    }
  }

  public void updateChecklistItemText(NoteModel note, ChecklistItemModel item, String value) {
    if (note != null && item != null && !safeEquals(item.getText(), value)) {
      item.setText(value);
      persistNote(note, SyncActionType.UPDATE_NOTE);
    }
  }

  public ChecklistItemModel addChecklistItem(NoteModel note) {
    ChecklistItemModel item = new ChecklistItemModel("", false);
    if (note != null) {
      note.getChecklistItems().add(item);
      persistNote(note, SyncActionType.UPDATE_NOTE);
    }
    return item;
  }

  public void removeChecklistItem(NoteModel note, ChecklistItemModel item) {
    if (note != null && item != null && note.getChecklistItems().remove(item)) {
      persistNote(note, SyncActionType.UPDATE_NOTE);
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
      note.getShareMembers().add(new ShareMember(resolveDisplayName(null, normalized), normalized, permission));
      persistNote(note, SyncActionType.UPDATE_NOTE);
    }
  }

  public void removeShareMember(NoteModel note, ShareMember member) {
    if (note != null && member != null && note.getShareMembers().remove(member)) {
      persistNote(note, SyncActionType.UPDATE_NOTE);
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

  public ObservableList<NoteModel> getVisibleNotes() {
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

  public double getWindowWidth() {
    return DEFAULT_WINDOW_WIDTH;
  }

  public double getWindowHeight() {
    return DEFAULT_WINDOW_HEIGHT;
  }

  public void updateWindowSize(double width, double height) {
    if (uiState == null) {
      uiState = new UiState();
    }

    boolean changed = false;
    if (width > 0 && Double.compare(width, uiState.getWindowWidth()) != 0) {
      uiState.setWindowWidth(width);
      changed = true;
    }
    if (height > 0 && Double.compare(height, uiState.getWindowHeight()) != 0) {
      uiState.setWindowHeight(height);
      changed = true;
    }
    if (changed) {
      saveUiState();
    }
  }

  private void persistNote(NoteModel noteModel, SyncActionType actionType) {
    persistNote(noteModel, actionType, false);
  }

  private void persistNote(NoteModel noteModel, SyncActionType actionType, boolean preserveUpdatedAt) {
    Note persisted = noteCacheStore.save(toNoteEntity(noteModel), preserveUpdatedAt);
    noteEntitiesById.put(persisted.getNoteId(), persisted);
    noteModel.setCreatedAt(toEpochMillis(persisted.getCreatedAt()));
    noteModel.setUpdatedAt(toEpochMillis(persisted.getUpdatedAt()));
    enqueueSync(persisted, actionType);
  }

  private void enqueueSync(Note note, SyncActionType actionType) {
    if (note == null || note.getNoteId() == null || note.getNoteId().isBlank()) {
      return;
    }

    syncQueueStore.add(new PendingSyncItem(
        null,
        actionType,
        note.getNoteId(),
        jsonStore.toJson(note),
        0,
        null,
        null));
  }

  private Note toNoteEntity(NoteModel model) {
    Note note = noteEntitiesById.get(model.getId());
    if (note == null) {
      note = new Note();
      note.setNoteId(model.getId());
    }

    User owner = note.getOwner() == null ? buildDefaultOwner() : note.getOwner();
    note.setOwner(owner);
    note.setTitle(model.getTitle());
    note.setContent(model.getType() == NoteType.CHECKLIST
        ? ChecklistContentCodec.encode(new ArrayList<>(model.getChecklistItems()))
        : model.getContent());
    note.setColor((model.getColor() == null ? NoteColor.DEFAULT : model.getColor()).cssName());
    note.setPinned(model.isPinned());
    note.setDeleted(false);
    note.setShareStatus(hasSharedMembers(model.getShareMembers(), owner) ? ShareStatus.SHARED : ShareStatus.PRIVATE);
    note.setNoteType(model.getType() == null ? NoteType.TEXT : model.getType());
    note.setCreatedAt(resolveCreatedAt(note, model));
    note.setUpdatedAt(resolveUpdatedAt(model));
    note.setTags(buildTags(note, model.getTags(), owner));
    note.setShares(buildShares(note, model.getShareMembers(), owner));
    return note;
  }

  private NoteModel toNoteModel(Note note) {
    NoteType type = note.getNoteType() == null ? NoteType.TEXT : note.getNoteType();
    String content = type == NoteType.TEXT
        ? safeText(note.getContent())
        : ChecklistContentCodec.toPlainText(note.getContent());

    NoteModel model = new NoteModel(
        note.getNoteId(),
        type,
        safeText(note.getTitle()),
        content,
        resolveNoteColor(note.getColor()),
        Boolean.TRUE.equals(note.getPinned()),
        toEpochMillis(note.getCreatedAt()),
        toEpochMillis(note.getUpdatedAt()));

    if (type == NoteType.CHECKLIST) {
      List<ChecklistItemModel> items = new ArrayList<>(ChecklistContentCodec.decode(note.getContent()));
      if (items.isEmpty()) {
        items.add(new ChecklistItemModel("", false));
      }
      model.getChecklistItems().setAll(items);
    }

    model.getTags().setAll(extractTagNames(note));
    model.getShareMembers().setAll(extractShareMembers(note));
    return model;
  }

  private void applySortModeOrdering(SortMode mode) {
    FXCollections.sort(notes, createComparator(mode));
  }

  private void insertCreatedNote(NoteModel note) {
    if (note == null) {
      return;
    }

    int targetIndex = sortMode.get() == SortMode.NEWEST ? firstUnpinnedIndex() : notes.size();
    notes.add(targetIndex, note);
  }

  private void moveNoteForPinState(NoteModel note) {
    int currentIndex = notes.indexOf(note);
    if (currentIndex < 0) {
      return;
    }

    notes.remove(currentIndex);
    int targetIndex = note.isPinned() ? 0 : firstUnpinnedIndex();
    notes.add(targetIndex, note);
  }

  private int firstUnpinnedIndex() {
    for (int index = 0; index < notes.size(); index++) {
      if (!notes.get(index).isPinned()) {
        return index;
      }
    }
    return notes.size();
  }

  private List<Tag> buildTags(Note note, List<String> tagNames, User owner) {
    Map<String, Tag> existingTagsByName = new LinkedHashMap<>();
    if (note.getTags() != null) {
      for (Tag existingTag : note.getTags()) {
        if (existingTag != null && existingTag.getTagName() != null) {
          existingTagsByName.put(tagKey(existingTag.getTagName()), existingTag);
        }
      }
    }

    List<Tag> tags = new ArrayList<>();
    Set<String> seenKeys = new LinkedHashSet<>();
    for (String rawTagName : tagNames) {
      String normalized = rawTagName == null ? "" : rawTagName.trim();
      if (normalized.isBlank()) {
        continue;
      }

      String key = tagKey(normalized);
      if (!seenKeys.add(key)) {
        continue;
      }

      Tag tag = existingTagsByName.get(key);
      if (tag == null) {
        tag = new Tag();
        tag.setCreatedAt(LocalDateTime.now());
      }
      if (tag.getUser() == null) {
        tag.setUser(owner);
      }
      tag.setTagName(normalized);
      if (tag.getNotes() == null) {
        tag.setNotes(new ArrayList<>());
      }
      tags.add(tag);
    }
    return tags;
  }

  private List<Share> buildShares(Note note, List<ShareMember> shareMembers, User owner) {
    Map<String, Share> existingSharesByEmail = new LinkedHashMap<>();
    if (note.getShares() != null) {
      for (Share existingShare : note.getShares()) {
        if (existingShare == null || existingShare.getSharedWith() == null) {
          continue;
        }

        String emailKey = emailKey(existingShare.getSharedWith().getEmail());
        if (emailKey != null) {
          existingSharesByEmail.put(emailKey, existingShare);
        }
      }
    }

    String ownerEmailKey = owner == null ? null : emailKey(owner.getEmail());
    List<Share> shares = new ArrayList<>();
    Set<String> seenKeys = new LinkedHashSet<>();
    for (ShareMember member : shareMembers) {
      if (member == null) {
        continue;
      }

      String memberEmailKey = emailKey(member.getEmail());
      if (memberEmailKey == null || memberEmailKey.equals(ownerEmailKey) || !seenKeys.add(memberEmailKey)) {
        continue;
      }

      Share share = existingSharesByEmail.get(memberEmailKey);
      if (share == null) {
        share = new Share();
      }

      share.setNote(note);
      share.setSharedBy(share.getSharedBy() == null ? owner : share.getSharedBy());
      share.setSharedWith(updateOrCreateUser(share.getSharedWith(), member.getDisplayName(), member.getEmail()));
      share.setPermission(member.getPermission() == null ? SharePermission.VIEW : member.getPermission());
      shares.add(share);
    }
    return shares;
  }

  private List<String> extractTagNames(Note note) {
    List<String> tagNames = new ArrayList<>();
    if (note.getTags() == null) {
      return tagNames;
    }

    for (Tag tag : note.getTags()) {
      if (tag == null || tag.getTagName() == null || tag.getTagName().isBlank()) {
        continue;
      }
      tagNames.add(tag.getTagName());
    }
    return tagNames;
  }

  private List<ShareMember> extractShareMembers(Note note) {
    List<ShareMember> members = new ArrayList<>();
    if (note.getOwner() != null && userHasIdentity(note.getOwner())) {
      members.add(new ShareMember(
          resolveDisplayName(note.getOwner().getFullName(), note.getOwner().getEmail()),
          safeText(note.getOwner().getEmail()),
          SharePermission.EDIT));
    }

    if (note.getShares() == null) {
      return members;
    }

    for (Share share : note.getShares()) {
      if (share == null || share.getSharedWith() == null) {
        continue;
      }
      members.add(new ShareMember(
          resolveDisplayName(share.getSharedWith().getFullName(), share.getSharedWith().getEmail()),
          safeText(share.getSharedWith().getEmail()),
          share.getPermission() == null ? SharePermission.VIEW : share.getPermission()));
    }
    return members;
  }

  private List<ChecklistItemModel> checklistItemsFromText(String plainText) {
    String normalized = plainText == null ? "" : plainText.replace("\r\n", "\n");
    if (normalized.isBlank()) {
      return List.of();
    }

    List<ChecklistItemModel> items = new ArrayList<>();
    for (String line : normalized.split("\\R", -1)) {
      items.add(new ChecklistItemModel(line, false));
    }
    return items;
  }

  private User buildDefaultOwner() {
    User owner = new User();
    owner.setUserName("local-user");
    owner.setEmail(DEFAULT_LOCAL_USER_EMAIL);
    owner.setFullName(DEFAULT_LOCAL_USER_NAME);
    owner.setActive(true);
    owner.setVerified(true);
    LocalDateTime now = LocalDateTime.now();
    owner.setCreatedAt(now);
    owner.setUpdatedAt(now);
    return owner;
  }

  private User updateOrCreateUser(User existingUser, String displayName, String email) {
    User user = existingUser == null ? new User() : existingUser;
    user.setFullName(resolveDisplayName(displayName, email));
    user.setEmail(email == null ? "" : email.trim());
    if (user.getUserName() == null || user.getUserName().isBlank()) {
      user.setUserName(resolveUserName(user.getEmail()));
    }
    return user;
  }

  private boolean hasSharedMembers(List<ShareMember> members, User owner) {
    String ownerEmailKey = owner == null ? null : emailKey(owner.getEmail());
    if (members == null || members.isEmpty()) {
      return false;
    }

    for (ShareMember member : members) {
      if (member == null) {
        continue;
      }
      String emailKey = emailKey(member.getEmail());
      if (emailKey != null && !emailKey.equals(ownerEmailKey)) {
        return true;
      }
    }
    return false;
  }

  private LocalDateTime resolveCreatedAt(Note note, NoteModel model) {
    if (note.getCreatedAt() != null) {
      return note.getCreatedAt();
    }
    if (model.getCreatedAt() > 0) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(model.getCreatedAt()), ZoneId.systemDefault());
    }
    return LocalDateTime.now();
  }

  private LocalDateTime resolveUpdatedAt(NoteModel model) {
    if (model.getUpdatedAt() > 0) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(model.getUpdatedAt()), ZoneId.systemDefault());
    }
    return LocalDateTime.now();
  }

  private long toEpochMillis(LocalDateTime value) {
    LocalDateTime safeValue = value == null ? LocalDateTime.now() : value;
    return safeValue.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  private NoteColor resolveNoteColor(String storedColor) {
    NoteColor color = findNoteColor(storedColor);
    return color == null ? NoteColor.DEFAULT : color;
  }

  private NoteColor findNoteColor(String storedColor) {
    if (storedColor == null || storedColor.isBlank()) {
      return null;
    }

    for (NoteColor color : NoteColor.values()) {
      if (color.cssName().equalsIgnoreCase(storedColor) || color.name().equalsIgnoreCase(storedColor)) {
        return color;
      }
    }
    return null;
  }

  private AppTheme resolveTheme(String storedTheme) {
    if (storedTheme == null || storedTheme.isBlank()) {
      return AppTheme.LIGHT;
    }

    try {
      return AppTheme.valueOf(storedTheme.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return AppTheme.LIGHT;
    }
  }

  private SortMode resolveSortMode(String storedSortMode) {
    if (storedSortMode == null || storedSortMode.isBlank()) {
      return SortMode.NEWEST;
    }

    try {
      return SortMode.valueOf(storedSortMode.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return SortMode.NEWEST;
    }
  }

  private String buildSelectedFilterSummary() {
    if (!selectedTags.isEmpty()) {
      return "tag:" + selectedTags.iterator().next();
    }
    if (!selectedColors.isEmpty()) {
      return "color:" + selectedColors.iterator().next().name();
    }
    return "sort:" + (sortMode.get() == null ? SortMode.NEWEST.name() : sortMode.get().name());
  }

  private void saveUiState() {
    if (uiState == null) {
      uiState = new UiState();
    }

    uiState.setWindowWidth(getWindowWidth());
    uiState.setWindowHeight(getWindowHeight());
    uiState.setTheme(getTheme().name());
    uiState.setLatestSearchKeyword(searchQuery.get());
    uiState.setSortMode(sortMode.get() == null ? SortMode.NEWEST.name() : sortMode.get().name());
    uiState.setSelectedFilter(buildSelectedFilterSummary());
    uiState.setSelectedTags(new ArrayList<>(selectedTags));

    List<String> selectedColorNames = new ArrayList<>();
    for (NoteColor color : selectedColors) {
      selectedColorNames.add(color.name());
    }
    uiState.setSelectedColors(selectedColorNames);
    uiStateStore.save(uiState);
  }

  private String safeText(String value) {
    return value == null ? "" : value;
  }

  private boolean safeEquals(String left, String right) {
    return safeText(left).equals(safeText(right));
  }

  private String tagKey(String tagName) {
    return tagName == null ? "" : tagName.trim().toLowerCase(Locale.ROOT);
  }

  private String emailKey(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String resolveDisplayName(String displayName, String email) {
    if (displayName != null && !displayName.isBlank()) {
      return displayName.trim();
    }
    if (email == null || email.isBlank()) {
      return "CoNote User";
    }
    int atIndex = email.indexOf('@');
    return (atIndex > 0 ? email.substring(0, atIndex) : email).trim();
  }

  private String resolveUserName(String email) {
    if (email == null || email.isBlank()) {
      return "conote-user";
    }
    int atIndex = email.indexOf('@');
    return (atIndex > 0 ? email.substring(0, atIndex) : email).trim();
  }

  private boolean userHasIdentity(User user) {
    return user != null
        && ((user.getFullName() != null && !user.getFullName().isBlank())
        || (user.getEmail() != null && !user.getEmail().isBlank()));
  }
}
