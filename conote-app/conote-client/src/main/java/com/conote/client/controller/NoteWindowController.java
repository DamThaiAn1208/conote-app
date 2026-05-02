package com.conote.client.controller;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import com.conote.client.app.ClientApplication;
import com.conote.client.model.AppTheme;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.common.enums.NoteType;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.MotionSupport;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

public class NoteWindowController {
  static final double DEFAULT_WINDOW_WIDTH = 500.0;
  static final double DEFAULT_WINDOW_HEIGHT = 550.0;
  static final double MIN_WINDOW_WIDTH = 500.0;
  static final double MIN_WINDOW_HEIGHT = 550.0;
  private static final double WINDOW_CORNER_RADIUS = 5.0;
  private static final double WINDOW_CORNER_ARC = WINDOW_CORNER_RADIUS * 2.0;
  private static final double RESIZE_BORDER = 8.0;
  private static final double OVERFLOW_MENU_GAP = 6.0;
  private static final String SHARED_PERMISSION_VIEW_CLASS = "note-shared-permission-view";
  private static final String SHARED_PERMISSION_EDIT_CLASS = "note-shared-permission-edit";
  private static final DateTimeFormatter FOOTER_TIME =
      DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy", Locale.ENGLISH);

  @FXML
  private StackPane root;

  @FXML
  private StackPane windowFrame;

  @FXML
  private VBox windowShell;

  @FXML
  private VBox noteContentShell;

  @FXML
  private StackPane overlayLayer;

  @FXML
  private Pane noteSidebarSpace;

  @FXML
  private Pane resizeHandleLayer;

  @FXML
  private Region resizeNorthHandle;

  @FXML
  private Region resizeSouthHandle;

  @FXML
  private Region resizeWestHandle;

  @FXML
  private Region resizeEastHandle;

  @FXML
  private Region resizeNorthWestHandle;

  @FXML
  private Region resizeNorthEastHandle;

  @FXML
  private Region resizeSouthWestHandle;

  @FXML
  private Region resizeSouthEastHandle;

  @FXML
  private HBox windowTitleBar;

  @FXML
  private ToggleButton pinButton;

  @FXML
  private Button colorButton;

  @FXML
  private Button tagButton;

  @FXML
  private StackPane noteMenuButton;

  @FXML
  private StackPane windowMinimizeButton;

  @FXML
  private StackPane windowCloseButton;

  @FXML
  private NoteToolbarController noteToolbarController;

  @FXML
  private TextNoteEditorController textNoteEditorController;

  @FXML
  private ChecklistNoteEditorController checklistNoteEditorController;

  @FXML
  private FlowPane tagFlow;

  @FXML
  private TextField titleField;

  @FXML
  private FlowPane sharedInfoRow;

  @FXML
  private Label sharedByLabel;

  @FXML
  private Label sharedPermissionLabel;

  @FXML
  private ColorPickerPopoverController colorPickerPopoverController;

  @FXML
  private TagSelectorController tagSelectorController;

  @FXML
  private Label createdLabel;

  @FXML
  private Label updatedLabel;

  private NoteModel note;
  private CoNoteStore store;
  private Stage stage;
  private double dragOffsetX;
  private double dragOffsetY;
  private final Rectangle overlayClip = new Rectangle();
  private final Rectangle windowShellClip = new Rectangle();
  private ParallelTransition sidebarTransition;
  private boolean suppressPendingFlush;
  private ResizeDirection resizeDirection = ResizeDirection.NONE;
  private double resizeStartScreenX;
  private double resizeStartScreenY;
  private double resizeStartStageX;
  private double resizeStartStageY;
  private double resizeStartWidth;
  private double resizeStartHeight;
  private boolean resizing;
  private Popup overflowMenuPopup;
  private VBox overflowMenuRoot;

