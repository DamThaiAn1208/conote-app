package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.NoteColor;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class NoteCardSourceBadgeTest {
  private Stage primaryStage;
  private Path storageDir;

  @Start
  void start(Stage stage) {
    primaryStage = stage;
    primaryStage.hide();
  }

  @BeforeEach
  void setUp() throws IOException {
    storageDir = createStorageDir("note-source-badge-");
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
  void sharedBadgeOnlyShowsForSharedNotes() throws Exception {
    LoadedView<NoteCardController> ownedView = renderCard(ownedNote());
    HBox ownedBadge = readField(ownedView.controller(), "sharedSourceBadge", HBox.class);
    assertFalse(ownedBadge.isVisible());
    assertFalse(ownedBadge.isManaged());

    LoadedView<NoteCardController> sharedView = renderCard(sharedNote());
    HBox sharedBadge = readField(sharedView.controller(), "sharedSourceBadge", HBox.class);
    Label sharedLabel = readField(sharedView.controller(), "sharedSourceLabel", Label.class);

    assertTrue(sharedBadge.isVisible());
    assertTrue(sharedBadge.isManaged());
    assertEquals("Trần Minh", sharedLabel.getText());
  }

  private LoadedView<NoteCardController> renderCard(NoteModel note) throws Exception {
    CoNoteStore store = new CoNoteStore();
    LoadedView<NoteCardController> view = runFx(() -> ViewLoader.load("/fxml/shared/NoteCard.fxml"));
    runFx(() -> {
      view.controller().setContext(note, store, null);
      Scene scene = new Scene(view.root(), 420, 240);
      scene.getStylesheets().add(ClientApplication.stylesheetUrl());
      primaryStage.setScene(scene);
      primaryStage.show();
      view.root().applyCss();
      view.root().layout();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();
    return view;
  }

  private NoteModel ownedNote() {
    NoteModel note = note("owned-note");
    note.setOwnerId("local@conote.app");
    note.setOwnerName("CoNote Local User");
    note.setShared(false);
    return note;
  }

  private NoteModel sharedNote() {
    NoteModel note = note("shared-note");
    note.setOwnerId("101");
    note.setOwnerName("Trần Minh");
    note.setShared(true);
    note.setSharedById("101");
    note.setSharedByName("Trần Minh");
    return note;
  }

  private NoteModel note(String id) {
    long now = System.currentTimeMillis();
    return new NoteModel(
        id,
        NoteType.TEXT,
        "Source badge note",
        "Preview",
        NoteColor.BLUE,
        false,
        now,
        now);
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
