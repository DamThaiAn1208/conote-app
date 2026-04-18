package com.conote.client.controller;

import javafx.animation.Interpolator;
import com.conote.client.model.NoteModel;
import com.conote.common.enums.NoteType;
import com.conote.client.util.MotionSupport;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.util.Duration;

public class NoteToolbarController {
  static final double ROOT_WIDTH = 108.0;
  static final double HANDLE_WIDTH = 28.0;
  static final double COLLAPSED_ROOT_TRANSLATE = -(ROOT_WIDTH - HANDLE_WIDTH);
  static final double COLLAPSED_TOOL_TRANSLATE = -18.0;
  static final Duration SIDEBAR_ANIMATION_DURATION = Duration.millis(280);
  static final Interpolator SIDEBAR_INTERPOLATOR =
      Interpolator.SPLINE(0.22, 0.61, 0.36, 1.0);

  @FXML
  private StackPane root;

  @FXML
  private VBox toolRail;

  @FXML
  private Button resetTextStyleButton;

  @FXML
  private Separator formattingTopDivider;

  @FXML
  private VBox formattingGroup;

  @FXML
  private Separator formattingBottomDivider;

  @FXML
  private ToggleButton boldToggle;

  @FXML
  private ToggleButton italicToggle;

  @FXML
  private ToggleButton underlineToggle;

  @FXML
  private Button mediaButton;

  @FXML
  private Button sidebarToggleButton;

  @FXML
  private FontIcon sidebarToggleIcon;

  private TextNoteEditorController textNoteEditorController;
  private Consumer<Boolean> sidebarStateListener;
  private Consumer<Boolean> sidebarToggleHandler;
  private boolean collapsed;
  private final Rectangle toolRailClip = new Rectangle();

  @FXML
  private void initialize() {
    MotionSupport.installButtonMotion(resetTextStyleButton);
    MotionSupport.installButtonMotion(boldToggle);
    MotionSupport.installButtonMotion(italicToggle);
    MotionSupport.installButtonMotion(underlineToggle);
    MotionSupport.installButtonMotion(mediaButton);
    MotionSupport.installButtonMotion(sidebarToggleButton);

    toolRailClip.widthProperty().bind(root.widthProperty());
    toolRailClip.heightProperty().bind(toolRail.heightProperty());
    toolRail.setClip(toolRailClip);
  }

  public void setContext(NoteModel note, TextNoteEditorController textNoteEditorController) {
    this.textNoteEditorController = textNoteEditorController;

    updateVisibility(note.getType() == NoteType.TEXT);
    note.typeProperty().addListener((obs, oldValue, newValue) -> updateVisibility(newValue == NoteType.TEXT));
    applyTypography();
    applyCollapsedState(true);
  }

  public void setSidebarStateListener(Consumer<Boolean> sidebarStateListener) {
    this.sidebarStateListener = sidebarStateListener;
  }

  public void setSidebarToggleHandler(Consumer<Boolean> sidebarToggleHandler) {
    this.sidebarToggleHandler = sidebarToggleHandler;
  }

  @FXML
  private void resetTypography() {
    boldToggle.setSelected(false);
    italicToggle.setSelected(false);
    underlineToggle.setSelected(false);
    applyTypography();
  }

  @FXML
  private void toggleBold() {
    applyTypography();
  }

  @FXML
  private void toggleItalic() {
    applyTypography();
  }

  @FXML
  private void toggleUnderline() {
    applyTypography();
  }

  @FXML
  private void toggleMediaTool() {
    // Placeholder control for the image/media tool from the mockup.
  }

  @FXML
  private void toggleSidebar() {
    if (sidebarToggleHandler != null) {
      sidebarToggleHandler.accept(!collapsed);
    }
  }

  private void applyTypography() {
    if (textNoteEditorController != null) {
      textNoteEditorController.applyTypography(
          boldToggle.isSelected(), italicToggle.isSelected(), underlineToggle.isSelected());
    }
  }

  private void updateVisibility(boolean visible) {
    formattingGroup.setVisible(visible);
    formattingGroup.setManaged(visible);
    resetTextStyleButton.setVisible(visible);
    resetTextStyleButton.setManaged(visible);
    formattingTopDivider.setVisible(visible);
    formattingTopDivider.setManaged(visible);
    formattingBottomDivider.setVisible(visible);
    formattingBottomDivider.setManaged(visible);
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
        collapsed ? "codicon-chevron-right:16" : "codicon-chevron-left:16");
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
