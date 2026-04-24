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
import com.conote.common.enums.NoteType;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
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
class TextNoteAppLifecyclePersistenceTest {
  private Stage primaryStage;
  private Path storageDir;

  @Start
  void start(Stage stage) {
    primaryStage = stage;
    primaryStage.hide();
  }

  @BeforeEach
  void setUp() throws IOException {
    storageDir = createStorageDir("text-note-app-lifecycle-");
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
  void noteWindowEditsPersistThroughCloseAndAppReload() throws Exception {
    LoadedView<MainWindowController> mainView = loadMainWindow();
    NoteModel note = runFx(() -> mainView.controller().getStore().createNote(NoteType.TEXT));
    WaitForAsyncUtils.waitForFxEvents();

    runFx(() -> {
      mainView.controller().openNoteWindow(note);
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    NoteWindowController noteWindowController =
        openWindowController(mainView.controller(), note.getId());
    assertNotNull(noteWindowController);

    InlineCssTextArea editorArea = editorArea(noteWindowController);
    runFx(() -> {
      editorArea.replaceText("Lifecycle editor content");
      return null;
    });
    runFx(() -> {
      noteWindowController.closeWindow();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    runFx(() -> {
      mainView.controller().getStore().setExpandedNoteId(null);
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    NoteCardController cardController = noteCardController(mainView.controller(), note.getId());
    Label previewLabel = readField(cardController, "previewLabel", Label.class);
    String previewText = previewLabel.getText().replaceAll("\\s+", " ").trim();
    assertTrue(previewText.contains("Lifecycle editor content"));

    runFx(() -> {
      primaryStage.hide();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    CoNoteStore reloaded = new CoNoteStore();
    NoteModel saved = findNote(reloaded, note.getId());
    assertNotNull(saved);
    assertEquals("Lifecycle editor content", saved.getPlainTextContent());
    assertCacheContains(note.getId(), "Lifecycle editor content");
  }

  @Test
  void mainWindowEditsOpenInNoteWindowAndPersistAcrossReload() throws Exception {
    LoadedView<MainWindowController> mainView = loadMainWindow();
    NoteModel note = runFx(() -> mainView.controller().getStore().createNote(NoteType.TEXT));
    WaitForAsyncUtils.waitForFxEvents();

    NoteCardController cardController = noteCardController(mainView.controller(), note.getId());
    TextArea quickTextArea = readField(cardController, "quickTextArea", TextArea.class);

    runFx(() -> {
      mainView.controller().getStore().setExpandedNoteId(note.getId());
      quickTextArea.setText("Lifecycle main window content");
      mainView.controller().openNoteWindow(note);
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    NoteWindowController noteWindowController =
        openWindowController(mainView.controller(), note.getId());
    assertNotNull(noteWindowController);

    InlineCssTextArea editorArea = editorArea(noteWindowController);
    String editorText = runFx(editorArea::getText);
    assertEquals("Lifecycle main window content", editorText);

    runFx(() -> {
      noteWindowController.closeWindow();
      primaryStage.hide();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    CoNoteStore reloaded = new CoNoteStore();
    NoteModel saved = findNote(reloaded, note.getId());
    assertNotNull(saved);
    assertEquals("Lifecycle main window content", saved.getPlainTextContent());
    assertCacheContains(note.getId(), "Lifecycle main window content");
  }

  private LoadedView<MainWindowController> loadMainWindow() throws Exception {
    LoadedView<MainWindowController> view = runFx(() -> ViewLoader.load("/fxml/main/MainWindow.fxml"));
    runFx(() -> {
      Scene scene = new Scene(view.root(), 480, 760);
      scene.setFill(Color.TRANSPARENT);
      scene.getStylesheets().add(ClientApplication.stylesheetUrl());
      primaryStage.setScene(scene);
      view.controller().bindPrimaryStage(primaryStage);
      primaryStage.show();
      view.root().applyCss();
      view.root().layout();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();
    return view;
  }

  private NoteCardController noteCardController(MainWindowController mainController, String noteId)
      throws Exception {
    NoteListController noteListController =
        readField(mainController, "noteListController", NoteListController.class);
    @SuppressWarnings("unchecked")
    Map<String, NoteCardController> controllers =
        readField(noteListController, "noteCardControllersById", Map.class);
    return controllers.get(noteId);
  }

  private NoteWindowController openWindowController(MainWindowController mainController, String noteId)
      throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, NoteWindowController> controllers =
        readField(mainController, "openWindowControllers", Map.class);
    return controllers.get(noteId);
  }

  private InlineCssTextArea editorArea(NoteWindowController noteWindowController) throws Exception {
    TextNoteEditorController textEditorController =
        readField(noteWindowController, "textNoteEditorController", TextNoteEditorController.class);
    return readField(textEditorController, "editorArea", InlineCssTextArea.class);
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
