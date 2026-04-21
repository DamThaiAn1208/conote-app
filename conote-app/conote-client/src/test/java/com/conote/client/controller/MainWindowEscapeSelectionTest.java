package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.conote.client.app.ClientApplication;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.NoteModel;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import com.conote.common.enums.NoteType;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
class MainWindowEscapeSelectionTest {
  private static final String STORAGE_OVERRIDE_PROPERTY = "conote.storage.dir";

  @TempDir
  Path tempDir;

  private MainWindowController controller;
  private Stage stage;

  @BeforeEach
  void setUpStorage() {
    System.setProperty(STORAGE_OVERRIDE_PROPERTY, tempDir.resolve("conote-storage").toString());
    ClientStoragePaths.resetForTesting();
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
  void pressingEscapeClearsExpandedNoteSelection(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
        () -> !controller.getStore().loadingProperty().get());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    robot.interact(() -> {
      NoteModel created = controller.getStore().createNote(NoteType.TEXT);
      controller.getStore().updatePlainTextContent(created, "Esc should collapse this note");
      controller.getStore().setExpandedNoteId(created.getId());
    });
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(260);

    robot.interact(() -> stage.getScene().getRoot().fireEvent(
        new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
    WaitForAsyncUtils.waitForFxEvents();

    assertNull(controller.getStore().expandedNoteIdProperty().get(),
        "Pressing Escape in the main window should clear the selected note");
  }
}
