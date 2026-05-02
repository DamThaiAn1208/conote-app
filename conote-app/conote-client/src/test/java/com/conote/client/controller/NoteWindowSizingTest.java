package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.NoteModel;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import com.conote.common.enums.SharePermission;
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
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Modality;
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
class NoteWindowSizingTest {
  private Stage primaryStage;
  private Path storageDir;

  @Start
  void start(Stage stage) {
    primaryStage = stage;
    primaryStage.hide();
  }

  @BeforeEach
  void setUp() throws IOException {
    storageDir = createStorageDir("note-window-sizing-");
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
  void noteWindowOpensAtSmallerDefaultSizeAndResizesFromCorner() throws Exception {
    LoadedView<MainWindowController> mainView = loadMainWindow();
    NoteModel note = runFx(() -> mainView.controller().getStore().createNote(NoteType.TEXT));
    WaitForAsyncUtils.waitForFxEvents();

    runFx(() -> {
      mainView.controller().openNoteWindow(note);
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    Stage noteStage = openWindowStage(mainView.controller(), note.getId());
    NoteWindowController noteWindowController =
        openWindowController(mainView.controller(), note.getId());
    Region resizeSouthEastHandle =
        readField(noteWindowController, "resizeSouthEastHandle", Region.class);

    assertNotNull(noteStage);
    assertNotNull(noteWindowController);
    assertTrue(runFx(noteStage::isResizable));
    assertTrue(runFx(noteStage::getMaxWidth) > NoteWindowController.DEFAULT_WINDOW_WIDTH);
    assertTrue(runFx(noteStage::getMaxHeight) > NoteWindowController.DEFAULT_WINDOW_HEIGHT);

    double initialWidth = runFx(() -> noteStage.getWidth());
    double initialHeight = runFx(() -> noteStage.getHeight());
    assertEquals(NoteWindowController.DEFAULT_WINDOW_WIDTH, initialWidth, 1.5);
    assertEquals(NoteWindowController.DEFAULT_WINDOW_HEIGHT, initialHeight, 1.5);

    Bounds handleBounds =
        runFx(() -> resizeSouthEastHandle.localToScene(resizeSouthEastHandle.getBoundsInLocal()));
    double pressLocalX = 4.0;
    double pressLocalY = 4.0;
    double pressSceneX = handleBounds.getMinX() + pressLocalX;
    double pressSceneY = handleBounds.getMinY() + pressLocalY;
    double dragSceneX = pressSceneX + 80.0;
    double dragSceneY = pressSceneY + 60.0;

    runFx(() -> {
      Event.fireEvent(resizeSouthEastHandle, mouseEvent(
          MouseEvent.MOUSE_ENTERED,
          pressLocalX,
          pressLocalY,
          noteStage.getX() + pressSceneX,
          noteStage.getY() + pressSceneY,
          MouseButton.NONE,
          false));
      Event.fireEvent(resizeSouthEastHandle, mouseEvent(
          MouseEvent.MOUSE_PRESSED,
          pressLocalX,
          pressLocalY,
          noteStage.getX() + pressSceneX,
          noteStage.getY() + pressSceneY,
          MouseButton.PRIMARY,
          true));
      Event.fireEvent(resizeSouthEastHandle, mouseEvent(
          MouseEvent.MOUSE_DRAGGED,
          pressLocalX + 80.0,
          pressLocalY + 60.0,
          noteStage.getX() + dragSceneX,
          noteStage.getY() + dragSceneY,
          MouseButton.PRIMARY,
          true));
      Event.fireEvent(resizeSouthEastHandle, mouseEvent(
          MouseEvent.MOUSE_RELEASED,
          pressLocalX + 80.0,
          pressLocalY + 60.0,
          noteStage.getX() + dragSceneX,
          noteStage.getY() + dragSceneY,
          MouseButton.PRIMARY,
          false));
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    assertEquals(
        NoteWindowController.DEFAULT_WINDOW_WIDTH + 80.0,
        runFx(noteStage::getWidth),
        2.0);
    assertEquals(
        NoteWindowController.DEFAULT_WINDOW_HEIGHT + 60.0,
        runFx(noteStage::getHeight),
        2.0);
  }

  @Test
  void resizeCursorStaysOnVisibleFrameAndIgnoresShadowPadding() throws Exception {
    LoadedView<MainWindowController> mainView = loadMainWindow();
    NoteModel note = runFx(() -> mainView.controller().getStore().createNote(NoteType.TEXT));
    WaitForAsyncUtils.waitForFxEvents();

    runFx(() -> {
      mainView.controller().openNoteWindow(note);
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    Stage noteStage = openWindowStage(mainView.controller(), note.getId());
    NoteWindowController noteWindowController =
        openWindowController(mainView.controller(), note.getId());
    StackPane root = readField(noteWindowController, "root", StackPane.class);
    StackPane windowFrame = readField(noteWindowController, "windowFrame", StackPane.class);
    Bounds frameBounds = runFx(() -> windowFrame.localToScene(windowFrame.getBoundsInLocal()));

    Region resizeNorthHandle = readField(noteWindowController, "resizeNorthHandle", Region.class);
    Region resizeSouthHandle = readField(noteWindowController, "resizeSouthHandle", Region.class);
    Region resizeWestHandle = readField(noteWindowController, "resizeWestHandle", Region.class);
    Region resizeEastHandle = readField(noteWindowController, "resizeEastHandle", Region.class);
    Region resizeNorthWestHandle = readField(noteWindowController, "resizeNorthWestHandle", Region.class);
    Region resizeNorthEastHandle = readField(noteWindowController, "resizeNorthEastHandle", Region.class);
    Region resizeSouthWestHandle = readField(noteWindowController, "resizeSouthWestHandle", Region.class);
    Region resizeSouthEastHandle = readField(noteWindowController, "resizeSouthEastHandle", Region.class);

    runFx(() -> {
      Event.fireEvent(root, mouseEvent(
          MouseEvent.MOUSE_MOVED,
          frameBounds.getMinX() - 1.0,
          frameBounds.getMinY() + 40.0,
          noteStage.getX() + frameBounds.getMinX() - 1.0,
          noteStage.getY() + frameBounds.getMinY() + 40.0,
          MouseButton.NONE,
          false));
      return null;
    });
    assertEquals(Cursor.DEFAULT, runFx(root::getCursor));

    assertCursorOnHandle(root, noteStage, resizeWestHandle, Cursor.W_RESIZE);
    assertCursorOnHandle(root, noteStage, resizeEastHandle, Cursor.E_RESIZE);
    assertCursorOnHandle(root, noteStage, resizeNorthHandle, Cursor.N_RESIZE);
    assertCursorOnHandle(root, noteStage, resizeSouthHandle, Cursor.S_RESIZE);
    assertCursorOnHandle(root, noteStage, resizeNorthWestHandle, Cursor.NW_RESIZE);
    assertCursorOnHandle(root, noteStage, resizeNorthEastHandle, Cursor.NE_RESIZE);
    assertCursorOnHandle(root, noteStage, resizeSouthWestHandle, Cursor.SW_RESIZE);
    assertCursorOnHandle(root, noteStage, resizeSouthEastHandle, Cursor.SE_RESIZE);
  }

  @Test
  void noteWindowLayoutStaysAlignedWhenResizedNarrower() throws Exception {
    LoadedView<MainWindowController> mainView = loadMainWindow();
    NoteModel note = runFx(() -> mainView.controller().getStore().createNote(NoteType.TEXT));
    WaitForAsyncUtils.waitForFxEvents();

    runFx(() -> {
      mainView.controller().openNoteWindow(note);
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    Stage noteStage = openWindowStage(mainView.controller(), note.getId());
    NoteWindowController noteWindowController =
        openWindowController(mainView.controller(), note.getId());
    NoteToolbarController toolbarController =
        readField(noteWindowController, "noteToolbarController", NoteToolbarController.class);
    Pane noteSidebarSpace = readField(noteWindowController, "noteSidebarSpace", Pane.class);
    HBox titleBar = readField(noteWindowController, "windowTitleBar", HBox.class);
    TextField titleField = readField(noteWindowController, "titleField", TextField.class);
    InlineCssTextArea editorArea = editorArea(noteWindowController);
    HBox noteBodyShell = runFx(() -> (HBox) noteStage.getScene().getRoot().lookup(".note-body-shell"));

    javafx.scene.control.Button sidebarToggleButton =
        readField(toolbarController, "sidebarToggleButton", javafx.scene.control.Button.class);

    runFx(() -> {
      toolbarController.applyCollapsedState(false);
      noteSidebarSpace.setMinWidth(NoteToolbarController.ROOT_WIDTH);
      noteSidebarSpace.setPrefWidth(NoteToolbarController.ROOT_WIDTH);
      noteSidebarSpace.setMaxWidth(NoteToolbarController.ROOT_WIDTH);
      noteStage.setWidth(720.0);
      noteStage.setHeight(560.0);
      noteStage.getScene().getRoot().applyCss();
      noteStage.getScene().getRoot().layout();
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    Bounds titleBarBounds = runFx(() -> titleBar.localToScene(titleBar.getBoundsInLocal()));
    Bounds bodyBounds = runFx(() -> noteBodyShell.localToScene(noteBodyShell.getBoundsInLocal()));
    Bounds sidebarBounds =
        runFx(() -> noteSidebarSpace.localToScene(noteSidebarSpace.getBoundsInLocal()));
    Bounds toggleBounds =
        runFx(() -> sidebarToggleButton.localToScene(sidebarToggleButton.getBoundsInLocal()));

    double titleWidth = runFx(titleField::getWidth);
    double editorWidth = runFx(editorArea::getWidth);

    assertTrue(runFx(editorArea::isWrapText));
    assertEquals(720.0, runFx(noteStage::getWidth), 1.5);
    assertEquals(560.0, runFx(noteStage::getHeight), 1.5);
    assertTrue(titleBarBounds.getMaxY() <= bodyBounds.getMinY() + 1.0);
    assertTrue(titleWidth > 0.0);
    assertTrue(editorWidth > 0.0);
    assertTrue(editorWidth < runFx(noteBodyShell::getWidth));
    assertEquals(sidebarBounds.getMaxX(), toggleBounds.getMaxX(), 1.5);
  }

  @Test
  void noteWindowTopBarMergesActionsAndMovesShareDeleteToOverflowMenu() throws Exception {
    LoadedView<MainWindowController> mainView = loadMainWindow();
    NoteModel note = runFx(() -> mainView.controller().getStore().createNote(NoteType.TEXT));
    WaitForAsyncUtils.waitForFxEvents();

    runFx(() -> {
      mainView.controller().openNoteWindow(note);
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();

    Stage noteStage = openWindowStage(mainView.controller(), note.getId());
    NoteWindowController noteWindowController =
        openWindowController(mainView.controller(), note.getId());
    HBox titleBar = readField(noteWindowController, "windowTitleBar", HBox.class);
    ToggleButton pinButton = readField(noteWindowController, "pinButton", ToggleButton.class);
    Button colorButton = readField(noteWindowController, "colorButton", Button.class);
    Button tagButton = readField(noteWindowController, "tagButton", Button.class);
    StackPane noteMenuButton = readField(noteWindowController, "noteMenuButton", StackPane.class);
    StackPane minimizeButton = readField(noteWindowController, "windowMinimizeButton", StackPane.class);
    StackPane closeButton = readField(noteWindowController, "windowCloseButton", StackPane.class);
    Popup overflowMenuPopup = readField(noteWindowController, "overflowMenuPopup", Popup.class);
    VBox overflowMenuRoot = readField(noteWindowController, "overflowMenuRoot", VBox.class);
    StackPane overlayLayer = readField(noteWindowController, "overlayLayer", StackPane.class);

    assertTrue(runFx(() -> isDescendant(titleBar, pinButton)));
    assertTrue(runFx(() -> isDescendant(titleBar, colorButton)));
    assertTrue(runFx(() -> isDescendant(titleBar, tagButton)));
    assertTrue(runFx(() -> isDescendant(titleBar, noteMenuButton)));
    assertTrue(runFx(() -> noteStage.getScene().getRoot().lookup(".note-action-row") == null));

    HBox controlsGroup = runFx(() -> (HBox) noteMenuButton.getParent());
    assertEquals(noteMenuButton, runFx(() -> controlsGroup.getChildren().get(0)));
    assertEquals(minimizeButton, runFx(() -> controlsGroup.getChildren().get(1)));
    assertEquals(closeButton, runFx(() -> controlsGroup.getChildren().get(2)));
    assertTrue(runFx(() -> closeButton.getStyleClass().contains("note-window-close-button")));
    assertFalse(runFx(() -> closeButton.getStyleClass().contains("title-close-button")));

    clickNode(noteMenuButton, noteStage);
    assertTrue(runFx(overflowMenuPopup::isShowing));
    assertTrue(runFx(overflowMenuRoot::isVisible));
    assertEquals("Share", menuRowLabel(overflowMenuRoot, 0));
    assertEquals("Delete", menuRowLabel(overflowMenuRoot, 1));

    int initialShareMemberCount = runFx(() -> note.getShareMembers().size());
    clickMenuRowLater(overflowMenuRoot, 0, noteStage);
    Stage shareDialog = waitForOwnedDialog(noteStage, "Share note");
    assertNotNull(shareDialog);
    assertEquals(noteStage, runFx(shareDialog::getOwner));
    assertEquals(Modality.WINDOW_MODAL, runFx(shareDialog::getModality));
    assertTrue(runFx(shareDialog::isShowing));
    assertFalse(runFx(overlayLayer::isVisible));

    Button shareCancelButton = dialogButton(shareDialog, "Cancel");
    assertNotNull(shareCancelButton);
    fireButton(shareCancelButton);
    WaitForAsyncUtils.waitForFxEvents();
    assertFalse(runFx(shareDialog::isShowing));
    assertEquals(initialShareMemberCount, runFx(() -> note.getShareMembers().size()));

    clickNode(noteMenuButton, noteStage);
    clickMenuRowLater(overflowMenuRoot, 0, noteStage);
    Stage shareConfirmDialog = waitForOwnedDialog(noteStage, "Share note");
    assertNotNull(shareConfirmDialog);
    TextField shareEmailField = dialogTextField(shareConfirmDialog);
    ComboBox<SharePermission> permissionCombo = dialogPermissionCombo(shareConfirmDialog);
    assertNotNull(shareEmailField);
    assertNotNull(permissionCombo);
    runFx(() -> {
      shareEmailField.setText("shared-test@example.com");
      permissionCombo.setValue(SharePermission.EDIT);
      return null;
    });
    Button shareButton = dialogButton(shareConfirmDialog, "Share");
    assertNotNull(shareButton);
    fireButton(shareButton);
    assertTrue(runFx(() -> note.getShareMembers().stream()
        .anyMatch(member -> member.getEmail().equals("shared-test@example.com")
            && member.getPermission() == SharePermission.EDIT)));
    fireButton(dialogButton(shareConfirmDialog, "Cancel"));
    WaitForAsyncUtils.waitForFxEvents();
    assertFalse(runFx(shareConfirmDialog::isShowing));

    clickNode(noteMenuButton, noteStage);
    clickMenuRowLater(overflowMenuRoot, 1, noteStage);
    Stage deleteDialog = waitForOwnedDialog(noteStage, "Delete note?");
    assertNotNull(deleteDialog);
    assertEquals(noteStage, runFx(deleteDialog::getOwner));
    assertEquals(Modality.WINDOW_MODAL, runFx(deleteDialog::getModality));
    assertTrue(runFx(deleteDialog::isShowing));
    assertFalse(runFx(overlayLayer::isVisible));

    Button cancelButton = dialogButton(deleteDialog, "Cancel");
    assertNotNull(cancelButton);
    fireButton(cancelButton);
    WaitForAsyncUtils.waitForFxEvents();
    assertFalse(runFx(deleteDialog::isShowing));
    assertTrue(runFx(noteStage::isShowing));
    assertTrue(runFx(() -> noteExists(mainView.controller(), note)));

    clickNode(noteMenuButton, noteStage);
    clickMenuRowLater(overflowMenuRoot, 1, noteStage);
    Stage confirmDialog = waitForOwnedDialog(noteStage, "Delete note?");
    assertNotNull(confirmDialog);
    Button deleteButton = dialogButton(confirmDialog, "Delete");
    assertNotNull(deleteButton);
    fireButton(deleteButton);
    WaitForAsyncUtils.waitForFxEvents();
    assertFalse(runFx(confirmDialog::isShowing));
    assertFalse(runFx(noteStage::isShowing));
    assertFalse(runFx(() -> noteExists(mainView.controller(), note)));
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

  private Stage openWindowStage(MainWindowController mainController, String noteId) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Stage> stages = readField(mainController, "openWindows", Map.class);
    return stages.get(noteId);
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

  private void assertCursorOnHandle(
      StackPane root,
      Stage noteStage,
      Region handle,
      Cursor expectedCursor)
      throws Exception {
    Bounds handleBounds = runFx(() -> handle.localToScene(handle.getBoundsInLocal()));
    Cursor actualCursor = runFx(() -> {
      double localX = Math.max(1.0, handle.getWidth() / 2.0);
      double localY = Math.max(1.0, handle.getHeight() / 2.0);
      double screenX = noteStage.getX() + handleBounds.getMinX() + localX;
      double screenY = noteStage.getY() + handleBounds.getMinY() + localY;
      Event.fireEvent(handle, mouseEvent(
          MouseEvent.MOUSE_ENTERED,
          localX,
          localY,
          screenX,
          screenY,
          MouseButton.NONE,
          false));
      return root.getCursor();
    });
    assertEquals(expectedCursor, actualCursor);
  }

  private void clickNode(Node node, Stage stage) throws Exception {
    Bounds bounds = runFx(() -> node.localToScene(node.getBoundsInLocal()));
    double localX = Math.max(1.0, bounds.getWidth() / 2.0);
    double localY = Math.max(1.0, bounds.getHeight() / 2.0);
    runFx(() -> {
      Event.fireEvent(node, mouseEvent(
          MouseEvent.MOUSE_CLICKED,
          localX,
          localY,
          stage.getX() + bounds.getMinX() + localX,
          stage.getY() + bounds.getMinY() + localY,
          MouseButton.PRIMARY,
          false));
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();
  }

  private void clickMenuRow(VBox menuRoot, int rowIndex, Stage stage) throws Exception {
    Node row = runFx(() -> menuRoot.getChildren().get(rowIndex));
    runFx(() -> {
      Event.fireEvent(row, mouseEvent(
          MouseEvent.MOUSE_CLICKED,
          4.0,
          4.0,
          stage.getX() + 4.0,
          stage.getY() + 4.0,
          MouseButton.PRIMARY,
          false));
      return null;
    });
    WaitForAsyncUtils.waitForFxEvents();
  }

  private void clickMenuRowLater(VBox menuRoot, int rowIndex, Stage stage) throws Exception {
    Node row = runFx(() -> menuRoot.getChildren().get(rowIndex));
    Platform.runLater(() -> Event.fireEvent(row, mouseEvent(
        MouseEvent.MOUSE_CLICKED,
        4.0,
        4.0,
        stage.getX() + 4.0,
        stage.getY() + 4.0,
        MouseButton.PRIMARY,
        false)));
  }

  private Stage waitForOwnedDialog(Stage owner, String title) throws Exception {
    for (int attempt = 0; attempt < 80; attempt++) {
      Stage dialog = runFx(() -> Window.getWindows().stream()
          .filter(Window::isShowing)
          .filter(Stage.class::isInstance)
          .map(Stage.class::cast)
          .filter(stage -> stage != owner)
          .filter(stage -> stage.getOwner() == owner)
          .filter(stage -> title.equals(stage.getTitle()))
          .findFirst()
          .orElse(null));
      if (dialog != null) {
        return dialog;
      }
      Thread.sleep(50);
    }
    return null;
  }

  private Button dialogButton(Stage dialog, String text) throws Exception {
    return runFx(() -> findButton(dialog.getScene().getRoot(), text));
  }

  private TextField dialogTextField(Stage dialog) throws Exception {
    return runFx(() -> findNode(dialog.getScene().getRoot(), TextField.class));
  }

  @SuppressWarnings("unchecked")
  private ComboBox<SharePermission> dialogPermissionCombo(Stage dialog) throws Exception {
    return runFx(() -> (ComboBox<SharePermission>) findNode(dialog.getScene().getRoot(), ComboBox.class));
  }

  private Button findButton(Node node, String text) {
    if (node instanceof Button button && text.equals(button.getText())) {
      return button;
    }
    if (node instanceof Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) {
        Button button = findButton(child, text);
        if (button != null) {
          return button;
        }
      }
    }
    return null;
  }

  private <T extends Node> T findNode(Node node, Class<T> type) {
    if (type.isInstance(node)) {
      return type.cast(node);
    }
    if (node instanceof Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) {
        T result = findNode(child, type);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  private void fireButton(Button button) throws Exception {
    runFx(() -> {
      button.fire();
      return null;
    });
  }

  private boolean noteExists(MainWindowController mainController, NoteModel note) {
    return mainController.getStore().getNotes().stream()
        .anyMatch(candidate -> candidate.getId().equals(note.getId()));
  }

  private String menuRowLabel(VBox menuRoot, int rowIndex) throws Exception {
    return runFx(() -> {
      HBox row = (HBox) menuRoot.getChildren().get(rowIndex);
      return row.getChildren().stream()
          .filter(Label.class::isInstance)
          .map(Label.class::cast)
          .findFirst()
          .map(Label::getText)
          .orElse("");
    });
  }

  private boolean isDescendant(Node ancestor, Node node) {
    Node current = node;
    while (current != null) {
      if (current == ancestor) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private MouseEvent mouseEvent(
      EventType<MouseEvent> type,
      double sceneX,
      double sceneY,
      double screenX,
      double screenY,
      MouseButton button,
      boolean primaryButtonDown) {
    return new MouseEvent(
        type,
        sceneX,
        sceneY,
        screenX,
        screenY,
        button,
        1,
        false,
        false,
        false,
        false,
        primaryButtonDown,
        false,
        false,
        false,
        false,
        false,
        null);
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
