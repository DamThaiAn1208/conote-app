package com.conote.client.controller;

import com.conote.client.model.ChecklistItemModel;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.common.enums.NoteType;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.IconFactory;
import com.conote.client.util.MotionSupport;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class NoteCardController {
  private static final DateTimeFormatter CARD_DATE =
      DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
  private static final String ACTION_FADE_KEY = "noteCard.actionFade";
  private static final String SOLID_PIN_PATH =
      "M16 9V3H17V1H7V3H8V9C8 10.1 7.55 11.1 6.76 11.9L5 13.66V15H11V23H13V15H19V13.66L17.24 11.9C16.45 11.1 16 10.1 16 9Z";

  @FXML
  private VBox root;

  @FXML
  private Label titleLabel;

  @FXML
  private TextField titleField;

  @FXML
  private Label previewLabel;

  @FXML
  private VBox checklistPreviewBox;

  @FXML
  private StackPane expandedContainer;

  @FXML
  private VBox expandedContent;

  @FXML
  private VBox textEditorBox;

  @FXML
  private TextArea quickTextArea;

  @FXML
  private VBox checklistEditorBox;

  @FXML
  private VBox checklistItemsBox;

  @FXML
  private Button addChecklistItemButton;

  @FXML
  private FlowPane tagsFlow;

  @FXML
  private Label dateLabel;

  @FXML
  private Button pinButton;

  @FXML
  private Button openWindowButton;

  private final BooleanProperty expanded = new SimpleBooleanProperty(false);
  private final Rectangle expandedClip = new Rectangle();
  private NoteModel note;
  private CoNoteStore store;
  private MainWindowController mainController;
  private Timeline accordionTimeline;

  public void setContext(NoteModel note, CoNoteStore store, MainWindowController mainController) {
    this.note = note;
    this.store = store;
    this.mainController = mainController;

    expandedClip.widthProperty().bind(expandedContainer.widthProperty());
    expandedClip.heightProperty().bind(expandedContainer.maxHeightProperty());
    expandedContainer.setClip(expandedClip);
    MotionSupport.installCardMotion(root);
    MotionSupport.installButtonMotion(pinButton);
    MotionSupport.installButtonMotion(openWindowButton);
    MotionSupport.installButtonMotion(addChecklistItemButton);

    titleField.textProperty().addListener((obs, oldValue, newValue) -> store.updateTitle(note, newValue));
    quickTextArea.textProperty().addListener((obs, oldValue, newValue) -> {
      if (note.getType() == NoteType.TEXT) {
        store.updateContent(note, newValue);
        updateQuickTextHeight();
      }
    });

    store.expandedNoteIdProperty().addListener((obs, oldValue, newValue) -> syncExpandedState());
    store.themeProperty().addListener((obs, oldValue, newValue) -> refreshSurface());
    note.titleProperty().addListener((obs, oldValue, newValue) -> updateTitle());
    note.contentProperty().addListener((obs, oldValue, newValue) -> handleContentChanged());
    note.typeProperty().addListener((obs, oldValue, newValue) -> refreshFromModel());
    note.colorProperty().addListener((obs, oldValue, newValue) -> refreshSurface());
    note.createdAtProperty().addListener((obs, oldValue, newValue) -> refreshDate());
    note.pinnedProperty().addListener((obs, oldValue, newValue) -> syncPinState());
    note.getTags().addListener((ListChangeListener<String>) change -> refreshTags());
    note.getChecklistItems().addListener((ListChangeListener<ChecklistItemModel>) this::handleChecklistItemsChanged);

    configureActionButton(pinButton, note.pinnedProperty().or(root.hoverProperty()));
    configureActionButton(openWindowButton, root.hoverProperty());

    syncExpandedState();
    syncPinState();
    refreshFromModel();
  }

  @FXML
  private void handleCardClick(javafx.scene.input.MouseEvent event) {
    Object target = event.getTarget();
    if (target instanceof Button || target instanceof CheckBox || target instanceof TextInputControl) {
      return;
    }

    if (expanded.get()) {
      store.setExpandedNoteId(null);
    } else {
      store.setExpandedNoteId(note.getId());
    }
  }

  @FXML
  private void togglePin() {
    store.togglePin(note);
  }

  @FXML
  private void openNoteWindow() {
    mainController.openNoteWindow(note);
  }

  @FXML
  private void addChecklistItem() {
    store.addChecklistItem(note);
    renderChecklistRows();
  }

  private void syncExpandedState() {
    boolean selected = note.getId().equals(store.expandedNoteIdProperty().get());
    expanded.set(selected);
    titleLabel.setVisible(!selected);
    titleLabel.setManaged(!selected);
    titleField.setVisible(selected);
    titleField.setManaged(selected);
    refreshPreview();
    if (selected) {
      refreshEditorVisibility();
    }
    animateExpandedState(selected);
    if (!selected) {
      refreshEditorVisibility();
    }
    focusExpandedEditor(selected);
    if (selected && !root.getStyleClass().contains("note-card-selected")) {
      root.getStyleClass().add("note-card-selected");
    } else if (!selected) {
      root.getStyleClass().remove("note-card-selected");
    }
  }

  private void refreshFromModel() {
    updateTitle();
    refreshPreview();
    refreshDate();
    refreshTags();
    refreshSurface();
    refreshEditorVisibility();

    if (note.getType() == NoteType.TEXT) {
      syncQuickTextFromModel();
    } else {
      renderChecklistRows();
    }

    if (expanded.get()) {
      expandedContainer.setMaxHeight(measureExpandedHeight());
    }
  }

  private void refreshEditorVisibility() {
    boolean textExpanded = note.getType() == NoteType.TEXT && expanded.get();
    boolean checklistExpanded = note.getType() == NoteType.CHECKLIST && expanded.get();
    textEditorBox.setVisible(textExpanded);
    textEditorBox.setManaged(textExpanded);
    checklistEditorBox.setVisible(checklistExpanded);
    checklistEditorBox.setManaged(checklistExpanded);
  }

  private void updateTitle() {
    String title = note.getTitle() == null || note.getTitle().isBlank() ? "Untitled note" : note.getTitle();
    titleLabel.setText(title);
    if (!titleField.isFocused()) {
      titleField.setText(note.getTitle());
    }
  }

  private void refreshPreview() {
    boolean isTextNote = note.getType() == NoteType.TEXT;
    boolean showCollapsedPreview = !expanded.get();
    previewLabel.setVisible(isTextNote && showCollapsedPreview);
    previewLabel.setManaged(isTextNote && showCollapsedPreview);
    checklistPreviewBox.setVisible(!isTextNote && showCollapsedPreview);
    checklistPreviewBox.setManaged(!isTextNote && showCollapsedPreview);

    if (isTextNote) {
      String preview = note.getPreviewText();
      if (preview == null || preview.isBlank()) {
        preview = "Empty text note";
      }
      previewLabel.setText(preview);
      return;
    }

    renderChecklistPreview();
  }

  private void focusExpandedEditor(boolean selected) {
    if (!selected || note.getType() != NoteType.TEXT) {
      return;
    }

    Platform.runLater(() -> {
      quickTextArea.requestFocus();
      quickTextArea.positionCaret(quickTextArea.getText().length());
    });
  }

  private void refreshDate() {
    dateLabel.setText(CARD_DATE.format(Instant.ofEpochMilli(note.getCreatedAt()).atZone(ZoneId.systemDefault())));
  }

  private void refreshTags() {
    tagsFlow.getChildren().clear();
    for (String tag : note.getTags()) {
      LoadedView<TagChipController> view =
          ViewLoader.load("/fxml/shared/TagChip.fxml");
      view.controller().configure(tag, false, null, true);
      tagsFlow.getChildren().add(view.root());
    }
  }

  private void refreshSurface() {
    NoteColor color = note.getColor() == null ? NoteColor.DEFAULT : note.getColor();
    root.setStyle("-fx-background-color: " + color.surfaceForTheme(store.getTheme()) + ";");
  }

  private void syncPinState() {
    boolean pinned = note.isPinned();
    pinButton.setGraphic(createPinGraphic(pinned));
    if (pinned) {
      if (!pinButton.getStyleClass().contains("note-card-pin-active")) {
        pinButton.getStyleClass().add("note-card-pin-active");
      }
    } else {
      pinButton.getStyleClass().remove("note-card-pin-active");
    }
  }

  private void animateExpandedState(boolean selected) {
    if (accordionTimeline != null) {
      accordionTimeline.stop();
    }

    double targetHeight = selected ? measureExpandedHeight() : 0.0;
    if (selected) {
      expandedContainer.setVisible(true);
      expandedContainer.setManaged(true);
    }

    accordionTimeline = new Timeline(
        new KeyFrame(
            Duration.millis(selected ? 200 : 160),
            new KeyValue(expandedContainer.maxHeightProperty(), targetHeight, Interpolator.EASE_BOTH),
            new KeyValue(expandedContainer.opacityProperty(), selected ? 1.0 : 0.0, Interpolator.EASE_BOTH),
            new KeyValue(expandedContainer.translateYProperty(), selected ? 0.0 : -6.0, Interpolator.EASE_BOTH)));

    if (!selected) {
      accordionTimeline.setOnFinished(event -> {
        expandedContainer.setVisible(false);
        expandedContainer.setManaged(false);
      });
    }
    accordionTimeline.play();
  }

  private double measureExpandedHeight() {
    expandedContent.applyCss();
    expandedContent.layout();
    double targetHeight = expandedContent.prefHeight(root.getWidth());
    if (Double.isNaN(targetHeight) || targetHeight <= 0) {
      targetHeight = expandedContent.prefHeight(-1);
    }
    return Math.max(targetHeight, 0);
  }

  private void renderChecklistPreview() {
    checklistPreviewBox.getChildren().clear();
    int visibleItems = 0;
    for (ChecklistItemModel item : note.getChecklistItems()) {
      if (item.getText() == null || item.getText().isBlank()) {
        continue;
      }

      HBox row = new HBox(8);
      row.getStyleClass().add("checklist-preview-row");

      CheckBox checkBox = new CheckBox();
      checkBox.setFocusTraversable(false);
      checkBox.setMouseTransparent(true);
      checkBox.setSelected(item.isChecked());

      Label label = new Label(item.getText());
      label.getStyleClass().add("checklist-preview-text");
      HBox.setHgrow(label, Priority.ALWAYS);

      row.getChildren().addAll(checkBox, label);
      checklistPreviewBox.getChildren().add(row);
      visibleItems++;

      if (visibleItems == 3) {
        break;
      }
    }

    if (visibleItems == 0) {
      Label emptyLabel = new Label("Empty checklist");
      emptyLabel.getStyleClass().add("note-card-preview");
      checklistPreviewBox.getChildren().add(emptyLabel);
    }
  }

  private void renderChecklistRows() {
    checklistItemsBox.getChildren().clear();
    for (ChecklistItemModel item : note.getChecklistItems()) {
      checklistItemsBox.getChildren().add(buildChecklistRow(item));
    }
  }

  private void updateQuickTextHeight() {
    int lines = Math.max(4, quickTextArea.getText().split("\\R", -1).length);
    quickTextArea.setPrefRowCount(Math.min(10, lines + 1));
  }

  private void handleContentChanged() {
    if (note.getType() != NoteType.TEXT) {
      return;
    }
    refreshPreview();
    syncQuickTextFromModel();
  }

  private void handleChecklistItemsChanged(ListChangeListener.Change<? extends ChecklistItemModel> change) {
    boolean requiresRowRender = false;
    while (change.next()) {
      if (change.wasAdded() || change.wasRemoved() || change.wasPermutated() || change.wasReplaced()) {
        requiresRowRender = true;
      }
    }

    if (note.getType() != NoteType.CHECKLIST) {
      return;
    }

    renderChecklistPreview();
    if (requiresRowRender && expanded.get()) {
      renderChecklistRows();
    }
    if (expanded.get()) {
      expandedContainer.setMaxHeight(measureExpandedHeight());
    }
  }

  private void syncQuickTextFromModel() {
    if (!quickTextArea.isFocused() && !quickTextArea.getText().equals(note.getContent())) {
      quickTextArea.setText(note.getContent());
    }
    updateQuickTextHeight();
    if (expanded.get()) {
      expandedContainer.setMaxHeight(measureExpandedHeight());
    }
  }

  private HBox buildChecklistRow(ChecklistItemModel item) {
    HBox row = new HBox(8);
    row.getStyleClass().add("inline-checklist-row");

    CheckBox checkBox = new CheckBox();
    checkBox.setSelected(item.isChecked());
    checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (item.isChecked() != newValue) {
        item.setChecked(newValue);
        note.touch();
      }
    });
    item.checkedProperty().addListener((obs, oldValue, newValue) -> {
      if (checkBox.isSelected() != newValue) {
        checkBox.setSelected(newValue);
      }
    });

    TextField textField = new TextField(item.getText());
    textField.getStyleClass().add("inline-checklist-field");
    HBox.setHgrow(textField, Priority.ALWAYS);
    textField.textProperty().addListener((obs, oldValue, newValue) ->
        store.updateChecklistItemText(note, item, newValue));
    item.textProperty().addListener((obs, oldValue, newValue) -> {
      if (!textField.isFocused() && !textField.getText().equals(newValue)) {
        textField.setText(newValue);
      }
    });

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button removeButton = new Button();
    removeButton.getStyleClass().add("mini-icon-button");
    IconFactory.apply(removeButton, "codicon-close", 12, "mini-action-icon");
    removeButton.setText(null);
    MotionSupport.installButtonMotion(removeButton);
    removeButton.setOnAction(event -> store.removeChecklistItem(note, item));

    row.getChildren().addAll(checkBox, textField, spacer, removeButton);
    return row;
  }

  private void configureActionButton(Button button, ObservableBooleanValue visibleState) {
    button.setVisible(true);
    button.setManaged(true);
    button.setFocusTraversable(false);
    button.setPickOnBounds(true);
    button.setOpacity(visibleState.get() ? 1.0 : 0.0);
    button.setMouseTransparent(!visibleState.get());
    visibleState.addListener((obs, oldValue, newValue) -> animateActionButton(button, newValue));
  }

  private void animateActionButton(Button button, boolean visible) {
    FadeTransition existing = buttonFade(button);
    if (existing != null) {
      existing.stop();
    }

    if (visible) {
      button.setMouseTransparent(false);
    }

    FadeTransition fade = new FadeTransition(Duration.millis(130), button);
    fade.setFromValue(button.getOpacity());
    fade.setToValue(visible ? 1.0 : 0.0);
    if (!visible) {
      fade.setOnFinished(event -> button.setMouseTransparent(true));
    }
    button.getProperties().put(ACTION_FADE_KEY, fade);
    fade.playFromStart();
  }

  private FadeTransition buttonFade(Button button) {
    Object value = button.getProperties().get(ACTION_FADE_KEY);
    return value instanceof FadeTransition fade ? fade : null;
  }

  private Node createPinGraphic(boolean pinned) {
    if (!pinned) {
      return IconFactory.icon("codicon-pinned", 15, "card-action-icon", "pinned-icon");
    }

    SVGPath icon = new SVGPath();
    icon.setContent(SOLID_PIN_PATH);
    icon.setScaleX(0.56);
    icon.setScaleY(0.56);
    icon.getStyleClass().addAll("card-action-icon", "solid-pin-icon");
    return icon;
  }
}
