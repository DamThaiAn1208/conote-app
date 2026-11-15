package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import com.conote.common.dto.auth.AuthDtos;
import com.conote.common.enums.NoteType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

@ExtendWith(ApplicationExtension.class)
class AuthenticatedWindowIsolationTest {
  private Stage stage;

  @Start
  void start(Stage stage) {
    this.stage = stage;
    stage.hide();
  }

  @BeforeEach
  void setUp() throws Exception {
    Path storage = Files.createTempDirectory("conote-auth-window-");
    System.setProperty("conote.storage.dir", storage.toString());
    ClientStoragePaths.resetForTesting();
  }

  @AfterEach
  void tearDown() throws Exception {
    FutureTask<Void> hide = new FutureTask<>(() -> {
      stage.hide();
      return null;
    });
    Platform.runLater(hide);
    hide.get(10, TimeUnit.SECONDS);
    System.clearProperty("conote.storage.dir");
    ClientStoragePaths.resetForTesting();
  }

  @Test
  void authenticatedWindowDoesNotShowLegacyLocalNotes() throws Exception {
    new CoNoteStore().createNote(NoteType.TEXT);
    AuthDtos.UserView user = new AuthDtos.UserView(42L, "member", "member@example.test", "Member");
    FutureTask<LoadedView<MainWindowController>> task = new FutureTask<>(() -> {
      LoadedView<MainWindowController> view = ViewLoader.load("/fxml/main/MainWindow.fxml",
          type -> {
            if (type == MainWindowController.class) {
              return new MainWindowController(user);
            }
            try {
              return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException exception) {
              throw new IllegalStateException(exception);
            }
          });
      stage.setScene(new Scene(view.root(), 480, 760));
      return view;
    });
    Platform.runLater(task);
    LoadedView<MainWindowController> view = task.get(10, TimeUnit.SECONDS);

    assertEquals("member@example.test", view.controller().getStore().getCurrentUserEmail());
    assertTrue(view.controller().getStore().getVisibleNotes().isEmpty());
    FutureTask<Void> docking = new FutureTask<>(() -> {
      view.controller().bindPrimaryStage(stage);
      view.controller().getStore().dockOnDesktopProperty().set(true);
      assertTrue(stage.isAlwaysOnTop());
      view.controller().getStore().dockOnDesktopProperty().set(false);
      assertFalse(stage.isAlwaysOnTop());
      view.controller().unbindPrimaryStage();
      return null;
    });
    Platform.runLater(docking);
    docking.get(10, TimeUnit.SECONDS);
  }

  @Test
  void authWindowHasWorkingWindowControls() throws Exception {
    FutureTask<Void> task = new FutureTask<>(() -> {
      LoadedView<AuthWindowController> view = ViewLoader.load("/fxml/auth/AuthWindow.fxml");
      stage.setScene(new Scene(view.root(), 440, 510));
      stage.show();
      Button close = (Button) view.root().lookup(".auth-window-button");
      assertNotNull(close);
      assertEquals("Thu nhỏ cửa sổ", close.getAccessibleText());
      close.fire();
      assertTrue(stage.isIconified());
      stage.setIconified(false);
      return null;
    });
    Platform.runLater(task);
    task.get(10, TimeUnit.SECONDS);
  }
}
