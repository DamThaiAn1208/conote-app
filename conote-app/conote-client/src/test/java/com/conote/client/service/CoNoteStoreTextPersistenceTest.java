package com.conote.client.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.NoteModel;
import com.conote.client.model.SortMode;
import com.conote.common.enums.NoteType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoNoteStoreTextPersistenceTest {
  private Path storageDir;

  @BeforeEach
  void setUp() throws IOException {
    storageDir = createStorageDir("store-text-persistence-");
    System.setProperty("conote.storage.dir", storageDir.toString());
    ClientStoragePaths.resetForTesting();
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("conote.storage.dir");
    ClientStoragePaths.resetForTesting();
  }

  @Test
  void updateContentPersistsTextNotesToLocalCache() throws IOException {
    CoNoteStore store = new CoNoteStore();
    NoteModel note = store.createNote(NoteType.TEXT);

    store.updateTitle(note, "Saved title");
    store.updateContent(note, "Saved from editor");

    CoNoteStore reloaded = new CoNoteStore();
    NoteModel saved = findNote(reloaded, note.getId());

    assertNotNull(saved);
    assertEquals("Saved title", saved.getTitle());
    assertEquals("Saved from editor", saved.getContent());
    assertEquals("Saved from editor", saved.getPlainTextContent());
    assertCacheContains(note.getId(), "Saved from editor");
  }

  @Test
  void updatePlainTextContentPersistsTextNotesAcrossReload() throws IOException {
    CoNoteStore store = new CoNoteStore();
    NoteModel note = store.createNote(NoteType.TEXT);

    store.updateTitle(note, "Plain text note");
    store.updatePlainTextContent(note, "Saved from main window");

    CoNoteStore reloaded = new CoNoteStore();
    NoteModel saved = findNote(reloaded, note.getId());

    assertNotNull(saved);
    assertEquals("Plain text note", saved.getTitle());
    assertEquals("Saved from main window", saved.getContent());
    assertEquals("Saved from main window", saved.getPlainTextContent());
    assertCacheContains(note.getId(), "Saved from main window");
  }

  @Test
  void manualReorderPersistsAcrossReload() throws IOException {
    CoNoteStore store = new CoNoteStore();
    store.createNote(NoteType.TEXT);
    store.createNote(NoteType.TEXT);
    store.createNote(NoteType.TEXT);
    NoteModel previousTop = store.getVisibleNotes().getFirst();
    NoteModel previousBottom = store.getVisibleNotes().getLast();

    boolean moved = store.moveVisibleNote(previousBottom.getId(), previousTop.getId(), false);
    List<String> expectedOrder = store.getVisibleNotes().stream()
        .map(NoteModel::getId)
        .toList();

    assertTrue(moved);
    assertEquals(SortMode.MANUAL, store.sortModeProperty().get());
    assertEquals(previousBottom.getId(), store.getVisibleNotes().getFirst().getId());
    assertOrderCacheContains(previousBottom.getId());

    CoNoteStore reloaded = new CoNoteStore();
    List<String> reloadedOrder = reloaded.getVisibleNotes().stream()
        .map(NoteModel::getId)
        .toList();

    assertEquals(SortMode.MANUAL, reloaded.sortModeProperty().get());
    assertEquals(expectedOrder, reloadedOrder);
  }

  private NoteModel findNote(CoNoteStore store, String noteId) {
    return store.getNotes().stream()
        .filter(note -> noteId.equals(note.getId()))
        .findFirst()
        .orElse(null);
  }

  private void assertCacheContains(String noteId, String content) throws IOException {
    String json = Files.readString(ClientStoragePaths.noteCacheFile());
    assertTrue(json.contains(noteId));
    assertTrue(json.contains(content));
  }

  private void assertOrderCacheContains(String noteId) throws IOException {
    String json = Files.readString(ClientStoragePaths.noteOrderCacheFile());
    assertTrue(json.contains(noteId));
    assertTrue(json.contains("sortOrder"));
  }

  private Path createStorageDir(String prefix) throws IOException {
    Path baseDir = Path.of("target", "test-data").toAbsolutePath();
    Files.createDirectories(baseDir);
    return Files.createTempDirectory(baseDir, prefix);
  }
}