  @FXML
  private void initialize() {
    MotionSupport.installButtonMotion(pinButton);
    MotionSupport.installButtonMotion(colorButton);
    MotionSupport.installButtonMotion(tagButton);
    MotionSupport.installButtonMotion(noteMenuButton);
    MotionSupport.installButtonMotion(windowMinimizeButton);
    MotionSupport.installButtonMotion(windowCloseButton);
    titleField.setCursor(Cursor.TEXT);
    colorPickerPopoverController.setVisible(false);
    tagSelectorController.setVisible(false);
    initializeOverflowMenu();

    overlayLayer.setVisible(false);
    overlayLayer.setManaged(false);
    overlayLayer.setMouseTransparent(true);
    windowShellClip.widthProperty().bind(windowShell.widthProperty());
    windowShellClip.heightProperty().bind(windowShell.heightProperty());
    windowShellClip.setArcWidth(WINDOW_CORNER_ARC);
    windowShellClip.setArcHeight(WINDOW_CORNER_ARC);
    windowShell.setClip(windowShellClip);
    overlayClip.widthProperty().bind(windowFrame.widthProperty());
    overlayClip.heightProperty().bind(windowFrame.heightProperty());
    overlayClip.setArcWidth(WINDOW_CORNER_ARC);
    overlayClip.setArcHeight(WINDOW_CORNER_ARC);
    overlayLayer.setClip(overlayClip);
    installResizeHandle(resizeNorthHandle, ResizeDirection.NORTH);
    installResizeHandle(resizeSouthHandle, ResizeDirection.SOUTH);
    installResizeHandle(resizeWestHandle, ResizeDirection.WEST);
    installResizeHandle(resizeEastHandle, ResizeDirection.EAST);
    installResizeHandle(resizeNorthWestHandle, ResizeDirection.NORTH_WEST);
    installResizeHandle(resizeNorthEastHandle, ResizeDirection.NORTH_EAST);
    installResizeHandle(resizeSouthWestHandle, ResizeDirection.SOUTH_WEST);
    installResizeHandle(resizeSouthEastHandle, ResizeDirection.SOUTH_EAST);
    root.setCursor(Cursor.DEFAULT);
  }

  public void setContext(NoteModel note, CoNoteStore store, Stage stage) {
    this.note = note;
    this.store = store;
    this.stage = stage;
    this.suppressPendingFlush = false;

    if (this.stage != null) {
      this.stage.setResizable(true);
      this.stage.setOnHiding(event -> flushPendingChanges());
    }

    overlayLayer.setVisible(false);
    overlayLayer.setManaged(false);
    overlayLayer.setMouseTransparent(true);

    pinButton.setSelected(note.isPinned());
    note.pinnedProperty().addListener((obs, oldValue, newValue) -> pinButton.setSelected(newValue));
    colorPickerPopoverController.setContext(note, store);
    tagSelectorController.setContext(note, store);
    titleField.setText(note.getTitle());
    titleField.textProperty().addListener((obs, oldValue, newValue) -> store.updateTitle(note, newValue));
    note.titleProperty().addListener((obs, oldValue, newValue) -> {
      if (!titleField.isFocused() && !titleField.getText().equals(newValue)) {
        titleField.setText(newValue);
      }
    });
    note.mockSharedProperty().addListener((obs, oldValue, newValue) -> refreshSharedInfo());
    note.mockSharedByProperty().addListener((obs, oldValue, newValue) -> refreshSharedInfo());
    note.mockSharePermissionProperty().addListener((obs, oldValue, newValue) -> refreshSharedInfo());
    textNoteEditorController.setContext(note, store);
    checklistNoteEditorController.setContext(note, store);
    noteToolbarController.setSidebarStateListener(this::setSidebarCollapsed);
    noteToolbarController.setSidebarToggleHandler(this::playSidebarAnimation);
    noteToolbarController.setContext(note, textNoteEditorController);
    noteSidebarSpace.setMinWidth(NoteToolbarController.HANDLE_WIDTH);
    noteSidebarSpace.setPrefWidth(NoteToolbarController.HANDLE_WIDTH);
    noteSidebarSpace.setMaxWidth(NoteToolbarController.HANDLE_WIDTH);

    note.typeProperty().addListener((obs, oldValue, newValue) -> refreshEditorVisibility());
    note.colorProperty().addListener((obs, oldValue, newValue) -> refreshSurface());
    note.createdAtProperty().addListener((obs, oldValue, newValue) -> refreshFooterTimes());
    note.updatedAtProperty().addListener((obs, oldValue, newValue) -> refreshFooterTimes());
    note.getTags().addListener((ListChangeListener<String>) change -> refreshTags());
    store.themeProperty().addListener((obs, oldValue, newValue) -> applyTheme());

    applyTheme();
    refreshFromModel();
  }

