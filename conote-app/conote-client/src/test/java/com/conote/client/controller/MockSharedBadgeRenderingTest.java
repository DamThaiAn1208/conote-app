package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import com.conote.common.enums.NoteType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
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
class MockSharedBadgeRenderingTest {
  private static final String SHARED_BY_ONE = "Tr\u1ea7n Minh";
  private static final String SHARED_BY_TWO = "Nguy\u1ec5n An";

  private Stage primaryStage;
  private Path storageDir;

  @Start
  void start(Stage stage) {
    primaryStage = stage;
    primaryStage.hide();
  }

  @BeforeEach
  void setUp() throws IOException {
    storageDir = createStorageDir("mock-shared-badge-");
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
  void mainCardShowsSharerNearDateWhilePopupKeepsPermissionBadge() throws Exception {
    CoNoteStore store = new CoNoteStore();
    NoteModel note = store.createNote(NoteType.TEXT);
    runFx(() -> {
      note.setMockSharedState(true, SHARED_BY_ONE, "view");
      return null;
    });

    LoadedView<NoteCardController> cardView = runFx(() -> ViewLoader.load("/fxml/shared/NoteCard.fxml"));
    showNoteCard(cardView, note, store);

    HBox cardDateRow = lookupNode(cardView.root(), ".note-card-date-row", HBox.class);
    HBox cardSharedBadge = lookupNode(cardView.root(), ".note-card-shared-badge", HBox.class);
    Label cardSharedByLabel = lookupNode(cardView.root(), ".note-card-shared-name", Label.class);

    assertTrue(cardSharedBadge.isVisible());
    assertTrue(cardSharedBadge.isManaged());
    assertEquals(cardDateRow, cardSharedBadge.getParent());
    assertEquals(note.getMockSharedBy(), cardSharedByLabel.getText());
    assertFalse(cardSharedByLabel.getText().contains("Chia"));
    assertNull(lookupOptionalNode(cardView.root(), ".note-shared-permission-badge"));

    runFx(() -> {
      note.setMockSharedState(true, SHARED_BY_TWO, "edit");
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    assertEquals(note.getMockSharedBy(), cardSharedByLabel.getText());
    assertNull(lookupOptionalNode(cardView.root(), ".note-shared-permission-badge"));

    LoadedView<NoteWindowController> noteWindowView =
        runFx(() -> ViewLoader.load("/fxml/note/NoteWindow.fxml"));
    Stage noteStage = showNoteWindow(noteWindowView, note, store);

    FlowPane windowSharedInfoRow =
        lookupNode(noteWindowView.root(), ".note-shared-info-row", FlowPane.class);
    Label windowSharedByLabel =
        lookupNode(noteWindowView.root(), ".note-shared-by-text", Label.class);
    Label windowPermissionLabel =
        lookupNode(noteWindowView.root(), ".note-shared-permission-badge", Label.class);

    assertTrue(windowSharedInfoRow.isVisible());
    assertTrue(windowSharedInfoRow.isManaged());
    assertEquals(note.getMockSharedDisplayText(), windowSharedByLabel.getText());
    assertEquals(note.getMockPermissionDisplayText(), windowPermissionLabel.getText());
    assertTrue(windowPermissionLabel.getStyleClass().contains("note-shared-permission-edit"));

    runFx(() -> {
      noteStage.hide();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();
  }

  @Test
  void sharedBadgesStayHiddenForOwnedNotes() throws Exception {
    CoNoteStore store = new CoNoteStore();
    NoteModel note = store.createNote(NoteType.TEXT);
    runFx(() -> {
      note.setMockSharedState(false, "", "view");
      return null;
    });

    LoadedView<NoteCardController> cardView = runFx(() -> ViewLoader.load("/fxml/shared/NoteCard.fxml"));
    showNoteCard(cardView, note, store);
    HBox cardSharedBadge = lookupNode(cardView.root(), ".note-card-shared-badge", HBox.class);

    assertFalse(cardSharedBadge.isVisible());
    assertFalse(cardSharedBadge.isManaged());

    LoadedView<NoteWindowController> noteWindowView =
        runFx(() -> ViewLoader.load("/fxml/note/NoteWindow.fxml"));
    Stage noteStage = showNoteWindow(noteWindowView, note, store);
    FlowPane windowSharedInfoRow =
        lookupNode(noteWindowView.root(), ".note-shared-info-row", FlowPane.class);

    assertFalse(windowSharedInfoRow.isVisible());
    assertFalse(windowSharedInfoRow.isManaged());

    runFx(() -> {
      noteStage.hide();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();
  }

  private void showNoteCard(
      LoadedView<NoteCardController> view,
      NoteModel note,
      CoNoteStore store)
      throws InterruptedException, ExecutionException, TimeoutException {
    runFx(() -> {
      view.controller().setContext(note, store, null);
      Scene scene = new Scene(view.root(), 420, 320);
      scene.getStylesheets().add(ClientApplication.stylesheetUrl());
      primaryStage.setScene(scene);
      primaryStage.show();
      view.root().applyCss();
      view.root().layout();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();
  }

  private Stage showNoteWindow(
      LoadedView<NoteWindowController> view,
      NoteModel note,
      CoNoteStore store)
      throws InterruptedException, ExecutionException, TimeoutException {
    Stage stage = runFx(Stage::new);
    runFx(() -> {
      Scene scene = new Scene(
          view.root(),
          NoteWindowController.DEFAULT_WINDOW_WIDTH,
          NoteWindowController.DEFAULT_WINDOW_HEIGHT);
      scene.setFill(Color.TRANSPARENT);
      scene.getStylesheets().add(ClientApplication.stylesheetUrl());
      stage.setScene(scene);
      view.controller().setContext(note, store, stage);
      stage.show();
      view.root().applyCss();
      view.root().layout();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();
    return stage;
  }

  private <T> T lookupNode(Parent root, String selector, Class<T> type)
      throws InterruptedException, ExecutionException, TimeoutException {
    return runFx(() -> type.cast(root.lookup(selector)));
  }

  private Node lookupOptionalNode(Parent root, String selector)
      throws InterruptedException, ExecutionException, TimeoutException {
    return runFx(() -> root.lookup(selector));
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
