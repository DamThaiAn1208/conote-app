package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.NoteModel;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import com.conote.common.enums.NoteType;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
class MainWindowPinButtonStateTest {
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
  void newlyCreatedNotesKeepPinButtonHiddenUntilPinned(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
        () -> !controller.getStore().loadingProperty().get());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    AtomicReference<NoteModel> newestNoteRef = new AtomicReference<>();
    robot.interact(() -> {
      for (int index = 0; index < 4; index++) {
        newestNoteRef.set(controller.getStore().createNote(NoteType.TEXT));
      }
    });
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(450);

    robot.interact(() -> controller.getStore().togglePin(newestNoteRef.get()));
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(180);

    List<Button> visiblePinButtons = visibleButtons(robot, "#pinButton");
    assertTrue(!visiblePinButtons.isEmpty(),
        "Pinning the selected note should reveal at least one pin button");
    assertTrue(visiblePinButtons.stream().allMatch(button -> button.getGraphic() != null),
        "Visible pin buttons should keep their icon graphics after pinning");
  }

  private List<Button> visibleButtons(FxRobot robot, String query) {
    Set<Button> buttons = robot.lookup(query).queryAllAs(Button.class);
    return buttons.stream()
        .filter(button -> button.getOpacity() > 0.0 && !button.isMouseTransparent())
        .sorted(Comparator.comparingDouble(button -> button.localToScene(button.getBoundsInLocal()).getMinY()))
        .toList();
  }
}
