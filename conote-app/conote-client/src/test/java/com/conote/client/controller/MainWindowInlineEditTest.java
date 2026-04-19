package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.cache.ChecklistContentCodec;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.cache.NoteCacheStore;
import com.conote.client.model.NoteModel;
import com.conote.client.model.NoteColor;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import com.conote.common.enums.NoteType;
import com.conote.common.enums.ShareStatus;
import com.conote.common.model.Note;
import com.conote.common.model.User;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class MainWindowInlineEditTest {
  private static final String STORAGE_OVERRIDE_PROPERTY = "conote.storage.dir";

  @TempDir
  Path tempDir;

  private MainWindowController controller;
  private NoteModel meetingNote;
  private Stage stage;

  @BeforeEach
  void setUpStorage() {
    System.setProperty(STORAGE_OVERRIDE_PROPERTY, tempDir.resolve("conote-storage").toString());
    ClientStoragePaths.resetForTesting();

    User owner = new User();
    owner.setUserName("local-user");
    owner.setEmail("local@conote.app");
    owner.setFullName("CoNote Local User");
    owner.setVerified(true);
    owner.setActive(true);
    owner.setCreatedAt(LocalDateTime.now().minusDays(3));
    owner.setUpdatedAt(LocalDateTime.now().minusDays(1));

    Note meeting = new Note();
    meeting.setNoteId(UUID.randomUUID().toString());
    meeting.setOwner(owner);
    meeting.setTitle("Meeting Notes: Q3 Planning");
    meeting.setContent(
        "Discussed goals for the next quarter.\n\nFocus on onboarding, activation and the sharing flow.");
    meeting.setColor(NoteColor.AMBER.cssName());
    meeting.setPinned(true);
    meeting.setDeleted(false);
    meeting.setShareStatus(ShareStatus.PRIVATE);
    meeting.setNoteType(NoteType.TEXT);
    meeting.setCreatedAt(LocalDateTime.now().minusHours(5));
    meeting.setUpdatedAt(LocalDateTime.now().minusMinutes(14));

    Note checklist = new Note();
    checklist.setNoteId(UUID.randomUUID().toString());
    checklist.setOwner(owner);
    checklist.setTitle("Grocery List");
    checklist.setContent(ChecklistContentCodec.encode(List.of(
        new com.conote.client.model.ChecklistItemModel("Milk", true),
        new com.conote.client.model.ChecklistItemModel("Eggs", false))));
    checklist.setColor(NoteColor.GREEN.cssName());
    checklist.setPinned(false);
    checklist.setDeleted(false);
    checklist.setShareStatus(ShareStatus.PRIVATE);
    checklist.setNoteType(NoteType.CHECKLIST);
    checklist.setCreatedAt(LocalDateTime.now().minusDays(1));
    checklist.setUpdatedAt(LocalDateTime.now().minusHours(3));

    new NoteCacheStore().saveAll(List.of(meeting, checklist));
  }

  @AfterEach
  void tearDownStorage() {
    System.clearProperty(STORAGE_OVERRIDE_PROPERTY);
    ClientStoragePaths.resetForTesting();
  }

  @Start
  private void start(Stage stage) {
    LoadedView<MainWindowController> view = ViewLoader.load("/fxml/main/MainWindow.fxml");
    controller = view.controller();
    this.stage = stage;

    Scene scene = new Scene(view.root(), 500, 760);
    scene.getStylesheets().add(ClientApplication.stylesheetUrl());

    stage.setScene(scene);
    stage.show();
    stage.toFront();
    stage.requestFocus();
  }

  @Test
  void mainWindowLoadsLocalCachedNotes(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
        () -> !controller.getStore().loadingProperty().get());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !visibleNoteTitles(robot).isEmpty());

    List<String> noteTitles = visibleNoteTitles(robot).stream()
        .map(Label::getText)
        .toList();

    assertFalse(noteTitles.isEmpty(), "Main window should render locally cached notes after loading");
    assertTrue(noteTitles.contains("Meeting Notes: Q3 Planning"),
        "Main window should show notes from the local cache");
  }

  @Test
  void inlineTextEditKeepsSelectedNoteExpanded(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
        () -> !controller.getStore().loadingProperty().get());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !visibleNoteTitles(robot).isEmpty());

    meetingNote = controller.getStore().getVisibleNotes().stream()
        .filter(note -> "Meeting Notes: Q3 Planning".equals(note.getTitle()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected cached note was not loaded"));

    robot.interact(() -> controller.getStore().setExpandedNoteId(meetingNote.getId()));
    WaitForAsyncUtils.waitForFxEvents();
    WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS,
        () -> meetingNote.getId().equals(controller.getStore().expandedNoteIdProperty().get()));

    TextArea editor = visibleQuickTextArea(robot);
    robot.interact(editor::requestFocus);
    WaitForAsyncUtils.waitForFxEvents();
    robot.write("x");

    Thread.sleep(450);
    WaitForAsyncUtils.waitForFxEvents();

    TextArea visibleEditor = visibleQuickTextArea(robot);
    assertNotNull(visibleEditor, "Inline editor should still be visible after typing");
    assertEquals(meetingNote.getId(), controller.getStore().expandedNoteIdProperty().get(),
        "Typing inline should not collapse the selected note");
    assertTrue(meetingNote.getContent().endsWith("x"),
        "Typing inline should update the selected note content");
  }

  private TextArea visibleQuickTextArea(FxRobot robot) {
    Set<TextArea> editors = robot.lookup("#quickTextArea").queryAllAs(TextArea.class);
    return editors.stream()
        .filter(TextArea::isVisible)
        .max(Comparator.comparingDouble(TextArea::getOpacity))
        .orElse(null);
  }

  private List<Label> visibleNoteTitles(FxRobot robot) {
    Set<Label> titles = robot.lookup(".note-card-title").queryAllAs(Label.class);
    return titles.stream()
        .filter(Label::isVisible)
        .sorted(Comparator.comparingDouble(title -> title.localToScene(title.getBoundsInLocal()).getMinY()))
        .toList();
  }
}
