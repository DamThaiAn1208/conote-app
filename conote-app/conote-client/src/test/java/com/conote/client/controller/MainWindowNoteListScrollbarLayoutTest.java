package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.NoteModel;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import com.conote.common.enums.NoteType;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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
class MainWindowNoteListScrollbarLayoutTest {
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

    Scene scene = new Scene(view.root(), 500, 480);
    scene.getStylesheets().add(ClientApplication.stylesheetUrl());

    stage.setScene(scene);
    stage.show();
    stage.toFront();
    stage.requestFocus();
  }

  @Test
  void noteCardsKeepStableWidthWhenMainWindowScrollbarAppears(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
        () -> !controller.getStore().loadingProperty().get());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    NoteModel[] expandedCandidate = new NoteModel[1];
    robot.interact(() -> {
      NoteModel first = controller.getStore().createNote(NoteType.TEXT);
      controller.getStore().updatePlainTextContent(first,
          "Single note before scrollbar\n"
              + "Second line for expansion\n"
              + "Third line for expansion\n"
              + "Fourth line for expansion\n"
              + "Fifth line for expansion");
      controller.getStore().setExpandedNoteId(null);
      expandedCandidate[0] = first;
    });
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(260);
    WaitForAsyncUtils.waitForFxEvents();

    Pane listHost = robot.lookup("#noteListHost").queryAs(Pane.class);
    ScrollPane noteListScroll = robot.lookup("#noteListScroll").queryAs(ScrollPane.class);
    HBox createButton = robot.lookup("#primaryButton").queryAs(HBox.class);
    VBox initialCard = robot.lookup(".note-card").queryAs(VBox.class);
    double listHostMinX = listHost.localToScene(listHost.getLayoutBounds()).getMinX();
    double createButtonMinX = createButton.localToScene(createButton.getLayoutBounds()).getMinX();
    double createButtonMaxX = createButton.localToScene(createButton.getLayoutBounds()).getMaxX();
    double cardMinX = initialCard.localToScene(initialCard.getLayoutBounds()).getMinX();
    double cardMaxX = initialCard.localToScene(initialCard.getLayoutBounds()).getMaxX();

    assertEquals(createButtonMinX, cardMinX, 1.0,
        "Main window note cards should align with the left edge of the create note button");
    assertEquals(createButtonMaxX, cardMaxX, 1.0,
        "Main window note cards should align with the right edge of the create note button");
    assertEquals(createButtonMinX, listHostMinX, 1.0,
        "Main window note list host should start at the same left edge as the create note button");

    double widthWithoutScrollbar = initialCard.getWidth();

    robot.interact(() -> {
      for (int index = 0; index < 24; index++) {
        NoteModel note = controller.getStore().createNote(NoteType.TEXT);
        controller.getStore().updatePlainTextContent(note, "Scrollable note " + index);
      }
      controller.getStore().setExpandedNoteId(null);
    });
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(420);
    WaitForAsyncUtils.waitForFxEvents();

    assertTrue(listRootOverflowsViewport(robot, noteListScroll),
        "Main window note list should overflow vertically once enough notes are present");
    assertScrollbarLayout(noteListScroll);

    robot.interact(() -> controller.getStore().setExpandedNoteId(expandedCandidate[0].getId()));
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(320);
    WaitForAsyncUtils.waitForFxEvents();

    assertTrue(listRootOverflowsViewport(robot, noteListScroll),
        "Main window note list should keep overflowing when an expanded note grows taller");
    assertScrollbarLayout(noteListScroll);

    Set<Node> cards = robot.lookup(".note-card").queryAll();
    VBox anyCard = cards.stream()
        .filter(VBox.class::isInstance)
        .map(VBox.class::cast)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected at least one note card"));

    assertEquals(widthWithoutScrollbar, anyCard.getWidth(), 1.0,
        "Main window note cards should keep the same width after the vertical scrollbar appears");
  }

  private ScrollBar findVerticalScrollBar(ScrollPane noteListScroll) {
    return noteListScroll.lookupAll(".scroll-bar").stream()
        .filter(ScrollBar.class::isInstance)
        .map(ScrollBar.class::cast)
        .filter(scrollBar -> scrollBar.getOrientation() == Orientation.VERTICAL && scrollBar.isVisible())
        .findFirst()
        .orElse(null);
  }

  private void assertScrollbarLayout(ScrollPane noteListScroll) {
    ScrollBar verticalBar = findVerticalScrollBar(noteListScroll);
    assertTrue(verticalBar != null && verticalBar.isVisible(),
        "Main window scrollbar should remain visible when the note list overflows");
  }

  private boolean listRootOverflowsViewport(FxRobot robot, ScrollPane noteListScroll) {
    VBox listRoot = robot.lookup("#listRoot").queryAs(VBox.class);
    return listRoot.getLayoutBounds().getHeight() > noteListScroll.getViewportBounds().getHeight();
  }
}
