package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fxmisc.richtext.InlineCssTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class TextNotePersistenceFlowTest {
  private Stage primaryStage;
  private Path storageDir;

  @Start
  void start(Stage stage) {
    primaryStage = stage;
    primaryStage.hide();
  }

  @BeforeEach
  void setUp() throws IOException {
    storageDir = createStorageDir("text-note-persistence-");
    System.setProperty("conote.storage.dir", storageDir.toString());
    ClientStoragePaths.resetForTesting();
  }

  @AfterEach
  void tearDown() throws Exception {
    runFx(() -> {
      for (Window window : new ArrayList<>(Window.getWindows())) {
        if (window instanceof Stage stage) {
          stage.hide();
        }
      }
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();
    System.clearProperty("conote.storage.dir");
    ClientStoragePaths.resetForTesting();
  }

  @Test
  void closingNoteEditorFlushesPendingTextContentToCache() throws Exception {
    CoNoteStore store = new CoNoteStore();
    NoteModel note = store.createNote(com.conote.common.enums.NoteType.TEXT);
    LoadedView<NoteWindowController> view = runFx(() -> ViewLoader.load("/fxml/note/NoteWindow.fxml"));

    runFx(() -> {
      Scene scene = new Scene(view.root(), 780, 760);
      scene.getStylesheets().add(ClientApplication.stylesheetUrl());
      primaryStage.setScene(scene);
      view.controller().setContext(note, store, primaryStage);
      primaryStage.show();
      view.root().applyCss();
      view.root().layout();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    TextNoteEditorController textEditorController =
        readField(view.controller(), "textNoteEditorController", TextNoteEditorController.class);
    InlineCssTextArea editorArea =
        readField(textEditorController, "editorArea", InlineCssTextArea.class);
    assertNotNull(editorArea);

    runFx(() -> {
      editorArea.replaceText("Latest editor text");
      return null;
    });
    runFx(() -> {
      view.controller().closeWindow();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    CoNoteStore reloaded = new CoNoteStore();
    NoteModel saved = findNote(reloaded, note.getId());
    assertNotNull(saved);
    assertEquals("Latest editor text", saved.getPlainTextContent());
    assertCacheContains(note.getId(), "Latest editor text");
  }

  @Test
  void flushingMainWindowTextEditsPersistsPendingContentToCache() throws Exception {
    CoNoteStore store = new CoNoteStore();
    NoteModel note = store.createNote(com.conote.common.enums.NoteType.TEXT);
    LoadedView<NoteCardController> view = runFx(() -> ViewLoader.load("/fxml/shared/NoteCard.fxml"));

    runFx(() -> {
      view.controller().setContext(note, store, null);
      Scene scene = new Scene(view.root(), 420, 320);
      scene.getStylesheets().add(ClientApplication.stylesheetUrl());
      primaryStage.setScene(scene);
      primaryStage.show();
      store.setExpandedNoteId(note.getId());
      view.root().applyCss();
      view.root().layout();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    TextArea quickTextArea = readField(view.controller(), "quickTextArea", TextArea.class);
    assertNotNull(quickTextArea);

    runFx(() -> {
      quickTextArea.setText("Latest main window text");
      return null;
    });
    runFx(() -> {
      view.controller().flushPendingChanges();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    CoNoteStore reloaded = new CoNoteStore();
    NoteModel saved = findNote(reloaded, note.getId());
    assertNotNull(saved);
    assertEquals("Latest main window text", saved.getPlainTextContent());
    assertCacheContains(note.getId(), "Latest main window text");
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

  private <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }

  private <T> T runFx(Callable<T> callable)
      throws InterruptedException, ExecutionException, TimeoutException {
    FutureTask<T> task = new FutureTask<>(callable);
    Platform.runLater(task);
    return task.get(10, TimeUnit.SECONDS);
  }

  private Path createStorageDir(String prefix) throws IOException {
    Path baseDir = Path.of("target", "test-data").toAbsolutePath();
    Files.createDirectories(baseDir);
    return Files.createTempDirectory(baseDir, prefix);
  }
}