  public void flushPendingEdits() {
    flushPendingChanges();
  }

  public void showShareDialog() {
    hideOverflowMenu();
    hideTopBarPopovers();
    LoadedView<ShareDialogController> view =
        ViewLoader.load("/fxml/dialog/ShareDialog.fxml");
    view.controller().setStore(store);
    view.controller().setNote(note);
    Stage dialogStage = createModalDialogStage("Share note", view.root());
    view.controller().setOnClose(dialogStage::close);
    dialogStage.showAndWait();
  }

  public void showConfirmDeleteDialog() {
    hideOverflowMenu();
    hideTopBarPopovers();
    LoadedView<ConfirmDeleteDialogController> view =
        ViewLoader.load("/fxml/dialog/ConfirmDeleteDialog.fxml");
    view.controller().setNote(note);
    Stage dialogStage = createModalDialogStage("Delete note?", view.root());
    view.controller().setOnClose(dialogStage::close);
    view.controller().setOnConfirm(() -> {
      dialogStage.close();
      deleteNote();
    });
    dialogStage.showAndWait();
  }

  public void closeOverlay() {
    overlayLayer.getChildren().clear();
    overlayLayer.setVisible(false);
    overlayLayer.setManaged(false);
    overlayLayer.setMouseTransparent(true);
  }

  @FXML
  public void closeWindow() {
    hideOverflowMenu();
    flushPendingChanges();
    if (stage != null) {
      stage.close();
    }
  }

  @FXML
  private void minimizeWindow() {
    hideOverflowMenu();
    if (stage != null) {
      stage.setIconified(true);
    }
  }

  @FXML
  private void togglePin() {
    store.togglePin(note);
  }

  @FXML
  private void toggleColorPicker() {
    hideOverflowMenu();
    boolean nextVisible = !colorPickerPopoverController.isVisible();
    colorPickerPopoverController.setVisible(nextVisible);
    if (nextVisible) {
      tagSelectorController.setVisible(false);
    }
  }

  @FXML
  private void toggleTagSelector() {
    hideOverflowMenu();
    boolean nextVisible = !tagSelectorController.isVisible();
    tagSelectorController.setVisible(nextVisible);
    if (nextVisible) {
      colorPickerPopoverController.setVisible(false);
    }
  }

  @FXML
  private void toggleOverflowMenu() {
    if (isOverflowMenuShowing()) {
      hideOverflowMenu();
      return;
    }
    showOverflowMenu();
  }

  @FXML
  private void rememberDragOffset(MouseEvent event) {
    if (stage != null) {
      dragOffsetX = event.getScreenX() - stage.getX();
      dragOffsetY = event.getScreenY() - stage.getY();
    }
  }

  @FXML
  private void dragWindow(MouseEvent event) {
    if (stage != null && !resizing) {
      stage.setX(event.getScreenX() - dragOffsetX);
      stage.setY(event.getScreenY() - dragOffsetY);
      updateOverflowMenuPosition();
    }
  }

  private void initializeOverflowMenu() {
    overflowMenuRoot = new VBox();
    overflowMenuRoot.getStyleClass().addAll("surface-shell", "note-overflow-menu");
    overflowMenuRoot.getStylesheets().add(ClientApplication.stylesheetUrl());
    overflowMenuRoot.setVisible(false);
    overflowMenuRoot.setManaged(false);
    overflowMenuRoot.getChildren().setAll(
        createOverflowMenuRow("codicon-live-share:15", "Share", false, event -> showShareDialog()),
        createOverflowMenuRow("codicon-trash:15", "Delete", true, event -> showConfirmDeleteDialog()));

    overflowMenuPopup = new Popup();
    overflowMenuPopup.setAutoFix(true);
    overflowMenuPopup.setAutoHide(true);
    overflowMenuPopup.setHideOnEscape(true);
    overflowMenuPopup.getContent().setAll(overflowMenuRoot);
    overflowMenuPopup.setOnHidden(event -> {
      if (overflowMenuRoot != null) {
        overflowMenuRoot.setVisible(false);
        overflowMenuRoot.setManaged(false);
      }
    });
  }

