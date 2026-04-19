package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.model.NoteModel;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class MainWindowInlineEditTest {
  private MainWindowController controller;
  private NoteModel meetingNote;
  private Stage stage;

  @Start
  private void start(Stage stage) throws Exception {
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
  void mainWindowLoadsDemoNotes(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
        () -> !controller.getStore().loadingProperty().get());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !visibleNoteTitles(robot).isEmpty());

    List<String> noteTitles = visibleNoteTitles(robot).stream()
        .map(Label::getText)
        .toList();

    assertFalse(noteTitles.isEmpty(), "Main window should render demo notes after loading");
    assertTrue(noteTitles.contains("Meeting Notes: Q3 Planning"),
        "Main window should show the seeded planning note");
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
        .orElseThrow(() -> new AssertionError("Expected demo note was not loaded"));

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
