package com.conote.client.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.client.model.NoteSourceFilter;
import com.conote.client.model.SortMode;
import com.conote.common.enums.NoteType;
import com.conote.common.enums.SharePermission;
import com.conote.common.enums.ShareStatus;
import com.conote.common.model.Note;
import com.conote.common.model.Share;
import com.conote.common.model.Tag;
import com.conote.common.model.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoNoteStoreSourceFilterTest {
  private static final String LOCAL_EMAIL = "local@conote.app";
  private Path storageDir;

  @BeforeEach
  void setUp() throws IOException {
    storageDir = createStorageDir("source-filter-");
    System.setProperty("conote.storage.dir", storageDir.toString());
    ClientStoragePaths.resetForTesting();
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("conote.storage.dir");
    ClientStoragePaths.resetForTesting();
  }

  @Test
  void filtersNotesBySource() {
    CoNoteStore store = new CoNoteStore();
    installSourceMockNotes(store);

    store.setSourceFilter(NoteSourceFilter.ALL);
    assertIterableEquals(
        List.of("shared-other", "shared-tran", "own-note"),
        visibleIds(store));

    store.setSourceFilter(NoteSourceFilter.MINE);
    assertIterableEquals(List.of("own-note"), visibleIds(store));

    store.setSourceFilter(NoteSourceFilter.SHARED);
    assertIterableEquals(List.of("shared-other", "shared-tran"), visibleIds(store));
  }

  @Test
  void sharedMetadataUsesOwnerOrIncomingShare() {
    CoNoteStore store = new CoNoteStore();
    installSourceMockNotes(store);

    NoteModel ownNote = findNote(store, "own-note");
    NoteModel sharedTran = findNote(store, "shared-tran");
    NoteModel sharedOther = findNote(store, "shared-other");

    assertFalse(ownNote.isShared());
    assertEquals(LOCAL_EMAIL, ownNote.getOwnerId());
    assertTrue(sharedTran.isShared());
    assertEquals("Trần Minh", sharedTran.getSharedByName());
    assertTrue(sharedOther.isShared());
    assertEquals("Ngọc Anh", sharedOther.getSharedByName());
  }

  @Test
  void sourceFilterCombinesWithSearchTagColorAndSort() {
    CoNoteStore store = new CoNoteStore();
    installSourceMockNotes(store);

    store.setSourceFilter(NoteSourceFilter.SHARED);
    store.setSearchQuery("redesign");
    store.toggleTagFilter("project");
    store.toggleColorFilter(NoteColor.BLUE);
    store.setSortMode(SortMode.OLDEST);

    assertIterableEquals(List.of("shared-tran"), visibleIds(store));
    assertEquals(SortMode.OLDEST, store.sortModeProperty().get());
  }

  private void installSourceMockNotes(CoNoteStore store) {
    User currentUser = user(null, LOCAL_EMAIL, "CoNote Local User");
    User tranMinh = user(101L, "tran.minh@example.com", "Trần Minh");
    User ngocAnh = user(102L, "ngoc.anh@example.com", "Ngọc Anh");

    Note ownNote = note(
        "own-note",
        currentUser,
        "Work plan",
        "Project planning note",
        NoteColor.AMBER,
        LocalDateTime.of(2026, 4, 28, 10, 0),
        "work");
    Note sharedTran = note(
        "shared-tran",
        tranMinh,
        "Dự án Redesign Logo",
        "Đã upload file Figma mới nhất",
        NoteColor.BLUE,
        LocalDateTime.of(2026, 4, 29, 10, 0),
        "design",
        "project");
    Note sharedOther = note(
        "shared-other",
        ngocAnh,
        "Marketing calendar",
        "Launch checklist",
        NoteColor.GREEN,
        LocalDateTime.of(2026, 4, 30, 10, 0),
        "meeting");

    sharedTran.setShares(List.of(incomingShare(sharedTran, tranMinh, currentUser)));
    sharedOther.setShares(List.of(incomingShare(sharedOther, ngocAnh, currentUser)));

    store.replaceNotesForTesting(List.of(ownNote, sharedTran, sharedOther));
  }

  private Note note(
      String id,
      User owner,
      String title,
      String content,
      NoteColor color,
      LocalDateTime createdAt,
      String... tags) {
    Note note = new Note();
    note.setNoteId(id);
    note.setOwner(owner);
    note.setTitle(title);
    note.setContent(content);
    note.setColor(color.cssName());
    note.setPinned(false);
    note.setDeleted(false);
    note.setShareStatus(ShareStatus.PRIVATE);
    note.setNoteType(NoteType.TEXT);
    note.setCreatedAt(createdAt);
    note.setUpdatedAt(createdAt);
    note.setShares(new ArrayList<>());
    note.setTags(tagList(owner, tags));
    return note;
  }

  private List<Tag> tagList(User owner, String... names) {
    List<Tag> tags = new ArrayList<>();
    for (String name : names) {
      Tag tag = new Tag();
      tag.setUser(owner);
      tag.setTagName(name);
      tag.setCreatedAt(LocalDateTime.now());
      tags.add(tag);
    }
    return tags;
  }

  private Share incomingShare(Note note, User sharedBy, User sharedWith) {
    Share share = new Share();
    share.setNote(note);
    share.setSharedBy(sharedBy);
    share.setSharedWith(sharedWith);
    share.setPermission(SharePermission.EDIT);
    note.setShareStatus(ShareStatus.SHARED);
    return share;
  }

  private User user(Long userId, String email, String fullName) {
    User user = new User();
    user.setUserId(userId);
    user.setUserName(email.substring(0, email.indexOf('@')));
    user.setEmail(email);
    user.setFullName(fullName);
    user.setActive(true);
    user.setVerified(true);
    return user;
  }

  private List<String> visibleIds(CoNoteStore store) {
    return store.getVisibleNotes().stream()
        .map(NoteModel::getId)
        .toList();
  }

  private NoteModel findNote(CoNoteStore store, String id) {
    return store.getNotes().stream()
        .filter(note -> id.equals(note.getId()))
        .findFirst()
        .orElseThrow();
  }

  private Path createStorageDir(String prefix) throws IOException {
    Path baseDir = Path.of("target", "test-data").toAbsolutePath();
    Files.createDirectories(baseDir);
    return Files.createTempDirectory(baseDir, prefix);
  }
}