  private HBox createOverflowMenuRow(
      String iconLiteral,
      String labelText,
      boolean danger,
      javafx.event.EventHandler<MouseEvent> handler) {
    FontIcon icon = new FontIcon(iconLiteral);
    icon.getStyleClass().add("note-overflow-menu-icon");
    Label label = new Label(labelText);
    label.getStyleClass().add("note-overflow-menu-label");

    HBox row = new HBox(icon, label);
    row.getStyleClass().add("note-overflow-menu-row");
    if (danger) {
      row.getStyleClass().add("note-overflow-menu-row-danger");
    }
    row.setOnMouseClicked(handler);
    MotionSupport.installGentleButtonMotion(row);
    return row;
  }

  private void showOverflowMenu() {
    Stage owner = stageFor(noteMenuButton);
    if (owner == null || overflowMenuPopup == null || overflowMenuRoot == null) {
      return;
    }

    hideTopBarPopovers();
    overflowMenuRoot.setVisible(true);
    overflowMenuRoot.setManaged(true);
    overflowMenuRoot.applyCss();
    overflowMenuRoot.layout();
    double popupX = computeOverflowMenuX();
    double popupY = computeOverflowMenuY();

    if (overflowMenuPopup.isShowing()) {
      overflowMenuPopup.setX(popupX);
      overflowMenuPopup.setY(popupY);
      return;
    }

    overflowMenuPopup.show(owner, popupX, popupY);
  }

  private void hideOverflowMenu() {
    if (overflowMenuPopup != null) {
      overflowMenuPopup.hide();
    }
  }

  private boolean isOverflowMenuShowing() {
    return overflowMenuPopup != null && overflowMenuPopup.isShowing();
  }

  private void updateOverflowMenuPosition() {
    if (!isOverflowMenuShowing()) {
      return;
    }
    overflowMenuRoot.applyCss();
    overflowMenuRoot.layout();
    overflowMenuPopup.setX(computeOverflowMenuX());
    overflowMenuPopup.setY(computeOverflowMenuY());
  }

  private double computeOverflowMenuX() {
    Bounds buttonBounds = noteMenuButton.localToScreen(noteMenuButton.getBoundsInLocal());
    double popupWidth = overflowMenuRoot == null ? 0.0 : overflowMenuRoot.prefWidth(-1);
    return buttonBounds == null ? 0.0 : buttonBounds.getMaxX() - popupWidth;
  }

  private double computeOverflowMenuY() {
    Bounds buttonBounds = noteMenuButton.localToScreen(noteMenuButton.getBoundsInLocal());
    return buttonBounds == null ? 0.0 : buttonBounds.getMaxY() + OVERFLOW_MENU_GAP;
  }

  private Stage stageFor(Node node) {
    return node == null || node.getScene() == null ? null : (Stage) node.getScene().getWindow();
  }

  private void hideTopBarPopovers() {
    colorPickerPopoverController.setVisible(false);
    tagSelectorController.setVisible(false);
  }

  private void centerDialogOnOwner(Stage dialogStage) {
    if (dialogStage == null) {
      return;
    }
    if (stage == null) {
      dialogStage.centerOnScreen();
      return;
    }

    dialogStage.setX(stage.getX() + Math.max(0.0, (stage.getWidth() - dialogStage.getWidth()) / 2.0));
    dialogStage.setY(stage.getY() + Math.max(0.0, (stage.getHeight() - dialogStage.getHeight()) / 2.0));
  }

