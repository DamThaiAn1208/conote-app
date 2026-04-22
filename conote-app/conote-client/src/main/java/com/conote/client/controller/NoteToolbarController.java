package com.conote.client.controller;

import javafx.animation.Interpolator;
import com.conote.client.model.NoteModel;
import com.conote.common.enums.NoteType;
import com.conote.client.util.MotionSupport;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.util.Duration;

public class NoteToolbarController {
  static final double ROOT_WIDTH = 78.0;
  static final double HANDLE_WIDTH = 22.0;
  static final double PANEL_WIDTH = ROOT_WIDTH - HANDLE_WIDTH;
  static final double COLLAPSED_ROOT_TRANSLATE = -(ROOT_WIDTH - HANDLE_WIDTH);
  static final double COLLAPSED_TOOL_TRANSLATE = -12.0;
  static final Duration SIDEBAR_ANIMATION_DURATION = Duration.millis(280);
  static final Interpolator SIDEBAR_INTERPOLATOR =
      Interpolator.SPLINE(0.22, 0.61, 0.36, 1.0);

  @FXML
  private StackPane root;

  @FXML
  private VBox toolRail;

  @FXML
  private VBox formattingGroup;

  @FXML
  private ToggleButton boldToggle;

  @FXML
  private ToggleButton italicToggle;

  @FXML
  private ToggleButton underlineToggle;

  @FXML
  private ToggleButton strikethroughToggle;

  @FXML
  private Button sidebarToggleButton;

  @FXML
  private FontIcon sidebarToggleIcon;

  private TextNoteEditorController textNoteEditorController;
  private Consumer<Boolean> sidebarStateListener;
  private Consumer<Boolean> sidebarToggleHandler;
  private boolean collapsed;
  private boolean syncingFormattingState;
  private final Rectangle toolRailClip = new Rectangle();

  @FXML
  private void initialize() {
    MotionSupport.installButtonMotion(boldToggle);
    MotionSupport.installButtonMotion(italicToggle);
    MotionSupport.installButtonMotion(underlineToggle);
    MotionSupport.installButtonMotion(strikethroughToggle);

    sidebarToggleButton.setFocusTraversable(false);
    toolRailClip.widthProperty().bind(toolRail.widthProperty());
    toolRailClip.heightProperty().bind(toolRail.heightProperty());
    toolRail.setClip(toolRailClip);
  }

  public void setContext(NoteModel note, TextNoteEditorController textNoteEditorController) {
    this.textNoteEditorController = textNoteEditorController;
    this.textNoteEditorController.setFormattingStateListener(this::syncFormattingState);

    updateVisibility(note.getType() == NoteType.TEXT);
    note.typeProperty().addListener((obs, oldValue, newValue) ->
        updateVisibility(newValue == NoteType.TEXT));
    applyCollapsedState(true);
  }

  public void setSidebarStateListener(Consumer<Boolean> sidebarStateListener) {
    this.sidebarStateListener = sidebarStateListener;
  }

  public void setSidebarToggleHandler(Consumer<Boolean> sidebarToggleHandler) {
    this.sidebarToggleHandler = sidebarToggleHandler;
  }

  @FXML
  private void toggleBold() {
    if (!syncingFormattingState && textNoteEditorController != null) {
      textNoteEditorController.setBoldSelected(boldToggle.isSelected());
    }
  }

  @FXML
  private void toggleItalic() {
    if (!syncingFormattingState && textNoteEditorController != null) {
      textNoteEditorController.setItalicSelected(italicToggle.isSelected());
    }
  }

  @FXML
  private void toggleUnderline() {
    if (!syncingFormattingState && textNoteEditorController != null) {
      textNoteEditorController.setUnderlineSelected(underlineToggle.isSelected());
    }
  }

  @FXML
  private void toggleStrikethrough() {
    if (!syncingFormattingState && textNoteEditorController != null) {
      textNoteEditorController.setStrikethroughSelected(strikethroughToggle.isSelected());
    }
  }

  @FXML
  private void toggleSidebar() {
    if (sidebarToggleHandler != null) {
      sidebarToggleHandler.accept(!collapsed);
    }
  }

  private void updateVisibility(boolean visible) {
    formattingGroup.setVisible(visible);
    formattingGroup.setManaged(visible);
    if (!visible) {
      syncFormattingState(TextNoteEditorController.FormattingState.PLAIN);
    }
  }

  public void prepareSidebarAnimation(boolean collapsed) {
    this.collapsed = collapsed;
    updateToggleIcon(collapsed);
    toolRail.setVisible(true);
    toolRail.setManaged(true);
  }

  public void applyCollapsedState(boolean collapsed) {
    this.collapsed = collapsed;
    root.setTranslateX(collapsed ? COLLAPSED_ROOT_TRANSLATE : 0.0);
    toolRail.setVisible(true);
    toolRail.setManaged(true);
    toolRail.setOpacity(collapsed ? 0.0 : 1.0);
    toolRail.setTranslateX(collapsed ? COLLAPSED_TOOL_TRANSLATE : 0.0);
    updateToggleIcon(collapsed);
    updateSidebarState(collapsed);
  }

  public void finishSidebarAnimation(boolean collapsed) {
    this.collapsed = collapsed;
    toolRail.setVisible(true);
    toolRail.setManaged(true);
    updateSidebarState(collapsed);
  }

  public StackPane getRoot() {
    return root;
  }

  public VBox getToolRail() {
    return toolRail;
  }

  private void updateToggleIcon(boolean collapsed) {
    sidebarToggleIcon.setIconLiteral(
        collapsed ? "codicon-chevron-right:14" : "codicon-chevron-left:14");
  }

  private void syncFormattingState(TextNoteEditorController.FormattingState formattingState) {
    TextNoteEditorController.FormattingState state =
        formattingState == null ? TextNoteEditorController.FormattingState.PLAIN : formattingState;
    syncingFormattingState = true;
    try {
      boldToggle.setSelected(state.bold());
      italicToggle.setSelected(state.italic());
      underlineToggle.setSelected(state.underline());
      strikethroughToggle.setSelected(state.strikethrough());
    } finally {
      syncingFormattingState = false;
    }
  }

  private void updateSidebarState(boolean collapsed) {
    root.getStyleClass().remove("note-toolbar-collapsed");
    if (collapsed) {
      root.getStyleClass().add("note-toolbar-collapsed");
    }
    if (sidebarStateListener != null) {
      sidebarStateListener.accept(collapsed);
    }
  }
}
