package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import com.conote.common.enums.NoteType;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class NoteToolbarLayoutTest {
  private NoteToolbarController controller;
  private StackPane root;
  private VBox toolRail;
  private Button sidebarToggleButton;
  private FontIcon sidebarToggleIcon;

  @Start
  private void start(Stage stage) {
    LoadedView<NoteToolbarController> view = ViewLoader.load("/fxml/note/NoteToolbar.fxml");
    controller = view.controller();
    root = (StackPane) view.root();

    NoteModel note = new NoteModel(
        "toolbar-test-note",
        NoteType.TEXT,
        "Toolbar Test",
        "Toolbar content",
        NoteColor.DEFAULT,
        false,
        System.currentTimeMillis(),
        System.currentTimeMillis());
    controller.setContext(note, new TextNoteEditorController());

    Scene scene = new Scene(root, NoteToolbarController.ROOT_WIDTH, 360);
    scene.getStylesheets().add(ClientApplication.stylesheetUrl());

    stage.setScene(scene);
    stage.show();
    stage.toFront();
    stage.requestFocus();

    root.applyCss();
    root.layout();

    toolRail = (VBox) root.lookup("#toolRail");
    sidebarToggleButton = (Button) root.lookup("#sidebarToggleButton");
    sidebarToggleIcon = (FontIcon) root.lookup("#sidebarToggleIcon");
  }

  @Test
  void toggleButtonSitsFlushAgainstToolbarEdgeWhenOpen(FxRobot robot) {
    robot.interact(() -> {
      controller.applyCollapsedState(false);
      root.applyCss();
      root.layout();
    });
    WaitForAsyncUtils.waitForFxEvents();

    Bounds railBounds = toolRail.localToScene(toolRail.getBoundsInLocal());
    Bounds buttonBounds = sidebarToggleButton.localToScene(sidebarToggleButton.getBoundsInLocal());
    Bounds rootBounds = root.localToScene(root.getBoundsInLocal());

    assertEquals(railBounds.getMaxX(), buttonBounds.getMinX(), 0.75,
        "The toggle tab should start exactly at the outer edge of the toolbar rail");
    assertEquals(rootBounds.getMaxX(), buttonBounds.getMaxX(), 0.75,
        "The toggle tab should remain fully outside the rail and flush with the shell edge");
  }

  @Test
  void toggleButtonKeepsStablePositionAcrossOpenAndClosedStates(FxRobot robot) {
    double openLayoutX = openAndMeasureButtonLayoutX(robot);
    double collapsedLayoutX = collapseAndMeasureButtonLayoutX(robot);

    assertEquals(NoteToolbarController.PANEL_WIDTH, openLayoutX, 0.01,
        "The open tab should stay anchored right after the toolbar panel");
    assertEquals(openLayoutX, collapsedLayoutX, 0.01,
        "The tab should not jump inside the toolbar when collapsing");
    assertEquals(NoteToolbarController.COLLAPSED_ROOT_TRANSLATE, root.getTranslateX(), 0.01,
        "Collapsed state should preserve the existing root translation behavior");
  }

  @Test
  void toggleButtonUsesMergedBorderCenteredIconAndNoHoverShift(FxRobot robot) {
    robot.interact(() -> {
      controller.applyCollapsedState(false);
      root.applyCss();
      root.layout();
    });
    WaitForAsyncUtils.waitForFxEvents();

    assertNotNull(sidebarToggleButton.getBorder(), "The toggle tab should have a visible border");
    assertEquals(1.0, sidebarToggleButton.getBorder().getStrokes().get(0).getWidths().getTop(), 0.01);
    assertEquals(1.0, sidebarToggleButton.getBorder().getStrokes().get(0).getWidths().getRight(), 0.01);
    assertEquals(1.0, sidebarToggleButton.getBorder().getStrokes().get(0).getWidths().getBottom(), 0.01);
    assertEquals(0.0, sidebarToggleButton.getBorder().getStrokes().get(0).getWidths().getLeft(), 0.01,
        "The border side touching the toolbar should be removed");

    Bounds buttonBoundsBeforeHover = sidebarToggleButton.localToScene(sidebarToggleButton.getBoundsInLocal());
    robot.moveTo(sidebarToggleButton);
    WaitForAsyncUtils.waitForFxEvents();
    Bounds buttonBoundsAfterHover = sidebarToggleButton.localToScene(sidebarToggleButton.getBoundsInLocal());
    Bounds iconBounds = sidebarToggleIcon.localToScene(sidebarToggleIcon.getBoundsInLocal());

    assertEquals(1.0, sidebarToggleButton.getScaleX(), 0.001,
        "Hover should not scale the toggle tab");
    assertEquals(1.0, sidebarToggleButton.getScaleY(), 0.001,
        "Hover should not scale the toggle tab");
    assertEquals(0.0, sidebarToggleButton.getTranslateY(), 0.001,
        "Hover should not shift the toggle tab vertically");
    assertEquals(buttonBoundsBeforeHover.getMinX(), buttonBoundsAfterHover.getMinX(), 0.01,
        "Hover should not move the toggle tab horizontally");
    assertEquals(buttonBoundsBeforeHover.getMinY(), buttonBoundsAfterHover.getMinY(), 0.01,
        "Hover should not move the toggle tab vertically");
    assertEquals(buttonBoundsAfterHover.getCenterX(), iconBounds.getCenterX(), 1.0,
        "The arrow icon should stay horizontally centered inside the tab");
    assertEquals(buttonBoundsAfterHover.getCenterY(), iconBounds.getCenterY(), 1.0,
        "The arrow icon should stay vertically centered inside the tab");
    assertTrue(sidebarToggleIcon.getIconLiteral().contains("chevron-left"),
        "Open state should keep the expected directional icon");
  }

  private double openAndMeasureButtonLayoutX(FxRobot robot) {
    robot.interact(() -> {
      controller.applyCollapsedState(false);
      root.applyCss();
      root.layout();
    });
    WaitForAsyncUtils.waitForFxEvents();
    return sidebarToggleButton.getLayoutX();
  }

  private double collapseAndMeasureButtonLayoutX(FxRobot robot) {
    robot.interact(() -> {
      controller.applyCollapsedState(true);
      root.applyCss();
      root.layout();
    });
    WaitForAsyncUtils.waitForFxEvents();
    return sidebarToggleButton.getLayoutX();
  }
}
