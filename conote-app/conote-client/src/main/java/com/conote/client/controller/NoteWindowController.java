package com.conote.client.controller;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
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
import javafx.scene.Parent;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

public class NoteWindowController {
  private static final double WINDOW_CORNER_RADIUS = 5.0;
  private static final double WINDOW_CORNER_ARC = WINDOW_CORNER_RADIUS * 2.0;
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
  private HBox windowTitleBar;

  @FXML
  private StackPane windowMinimizeButton;

  @FXML
  private StackPane windowCloseButton;

  @FXML
  private NoteToolbarController noteToolbarController;

  @FXML
  private NoteHeaderController noteHeaderController;

  @FXML
  private TextNoteEditorController textNoteEditorController;

  @FXML
  private ChecklistNoteEditorController checklistNoteEditorController;

  @FXML
  private FlowPane tagFlow;

  @FXML
  private TextField titleField;

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

  @FXML
  private void initialize() {
    MotionSupport.installButtonMotion(windowMinimizeButton);
    MotionSupport.installButtonMotion(windowCloseButton);
    titleField.setCursor(Cursor.TEXT);

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
  }

  public void setContext(NoteModel note, CoNoteStore store, Stage stage) {
    this.note = note;
    this.store = store;
    this.stage = stage;
    this.suppressPendingFlush = false;

    if (this.stage != null) {
      this.stage.setOnHiding(event -> flushPendingChanges());
    }

    overlayLayer.setVisible(false);
    overlayLayer.setManaged(false);
    overlayLayer.setMouseTransparent(true);

    noteHeaderController.setContext(note, store, this);
    titleField.setText(note.getTitle());
    titleField.textProperty().addListener((obs, oldValue, newValue) -> store.updateTitle(note, newValue));
    note.titleProperty().addListener((obs, oldValue, newValue) -> {
      if (!titleField.isFocused() && !titleField.getText().equals(newValue)) {
        titleField.setText(newValue);
      }
    });
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
    LoadedView<ShareDialogController> view =
        ViewLoader.load("/fxml/dialog/ShareDialog.fxml");
    view.controller().setOnClose(this::closeOverlay);
    view.controller().setStore(store);
    view.controller().setNote(note);
    showOverlay(view.root());
  }

  public void showConfirmDeleteDialog() {
    LoadedView<ConfirmDeleteDialogController> view =
        ViewLoader.load("/fxml/dialog/ConfirmDeleteDialog.fxml");
    view.controller().setNote(note);
    view.controller().setOnClose(this::closeOverlay);
    view.controller().setOnConfirm(this::deleteNote);
    showOverlay(view.root());
  }

  public void closeOverlay() {
    overlayLayer.getChildren().clear();
    overlayLayer.setVisible(false);
    overlayLayer.setManaged(false);
    overlayLayer.setMouseTransparent(true);
  }

  @FXML
  public void closeWindow() {
    flushPendingChanges();
    if (stage != null) {
      stage.close();
    }
  }

  @FXML
  private void minimizeWindow() {
    if (stage != null) {
      stage.setIconified(true);
    }
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
    if (stage != null) {
      stage.setX(event.getScreenX() - dragOffsetX);
      stage.setY(event.getScreenY() - dragOffsetY);
    }
  }

  private void refreshFromModel() {
    refreshEditorVisibility();
    refreshFooterTimes();
    refreshTags();
    refreshSurface();
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
}