  private Stage createModalDialogStage(String title, Parent root) {
    Stage dialogStage = new Stage();
    dialogStage.initStyle(StageStyle.TRANSPARENT);
    if (stage != null) {
      dialogStage.initOwner(stage);
      dialogStage.initModality(Modality.WINDOW_MODAL);
    } else {
      dialogStage.initModality(Modality.APPLICATION_MODAL);
    }
    dialogStage.setTitle(title);
    dialogStage.setResizable(false);

    Scene scene = new Scene(root);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(ClientApplication.stylesheetUrl());
    dialogStage.setScene(scene);
    dialogStage.setOnShown(event -> centerDialogOnOwner(dialogStage));
    return dialogStage;
  }

  private void installResizeHandle(Region handle, ResizeDirection direction) {
    if (handle == null) {
      return;
    }
    handle.setCursor(direction.cursor());
    handle.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
      if (!resizing && stage != null && stage.isResizable()) {
        root.setCursor(direction.cursor());
      }
    });
    handle.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
      if (!resizing) {
        root.setCursor(Cursor.DEFAULT);
      }
    });
    handle.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> beginResize(direction, event));
    handle.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::resizeWindow);
    handle.addEventHandler(MouseEvent.MOUSE_RELEASED, this::finishResize);
  }

  private void beginResize(ResizeDirection direction, MouseEvent event) {
    if (stage == null || !stage.isResizable()) {
      return;
    }

    resizeDirection = direction == null ? ResizeDirection.NONE : direction;
    resizing = true;
    resizeStartScreenX = event.getScreenX();
    resizeStartScreenY = event.getScreenY();
    resizeStartStageX = stage.getX();
    resizeStartStageY = stage.getY();
    resizeStartWidth = stage.getWidth();
    resizeStartHeight = stage.getHeight();
    root.setCursor(resizeDirection.cursor());
    event.consume();
  }

  private void resizeWindow(MouseEvent event) {
    if (!resizing || stage == null) {
      return;
    }

    double deltaX = event.getScreenX() - resizeStartScreenX;
    double deltaY = event.getScreenY() - resizeStartScreenY;
    double minWidth = Math.max(stage.getMinWidth(), MIN_WINDOW_WIDTH);
    double minHeight = Math.max(stage.getMinHeight(), MIN_WINDOW_HEIGHT);

    double nextX = resizeStartStageX;
    double nextY = resizeStartStageY;
    double nextWidth = resizeStartWidth;
    double nextHeight = resizeStartHeight;

    if (resizeDirection.left()) {
      nextWidth = resizeStartWidth - deltaX;
      if (nextWidth < minWidth) {
        nextX = resizeStartStageX + (resizeStartWidth - minWidth);
        nextWidth = minWidth;
      } else {
        nextX = resizeStartStageX + deltaX;
      }
    } else if (resizeDirection.right()) {
      nextWidth = Math.max(minWidth, resizeStartWidth + deltaX);
    }

    if (resizeDirection.top()) {
      nextHeight = resizeStartHeight - deltaY;
      if (nextHeight < minHeight) {
        nextY = resizeStartStageY + (resizeStartHeight - minHeight);
        nextHeight = minHeight;
      } else {
        nextY = resizeStartStageY + deltaY;
      }
    } else if (resizeDirection.bottom()) {
      nextHeight = Math.max(minHeight, resizeStartHeight + deltaY);
    }

    stage.setX(nextX);
    stage.setY(nextY);
    stage.setWidth(nextWidth);
    stage.setHeight(nextHeight);
    event.consume();
  }

  private void finishResize(MouseEvent event) {
    if (!resizing) {
      return;
    }

    resizing = false;
    resizeDirection = ResizeDirection.NONE;
    root.setCursor(Cursor.DEFAULT);
    event.consume();
  }

  private void refreshFromModel() {
    refreshSharedInfo();
    refreshEditorVisibility();
    refreshFooterTimes();
    refreshTags();
    refreshSurface();
  }

  private void refreshSharedInfo() {
    boolean visible = note != null && note.hasMockSharedInfo();
    sharedInfoRow.setVisible(visible);
    sharedInfoRow.setManaged(visible);
    if (!visible) {
      return;
    }

    sharedByLabel.setText(note.getMockSharedDisplayText());
    sharedPermissionLabel.setText(note.getMockPermissionDisplayText());
    sharedPermissionLabel.getStyleClass().removeAll(
        SHARED_PERMISSION_VIEW_CLASS,
        SHARED_PERMISSION_EDIT_CLASS);
    sharedPermissionLabel.getStyleClass().add(
        note.mockShareCanEdit() ? SHARED_PERMISSION_EDIT_CLASS : SHARED_PERMISSION_VIEW_CLASS);
  }

  private void flushPendingChanges() {
    if (suppressPendingFlush || note == null || store == null) {
      return;
    }

    store.updateTitle(note, titleField == null ? null : titleField.getText());
    textNoteEditorController.flushPendingContent();
  }

  private void refreshEditorVisibility() {
    boolean isText = note.getType() == NoteType.TEXT;
    textNoteEditorController.setVisible(isText);
    checklistNoteEditorController.setVisible(!isText);
    refreshToolbarVisibility(isText);
  }

  private void refreshFooterTimes() {
    createdLabel.setText("Created: " + FOOTER_TIME.format(
        Instant.ofEpochMilli(note.getCreatedAt()).atZone(ZoneId.systemDefault())));
    updatedLabel.setText("Last edited: " + FOOTER_TIME.format(
        Instant.ofEpochMilli(note.getUpdatedAt()).atZone(ZoneId.systemDefault())));
  }

  private void refreshTags() {
    tagFlow.getChildren().clear();
    for (String tag : note.getTags()) {
      LoadedView<TagChipController> view =
          ViewLoader.load("/fxml/shared/TagChip.fxml");
      view.controller().configure(tag, false, null, true);
      tagFlow.getChildren().add(view.root());
    }
  }

  private void deleteNote() {
    closeOverlay();
    suppressPendingFlush = true;
    store.deleteNote(note);
    if (stage != null) {
      stage.close();
    }
  }

  private void showOverlay(Parent content) {
    overlayLayer.getChildren().setAll(content);
    overlayLayer.setVisible(true);
    overlayLayer.setManaged(true);
    overlayLayer.setMouseTransparent(false);
    content.toFront();
  }

  private void applyTheme() {
    root.getStyleClass().removeAll(AppTheme.LIGHT.cssClass(), AppTheme.DARK.cssClass());
    root.getStyleClass().add(store.getTheme().cssClass());
    refreshSurface();
  }

  private void setSidebarCollapsed(boolean collapsed) {
    root.getStyleClass().remove("sidebar-collapsed");
    if (collapsed) {
      root.getStyleClass().add("sidebar-collapsed");
    }
  }

  private void playSidebarAnimation(boolean collapsed) {
    if (note == null || note.getType() != NoteType.TEXT) {
      return;
    }

    if (sidebarTransition != null) {
      sidebarTransition.stop();
    }

    noteToolbarController.prepareSidebarAnimation(collapsed);

    Timeline toolbarTimeline =
        new Timeline(
            new KeyFrame(
                NoteToolbarController.SIDEBAR_ANIMATION_DURATION,
                new KeyValue(
                    noteToolbarController.getRoot().translateXProperty(),
                    collapsed ? NoteToolbarController.COLLAPSED_ROOT_TRANSLATE : 0.0,
                    NoteToolbarController.SIDEBAR_INTERPOLATOR),
                new KeyValue(
                    noteToolbarController.getToolRail().opacityProperty(),
                    collapsed ? 0.0 : 1.0,
                    NoteToolbarController.SIDEBAR_INTERPOLATOR),
                new KeyValue(
                    noteToolbarController.getToolRail().translateXProperty(),
                    collapsed ? NoteToolbarController.COLLAPSED_TOOL_TRANSLATE : 0.0,
                    NoteToolbarController.SIDEBAR_INTERPOLATOR)));

    Timeline sidebarSpaceTimeline =
        new Timeline(
            new KeyFrame(
                NoteToolbarController.SIDEBAR_ANIMATION_DURATION,
                new KeyValue(
                    noteSidebarSpace.minWidthProperty(),
                    collapsed ? NoteToolbarController.HANDLE_WIDTH : NoteToolbarController.ROOT_WIDTH,
                    NoteToolbarController.SIDEBAR_INTERPOLATOR),
                new KeyValue(
                    noteSidebarSpace.prefWidthProperty(),
                    collapsed ? NoteToolbarController.HANDLE_WIDTH : NoteToolbarController.ROOT_WIDTH,
                    NoteToolbarController.SIDEBAR_INTERPOLATOR),
                new KeyValue(
                    noteSidebarSpace.maxWidthProperty(),
                    collapsed ? NoteToolbarController.HANDLE_WIDTH : NoteToolbarController.ROOT_WIDTH,
                    NoteToolbarController.SIDEBAR_INTERPOLATOR)));

    sidebarTransition = new ParallelTransition(toolbarTimeline, sidebarSpaceTimeline);
    sidebarTransition.setOnFinished(event -> noteToolbarController.finishSidebarAnimation(collapsed));
    sidebarTransition.play();
  }

  private void refreshToolbarVisibility(boolean isText) {
    if (sidebarTransition != null) {
      sidebarTransition.stop();
      sidebarTransition = null;
    }

    noteToolbarController.setToolbarVisible(isText);
    if (!isText) {
      noteSidebarSpace.setVisible(false);
      noteSidebarSpace.setManaged(false);
      noteSidebarSpace.setMinWidth(0.0);
      noteSidebarSpace.setPrefWidth(0.0);
      noteSidebarSpace.setMaxWidth(0.0);
      return;
    }

    boolean collapsed = noteToolbarController.isCollapsed();
    double sidebarWidth = collapsed
        ? NoteToolbarController.HANDLE_WIDTH
        : NoteToolbarController.ROOT_WIDTH;
    noteSidebarSpace.setVisible(true);
    noteSidebarSpace.setManaged(true);
    noteSidebarSpace.setMinWidth(sidebarWidth);
    noteSidebarSpace.setPrefWidth(sidebarWidth);
    noteSidebarSpace.setMaxWidth(sidebarWidth);
    noteToolbarController.applyCollapsedState(collapsed);
  }

  private void refreshSurface() {
    NoteColor color = note.getColor() == null ? NoteColor.DEFAULT : note.getColor();
    String surface = color.surfaceForTheme(store.getTheme());
    if (store.getTheme() != AppTheme.DARK && color == NoteColor.DEFAULT) {
      surface = "#fff6d8";
    }
    noteContentShell.setStyle("-fx-background-color: " + surface + ";");
    textNoteEditorController.updatePalette(surface, store.getTheme());
  }

  private enum ResizeDirection {
    NONE(Cursor.DEFAULT, false, false, false, false),
    NORTH(Cursor.N_RESIZE, true, false, false, false),
    NORTH_EAST(Cursor.NE_RESIZE, true, false, false, true),
    EAST(Cursor.E_RESIZE, false, false, false, true),
    SOUTH_EAST(Cursor.SE_RESIZE, false, true, false, true),
    SOUTH(Cursor.S_RESIZE, false, true, false, false),
    SOUTH_WEST(Cursor.SW_RESIZE, false, true, true, false),
    WEST(Cursor.W_RESIZE, false, false, true, false),
    NORTH_WEST(Cursor.NW_RESIZE, true, false, true, false);

    private final Cursor cursor;
    private final boolean top;
    private final boolean bottom;
    private final boolean left;
    private final boolean right;

    ResizeDirection(Cursor cursor, boolean top, boolean bottom, boolean left, boolean right) {
      this.cursor = cursor;
      this.top = top;
      this.bottom = bottom;
      this.left = left;
      this.right = right;
    }

    private Cursor cursor() {
      return cursor;
    }

    private boolean top() {
      return top;
    }

    private boolean bottom() {
      return bottom;
    }

    private boolean left() {
      return left;
    }

    private boolean right() {
      return right;
    }
  }
}
