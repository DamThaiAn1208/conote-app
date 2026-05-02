package com.conote.client.controller;

import com.conote.client.model.AppTheme;
import com.conote.client.model.ChecklistItemModel;
import com.conote.client.model.NoteColor;
import com.conote.client.model.NoteModel;
import com.conote.common.enums.NoteType;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.IconFactory;
import com.conote.client.util.LoadedView;
import com.conote.client.util.MotionSupport;
import com.conote.client.util.RichTextContentCodec;
import com.conote.client.util.RichTextContentCodec.Segment;
import com.conote.client.util.RichTextContentCodec.TextStyle;
import com.conote.client.util.ViewLoader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class NoteCardController {
  private static final String UNTITLED_NOTE_PLACEHOLDER = "Untitled note";
  private static final String EMPTY_TEXT_NOTE_PLACEHOLDER = "Empty text note";
  private static final String EMPTY_CHECKLIST_PLACEHOLDER = "Empty checklist";
  private static final int COLLAPSED_TEXT_PREVIEW_LINES = 3;
  private static final int MIN_EXPANDED_TEXT_LINES = 3;
  private static final int MAX_EXPANDED_TEXT_LINES = 12;
  private static final double FALLBACK_TEXT_WIDTH = 220.0;
  private static final String CHECKLIST_ITEM_CHECKED_CLASS = "checklist-item-checked";
  private static final String PREVIEW_TEXT_COLOR_LIGHT = "#64748b";
  private static final String PREVIEW_TEXT_COLOR_DARK = "#cbd5e1";
  private static final String EXPANDED_TEXT_COLOR_LIGHT = "#334155";
  private static final String EXPANDED_TEXT_COLOR_DARK = "#f8fafc";
  private static final String SOLID_PIN_PATH =
      "M16 9V3H17V1H7V3H8V9C8 10.1 7.55 11.1 6.76 11.9L5 13.66V15H11V23H13V15H19V13.66L17.24 11.9C16.45 11.1 16 10.1 16 9Z";
  private static final DateTimeFormatter CARD_DATE =
      DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
  private static final String ACTION_FADE_KEY = "noteCard.actionFade";
  private static final Duration TEXT_SWAP_DURATION = Duration.millis(120);
  private static final Duration QUICK_TEXT_COMMIT_DELAY = Duration.millis(180);
  private static final Duration QUICK_TEXT_LAYOUT_DELAY = Duration.millis(70);
  private static final double TEXT_SWAP_OFFSET = 2.5;
  private static final double TEXT_EDITOR_ALIGNMENT_OFFSET = -2.0;

  private enum RichTokenType {
    WORD,
    SPACE,
    NEWLINE
  }

  private record StyledToken(String text, TextStyle style, RichTokenType type) {
  }

  private enum ChecklistFocusRole {
    CHECKBOX,
    TEXT_FIELD,
    REMOVE_BUTTON,
    ADD_BUTTON
  }

  private record ChecklistFocusTarget(ChecklistItemModel item, ChecklistFocusRole role, int index) {
  }

  @FXML
  private VBox root;

  @FXML
  private Label titleLabel;

  @FXML
  private TextField titleField;

  @FXML
  private Label previewLabel;

  @FXML
  private StackPane textContentStack;

  @FXML
  private TextFlow richPreviewFlow;

  @FXML
  private VBox richTextEditorBox;

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
  private HBox sharedSourceBadge;

  @FXML
  private Label sharedSourceLabel;

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
  private boolean syncingTitleField;
  private boolean editingEmptyTitle;
  private boolean syncingQuickTextFromModel;
  private boolean quickTextDirty;
  private boolean committingQuickTextChange;
  private final Text textMeasure = new Text();
  private TextNoteEditorController richTextEditorController;
  private Timeline textContentTimeline;
  private final PauseTransition quickTextCommitDelay = new PauseTransition(QUICK_TEXT_COMMIT_DELAY);
  private final PauseTransition quickTextLayoutDelay = new PauseTransition(QUICK_TEXT_LAYOUT_DELAY);
  private ChecklistFocusTarget pendingChecklistFocusTarget;
  private Node pendingTabRedirectTarget;
  private Scene attachedScene;
  private final ChangeListener<Node> sceneFocusOwnerListener = (obs, oldValue, newValue) -> {
    if (pendingTabRedirectTarget == null) {
      return;
    }
    if (isSameNodeOrDescendant(pendingTabRedirectTarget, newValue)) {
      pendingTabRedirectTarget = null;
      return;
    }
    Platform.runLater(() -> {
      if (pendingTabRedirectTarget != null
          && !isSameNodeOrDescendant(
              pendingTabRedirectTarget,
              root.getScene() == null ? null : root.getScene().getFocusOwner())) {
        focusTabTarget(pendingTabRedirectTarget);
      }
    });
  };

  public void setContext(NoteModel note, CoNoteStore store, MainWindowController mainController) {
    this.note = note;
    this.store = store;
    this.mainController = mainController;

    expandedClip.widthProperty().bind(expandedContainer.widthProperty());
    expandedClip.heightProperty().bind(expandedContainer.maxHeightProperty());
    expandedContainer.setClip(expandedClip);
    root.setFocusTraversable(true);
    root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleTabTraversal);
    root.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleCollapsedCardPress);
    root.focusedProperty().addListener((obs, oldValue, newValue) -> {
      if (newValue && pendingTabRedirectTarget != null) {
        requestFocusForTabTarget(pendingTabRedirectTarget);
      }
    });
    previewLabel.setFocusTraversable(false);
    richPreviewFlow.setFocusTraversable(false);
    previewLabel.setPickOnBounds(true);
    richPreviewFlow.setPickOnBounds(true);
    richPreviewFlow.setLineSpacing(0);
    previewLabel.setOnMousePressed(this::handleCollapsedTextPreviewClick);
    richPreviewFlow.setOnMousePressed(this::handleCollapsedTextPreviewClick);
    MotionSupport.installCardMotion(root, expanded);
    MotionSupport.installButtonMotion(addChecklistItemButton);
    sharedSourceLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
    root.hoverProperty().addListener((obs, oldValue, newValue) -> refreshActionButtonsVisibility());
    titleField.setFocusTraversable(true);
    pinButton.setFocusTraversable(true);
    openWindowButton.setFocusTraversable(true);
    quickTextArea.setFocusTraversable(true);
    quickTextArea.setTextFormatter(new TextFormatter<>(change ->
        change.getText() != null && change.getText().contains("\t") ? null : change));
    quickTextArea.addEventFilter(KeyEvent.KEY_TYPED, this::consumeTabCharacterInsertion);
    addChecklistItemButton.setFocusTraversable(true);
    registerLoopingTabStop(titleField);
    registerLoopingTabStop(pinButton);
    registerLoopingTabStop(openWindowButton);
    registerLoopingTabStop(quickTextArea);
    registerLoopingTabStop(addChecklistItemButton);
    titleField.setPromptText(UNTITLED_NOTE_PLACEHOLDER);
    quickTextArea.setPromptText(EMPTY_TEXT_NOTE_PLACEHOLDER);
    textEditorBox.setTranslateX(TEXT_EDITOR_ALIGNMENT_OFFSET);
    quickTextArea.skinProperty().addListener((obs, oldValue, newValue) ->
        Platform.runLater(() -> {
          normalizeQuickTextInsets();
          installQuickTextAreaTabGuards();
          scheduleQuickTextHeightRefresh();
        }));
    Platform.runLater(this::installQuickTextAreaTabGuards);
    quickTextCommitDelay.setOnFinished(event -> flushPendingQuickTextContent());
    quickTextLayoutDelay.setOnFinished(event -> updateQuickTextHeight());
    titleField.focusedProperty().addListener((obs, oldValue, newValue) -> {
      if (!newValue && editingEmptyTitle && !hasUserTitle()) {
        editingEmptyTitle = false;
        applyExpandedVisualState();
      }
    });

    titleField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (!syncingTitleField && titleField.isFocused()) {
        store.updateTitle(note, newValue);
      }
    });
    quickTextArea.textProperty().addListener((obs, oldValue, newValue) -> {
      if (note.getType() == NoteType.TEXT && quickTextArea.isEditable() && !syncingQuickTextFromModel) {
        quickTextDirty = true;
        scheduleQuickTextPersist();
        scheduleQuickTextHeightRefresh();
      }
    });
    quickTextArea.focusedProperty().addListener((obs, oldValue, newValue) -> {
      if (newValue) {
        scheduleQuickTextHeightRefresh();
        return;
      }
      flushPendingQuickTextContent();
    });

    store.expandedNoteIdProperty().addListener((obs, oldValue, newValue) -> syncExpandedState());
    store.themeProperty().addListener((obs, oldValue, newValue) -> {
      refreshSurface();
      refreshPreview();
    });
    note.titleProperty().addListener((obs, oldValue, newValue) -> updateTitle());
    note.contentProperty().addListener((obs, oldValue, newValue) -> handleContentChanged());
    note.typeProperty().addListener((obs, oldValue, newValue) -> refreshFromModel());
    note.colorProperty().addListener((obs, oldValue, newValue) -> refreshSurface());
    note.createdAtProperty().addListener((obs, oldValue, newValue) -> refreshDate());
    note.pinnedProperty().addListener((obs, oldValue, newValue) -> syncPinState());
    note.ownerNameProperty().addListener((obs, oldValue, newValue) -> refreshSourceBadge());
    note.sharedByNameProperty().addListener((obs, oldValue, newValue) -> refreshSourceBadge());
    note.sharedProperty().addListener((obs, oldValue, newValue) -> refreshSourceBadge());
    note.getTags().addListener((ListChangeListener<String>) change -> refreshTags());
    note.getChecklistItems().addListener((ListChangeListener<ChecklistItemModel>) this::handleChecklistItemsChanged);
    textContentStack.widthProperty().addListener((obs, oldValue, newValue) -> refreshTextPresentation());
    checklistPreviewBox.widthProperty().addListener((obs, oldValue, newValue) -> {
      if (note.getType() == NoteType.CHECKLIST && !expanded.get()) {
        renderChecklistPreview();
      }
    });
    root.sceneProperty().addListener((obs, oldValue, newValue) -> {
      if (oldValue != null && newValue == null) {
        flushPendingQuickTextContent();
      }
      detachSceneFocusOwnerListener(oldValue);
      attachSceneFocusOwnerListener(newValue);
      if (newValue != null) {
        Platform.runLater(this::refreshPreview);
        Platform.runLater(this::refreshInitialPinGraphic);
        Platform.runLater(this::refreshActionButtonsVisibility);
        Platform.runLater(() -> {
          normalizeQuickTextInsets();
          scheduleQuickTextHeightRefresh();
        });
      }
    });
    if (root.getScene() != null) {
      attachSceneFocusOwnerListener(root.getScene());
    }

    configureActionButton(pinButton);
    configureActionButton(openWindowButton);

    syncExpandedState();
    syncPinState();
    refreshFromModel();
    refreshActionButtonsVisibility();
    Platform.runLater(this::refreshInitialPinGraphic);
  }

  public void flushPendingChanges() {
    flushPendingQuickTextContent();
    if (richTextEditorController != null) {
      richTextEditorController.flushPendingContent();
    }
  }

  @FXML
  private void handleCardClick(javafx.scene.input.MouseEvent event) {
    if (isInteractiveTarget(event.getTarget())) {
      return;
    }

    if (expanded.get() && event.getTarget() == titleLabel && !hasUserTitle()) {
      beginEditingEmptyTitle();
      event.consume();
      return;
    }

    store.setExpandedNoteId(note.getId());
    Platform.runLater(root::requestFocus);
  }

  private void handleCollapsedCardPress(MouseEvent event) {
    if (expanded.get() || isInteractiveTarget(event.getTarget())) {
      return;
    }

    store.setExpandedNoteId(note.getId());
    event.consume();
  }

  private void handleCollapsedTextPreviewClick(MouseEvent event) {
    if (expanded.get() || isInteractiveTarget(event.getTarget())) {
      return;
    }

    store.setExpandedNoteId(note.getId());
    event.consume();
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
    ChecklistItemModel newItem = store.addChecklistItem(note);
    pendingChecklistFocusTarget =
        new ChecklistFocusTarget(newItem, ChecklistFocusRole.TEXT_FIELD, note.getChecklistItems().size() - 1);
  }

  private void syncExpandedState() {
    boolean selected = note.getId().equals(store.expandedNoteIdProperty().get());
    boolean stateChanged = expanded.get() != selected;
    if (!selected) {
      flushPendingQuickTextContent();
    }
    expanded.set(selected);

    if (selected) {
      applyExpandedVisualState(stateChanged);
      if (note.getType() == NoteType.CHECKLIST) {
        animateExpandedState(true);
      } else {
        resetCollapsedContainerState();
      }
    } else if (note.getType() == NoteType.CHECKLIST && isExpandedContainerOpen()) {
      animateExpandedState(false);
    } else {
      applyCollapsedVisualState(stateChanged);
      resetCollapsedContainerState();
    }

    refreshActionButtonsVisibility();
    focusExpandedEditor(selected);
  }

  private void refreshFromModel() {
    updateTitle();
    refreshPreview();
    refreshDate();
    refreshTags();
    refreshSourceBadge();
    refreshSurface();
    syncQuickTextMode();
    refreshEditorVisibility();

    if (note.getType() == NoteType.TEXT) {
      syncQuickTextFromModel();
    } else {
      renderChecklistRows();
    }

    if (expanded.get() && note.getType() == NoteType.CHECKLIST) {
      expandedContainer.setMaxHeight(measureExpandedHeight());
    }
  }

  private void refreshEditorVisibility() {
    boolean isTextNote = note.getType() == NoteType.TEXT;
    boolean richText = isTextNote && note.hasRichTextFormatting();
    boolean showTextPreview = isTextNote && !expanded.get() && !richText;
    boolean showRichPreview = isTextNote && !expanded.get() && richText;
    boolean showRichEditor = isTextNote && expanded.get() && richText;
    boolean textExpanded = note.getType() == NoteType.TEXT && expanded.get() && !richText;
    boolean checklistCollapsed = note.getType() == NoteType.CHECKLIST && !expanded.get();
    boolean checklistExpanded = note.getType() == NoteType.CHECKLIST && expanded.get();
    textContentStack.setVisible(isTextNote);
    textContentStack.setManaged(isTextNote);
    previewLabel.setVisible(showTextPreview);
    previewLabel.setManaged(showTextPreview);
    richPreviewFlow.setVisible(showRichPreview);
    richPreviewFlow.setManaged(showRichPreview);
    richTextEditorBox.setVisible(showRichEditor);
    richTextEditorBox.setManaged(showRichEditor);
    textEditorBox.setVisible(textExpanded);
    textEditorBox.setManaged(textExpanded);
    checklistPreviewBox.setVisible(checklistCollapsed);
    checklistPreviewBox.setManaged(checklistCollapsed);
    checklistEditorBox.setVisible(checklistExpanded);
    checklistEditorBox.setManaged(checklistExpanded);
    if (textExpanded) {
      scheduleQuickTextHeightRefresh();
    } else {
      quickTextLayoutDelay.stop();
    }
    if (showRichEditor) {
      ensureRichTextEditor();
    }
    if (richTextEditorController != null) {
      richTextEditorController.setVisible(showRichEditor);
      if (showRichEditor) {
        richTextEditorController.scrollToStart();
      }
    }
  }

  private void updateTitle() {
    String title = hasUserTitle() ? note.getTitle() : UNTITLED_NOTE_PLACEHOLDER;
    titleLabel.setText(title);
    syncTitlePlaceholderStyle();
    if (!titleField.isFocused()) {
      syncTitleFieldFromModel();
    }
  }

  private void refreshPreview() {
    boolean isTextNote = note.getType() == NoteType.TEXT;

    if (isTextNote) {
      refreshTextPresentation();
      return;
    }

    if (!expanded.get()) {
      renderChecklistPreview();
    }
  }

  private void focusExpandedEditor(boolean selected) {
    if (!selected) {
      return;
    }

    Platform.runLater(() -> {
      if (note.getType() == NoteType.TEXT) {
        resetExpandedTextViewportToStart();
        normalizeQuickTextInsets();
      }
      root.requestFocus();
    });
  }

  private void refreshDate() {
    dateLabel.setText(CARD_DATE.format(Instant.ofEpochMilli(note.getCreatedAt()).atZone(ZoneId.systemDefault())));
  }

  private void refreshSourceBadge() {
    boolean showBadge = note != null && note.isShared();
    sharedSourceBadge.setVisible(showBadge);
    sharedSourceBadge.setManaged(showBadge);
    if (!showBadge) {
      sharedSourceLabel.setText("");
      return;
    }

    String displayName = note.getSharedByName();
    if (displayName == null || displayName.isBlank()) {
      displayName = note.getOwnerName();
    }
    sharedSourceLabel.setText(displayName == null || displayName.isBlank() ? "Shared" : displayName.trim());
  }

  private void refreshTags() {
    tagsFlow.getChildren().clear();
    for (String tag : note.getTags()) {
      LoadedView<TagChipController> view =
          ViewLoader.load("/fxml/shared/TagChip.fxml");
      view.controller().configure(tag, false, null, true);
      view.root().setMouseTransparent(true);
      view.root().setFocusTraversable(false);
      tagsFlow.getChildren().add(view.root());
    }
  }

  private void refreshSurface() {
    String surfaceColor = surfaceColor();
    root.setStyle("-fx-background-color: " + surfaceColor + ";");
    if (richTextEditorController != null) {
      richTextEditorController.updatePalette(surfaceColor, currentTheme());
    }
  }

  private void syncPinState() {
    boolean pinned = note.isPinned();
    ensurePinGraphic();
    if (pinned) {
      if (!pinButton.getStyleClass().contains("note-card-pin-active")) {
        pinButton.getStyleClass().add("note-card-pin-active");
      }
    } else {
      pinButton.getStyleClass().remove("note-card-pin-active");
    }
    refreshActionButtonsVisibility();
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
        resetCollapsedContainerState();
        applyCollapsedVisualState();
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
    Label preview = new Label(buildChecklistPreviewText());
    preview.getStyleClass().addAll("note-card-preview", "checklist-preview-inline");
    preview.setWrapText(true);
    preview.setMouseTransparent(true);
    preview.setFocusTraversable(false);
    checklistPreviewBox.getChildren().add(preview);
  }

  private void renderChecklistRows() {
    checklistItemsBox.getChildren().clear();
    for (ChecklistItemModel item : note.getChecklistItems()) {
      checklistItemsBox.getChildren().add(buildChecklistRow(item));
    }
  }

  private void updateQuickTextHeight() {
    if (!isPlainTextEditorVisible()) {
      return;
    }

    String text = quickTextArea.getText();
    if (text == null || text.isBlank()) {
      quickTextArea.setPrefRowCount(MIN_EXPANDED_TEXT_LINES);
      return;
    }

    int lines = wrapTextToLines(text, availableTextContentWidth(), quickTextArea.getFont()).size();
    quickTextArea.setPrefRowCount(Math.min(MAX_EXPANDED_TEXT_LINES, Math.max(MIN_EXPANDED_TEXT_LINES, lines)));
  }

  private void handleContentChanged() {
    if (note.getType() != NoteType.TEXT) {
      return;
    }
    if (committingQuickTextChange && quickTextArea.isEditable()) {
      return;
    }
    refreshPreview();
    syncQuickTextMode();
    syncQuickTextFromModel();
  }

  private void handleChecklistItemsChanged(ListChangeListener.Change<? extends ChecklistItemModel> change) {
    ChecklistFocusTarget focusTarget =
        pendingChecklistFocusTarget != null ? pendingChecklistFocusTarget : captureChecklistFocusTarget();
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
      restoreChecklistFocus(focusTarget);
    }
    if (expanded.get()) {
      expandedContainer.setMaxHeight(measureExpandedHeight());
    }
    pendingChecklistFocusTarget = null;
  }

  private void syncQuickTextFromModel() {
    String plainText = note.getPlainTextContent();
    String currentText = quickTextArea.getText() == null ? "" : quickTextArea.getText();
    if (!quickTextDirty && !currentText.equals(plainText)) {
      syncingQuickTextFromModel = true;
      try {
        quickTextArea.setText(plainText);
        quickTextDirty = false;
      } finally {
        syncingQuickTextFromModel = false;
      }
    }
    scheduleQuickTextHeightRefresh();
    if (expanded.get() && note.getType() == NoteType.CHECKLIST) {
      expandedContainer.setMaxHeight(measureExpandedHeight());
    }
  }

  private void resetExpandedTextViewportToStart() {
    if (note.getType() != NoteType.TEXT) {
      return;
    }

    if (note.hasRichTextFormatting()) {
      if (richTextEditorController != null) {
        richTextEditorController.scrollToStart();
      }
      return;
    }

    quickTextArea.positionCaret(0);
    quickTextArea.selectRange(0, 0);
    quickTextArea.setScrollTop(0);
    quickTextArea.setScrollLeft(0);
  }

  private void refreshTextPresentation() {
    if (note == null || note.getType() != NoteType.TEXT) {
      return;
    }

    if (note.hasRichTextFormatting()) {
      if (!expanded.get()) {
        renderRichTextPresentation();
      }
      return;
    }

    if (expanded.get()) {
      scheduleQuickTextHeightRefresh();
      return;
    }

    richPreviewFlow.getChildren().clear();

    String plainText = note.getPlainTextContent();
    if (plainText == null || plainText.isBlank()) {
      previewLabel.setText(EMPTY_TEXT_NOTE_PLACEHOLDER);
      return;
    }

    List<String> wrappedLines = wrapTextToLines(plainText, availableTextContentWidth(), previewLabel.getFont());
    previewLabel.setText(truncateWrappedLines(wrappedLines, COLLAPSED_TEXT_PREVIEW_LINES, previewLabel.getFont()));
  }

  private void scheduleQuickTextPersist() {
    if (!quickTextArea.isEditable() || note.getType() != NoteType.TEXT) {
      return;
    }
    quickTextCommitDelay.playFromStart();
  }

  private void flushPendingQuickTextContent() {
    quickTextCommitDelay.stop();
    if (note == null || note.getType() != NoteType.TEXT || !quickTextArea.isEditable()) {
      return;
    }
    if (!quickTextDirty) {
      return;
    }

    String currentText = quickTextArea.getText();
    String normalized = currentText == null ? "" : currentText;
    if (normalized.equals(note.getPlainTextContent())) {
      quickTextDirty = false;
      return;
    }

    committingQuickTextChange = true;
    try {
      store.updatePlainTextContent(note, normalized);
      quickTextDirty = false;
    } finally {
      committingQuickTextChange = false;
    }
  }

  private void scheduleQuickTextHeightRefresh() {
    if (!isPlainTextEditorVisible()) {
      quickTextLayoutDelay.stop();
      return;
    }
    quickTextLayoutDelay.playFromStart();
  }

  private boolean isPlainTextEditorVisible() {
    return note != null
        && note.getType() == NoteType.TEXT
        && quickTextArea.isEditable()
        && textEditorBox.isVisible()
        && textEditorBox.isManaged();
  }

  private void renderRichTextPresentation() {
    List<Segment> segments = RichTextContentCodec.decode(note.getContent());
    if (segments.isEmpty()) {
      richPreviewFlow.getChildren().clear();
      previewLabel.setText(EMPTY_TEXT_NOTE_PLACEHOLDER);
      return;
    }

    previewLabel.setText("");
    populateTextFlow(
        richPreviewFlow,
        segmentsFromLines(truncateRichLines(
            wrapStyledTokens(tokenizeRichSegments(segments), availableTextContentWidth(), previewLabel.getFont()),
            availableTextContentWidth(),
            previewLabel.getFont(),
            COLLAPSED_TEXT_PREVIEW_LINES)),
        previewTextColor());
  }

  private List<StyledToken> tokenizeRichSegments(List<Segment> segments) {
    List<StyledToken> tokens = new ArrayList<>();
    for (Segment segment : segments) {
      if (segment == null || segment.text().isEmpty()) {
        continue;
      }

      StringBuilder builder = new StringBuilder();
      RichTokenType currentType = null;
      for (int index = 0; index < segment.text().length(); index++) {
        char currentChar = segment.text().charAt(index);
        if (currentChar == '\r') {
          continue;
        }

        RichTokenType nextType;
        char normalizedChar = currentChar;
        if (currentChar == '\n') {
          nextType = RichTokenType.NEWLINE;
        } else if (Character.isWhitespace(currentChar)) {
          nextType = RichTokenType.SPACE;
          normalizedChar = ' ';
        } else {
          nextType = RichTokenType.WORD;
        }

        if (nextType == RichTokenType.NEWLINE) {
          flushStyledToken(tokens, builder, segment.style(), currentType);
          tokens.add(new StyledToken("\n", segment.style(), RichTokenType.NEWLINE));
          currentType = null;
          continue;
        }

        if (currentType != nextType) {
          flushStyledToken(tokens, builder, segment.style(), currentType);
          currentType = nextType;
        }
        builder.append(normalizedChar);
      }
      flushStyledToken(tokens, builder, segment.style(), currentType);
    }
    return tokens;
  }

  private void flushStyledToken(List<StyledToken> tokens, StringBuilder builder, TextStyle style, RichTokenType type) {
    if (type == null || builder.isEmpty()) {
      builder.setLength(0);
      return;
    }
    tokens.add(new StyledToken(builder.toString(), style, type));
    builder.setLength(0);
  }

  private List<List<StyledToken>> wrapStyledTokens(List<StyledToken> tokens, double maxWidth, Font baseFont) {
    List<List<StyledToken>> lines = new ArrayList<>();
    List<StyledToken> currentLine = new ArrayList<>();
    double currentWidth = 0.0;

    for (StyledToken token : tokens) {
      if (token.type() == RichTokenType.NEWLINE) {
        trimTrailingWhitespace(currentLine);
        lines.add(copyTokens(currentLine));
        currentLine.clear();
        currentWidth = 0.0;
        continue;
      }

      if (token.type() == RichTokenType.SPACE) {
        if (currentLine.isEmpty()) {
          continue;
        }

        double tokenWidth = measureStyledText(token.text(), token.style(), baseFont);
        if (currentWidth + tokenWidth <= maxWidth) {
          currentLine.add(token);
          currentWidth += tokenWidth;
        } else {
          trimTrailingWhitespace(currentLine);
          lines.add(copyTokens(currentLine));
          currentLine.clear();
          currentWidth = 0.0;
        }
        continue;
      }

      String remaining = token.text();
      while (!remaining.isEmpty()) {
        double tokenWidth = measureStyledText(remaining, token.style(), baseFont);
        if (currentLine.isEmpty() && tokenWidth <= maxWidth) {
          currentLine.add(new StyledToken(remaining, token.style(), RichTokenType.WORD));
          currentWidth += tokenWidth;
          remaining = "";
        } else if (!currentLine.isEmpty() && currentWidth + tokenWidth <= maxWidth) {
          currentLine.add(new StyledToken(remaining, token.style(), RichTokenType.WORD));
          currentWidth += tokenWidth;
          remaining = "";
        } else if (!currentLine.isEmpty()) {
          trimTrailingWhitespace(currentLine);
          lines.add(copyTokens(currentLine));
          currentLine.clear();
          currentWidth = 0.0;
        } else {
          int splitIndex = longestFittingPrefix(remaining, token.style(), maxWidth, baseFont);
          if (splitIndex <= 0) {
            splitIndex = 1;
          }

          String fitting = remaining.substring(0, splitIndex);
          currentLine.add(new StyledToken(fitting, token.style(), RichTokenType.WORD));
          trimTrailingWhitespace(currentLine);
          lines.add(copyTokens(currentLine));
          currentLine.clear();
          currentWidth = 0.0;
          remaining = remaining.substring(splitIndex);
        }
      }
    }

    trimTrailingWhitespace(currentLine);
    if (!currentLine.isEmpty() || lines.isEmpty()) {
      lines.add(copyTokens(currentLine));
    }
    return lines;
  }

  private List<List<StyledToken>> truncateRichLines(
      List<List<StyledToken>> lines,
      double maxWidth,
      Font baseFont,
      int maxLines) {
    if (lines.size() <= maxLines) {
      return lines;
    }

    List<List<StyledToken>> previewLines = new ArrayList<>();
    for (int index = 0; index < maxLines; index++) {
      previewLines.add(copyTokens(lines.get(index)));
    }

    List<StyledToken> lastLine = previewLines.get(maxLines - 1);
    trimTrailingWhitespace(lastLine);
    TextStyle ellipsisStyle = lastVisibleStyle(lastLine);
    double ellipsisWidth = measureStyledText("...", ellipsisStyle, baseFont);

    while (!lastLine.isEmpty() && measureTokensWidth(lastLine, baseFont) + ellipsisWidth > maxWidth) {
      shrinkLastToken(lastLine);
      trimTrailingWhitespace(lastLine);
    }

    lastLine.add(new StyledToken("...", ellipsisStyle, RichTokenType.WORD));
    return previewLines;
  }

  private void shrinkLastToken(List<StyledToken> tokens) {
    if (tokens.isEmpty()) {
      return;
    }

    StyledToken lastToken = tokens.get(tokens.size() - 1);
    if (lastToken.text().length() <= 1) {
      tokens.remove(tokens.size() - 1);
      return;
    }

    tokens.set(
        tokens.size() - 1,
        new StyledToken(
            lastToken.text().substring(0, lastToken.text().length() - 1),
            lastToken.style(),
            lastToken.type()));
  }

  private TextStyle lastVisibleStyle(List<StyledToken> tokens) {
    for (int index = tokens.size() - 1; index >= 0; index--) {
      StyledToken token = tokens.get(index);
      if (!token.text().isBlank()) {
        return token.style();
      }
    }
    return TextStyle.PLAIN;
  }

  private void trimTrailingWhitespace(List<StyledToken> tokens) {
    while (!tokens.isEmpty() && tokens.get(tokens.size() - 1).type() == RichTokenType.SPACE) {
      tokens.remove(tokens.size() - 1);
    }
  }

  private List<StyledToken> copyTokens(List<StyledToken> tokens) {
    return new ArrayList<>(tokens);
  }

  private int longestFittingPrefix(String value, TextStyle style, double maxWidth, Font baseFont) {
    int splitIndex = 0;
    for (int index = 1; index <= value.length(); index++) {
      if (measureStyledText(value.substring(0, index), style, baseFont) <= maxWidth) {
        splitIndex = index;
      } else {
        break;
      }
    }
    return splitIndex;
  }

  private double measureTokensWidth(List<StyledToken> tokens, Font baseFont) {
    double width = 0.0;
    for (StyledToken token : tokens) {
      width += measureStyledText(token.text(), token.style(), baseFont);
    }
    return width;
  }

  private List<Segment> segmentsFromLines(List<List<StyledToken>> lines) {
    List<Segment> segments = new ArrayList<>();
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      for (StyledToken token : lines.get(lineIndex)) {
        appendSegment(segments, token.text(), token.style());
      }
      if (lineIndex < lines.size() - 1) {
        appendSegment(segments, "\n", TextStyle.PLAIN);
      }
    }
    return segments;
  }

  private void appendSegment(List<Segment> segments, String text, TextStyle style) {
    if (text == null || text.isEmpty()) {
      return;
    }

    if (!segments.isEmpty()) {
      Segment previous = segments.get(segments.size() - 1);
      if (previous.style().equals(style)) {
        segments.set(segments.size() - 1, new Segment(previous.text() + text, style));
        return;
      }
    }

    segments.add(new Segment(text, style));
  }

  private void populateTextFlow(TextFlow flow, List<Segment> segments, Color fill) {
    flow.getChildren().clear();
    Font baseFont = previewLabel.getFont();
    for (Segment segment : segments) {
      if (segment == null || segment.text().isEmpty()) {
        continue;
      }

      Text textNode = new Text(segment.text());
      textNode.setFont(fontForStyle(segment.style(), baseFont));
      textNode.setUnderline(segment.style().underline());
      textNode.setStrikethrough(segment.style().strikethrough());
      textNode.setFill(fill);
      if (flow == richPreviewFlow) {
        textNode.setOnMousePressed(this::handleCollapsedTextPreviewClick);
      }
      flow.getChildren().add(textNode);
    }
  }

  private Color previewTextColor() {
    return currentTheme() == AppTheme.DARK
        ? Color.web(PREVIEW_TEXT_COLOR_DARK)
        : Color.web(PREVIEW_TEXT_COLOR_LIGHT);
  }

  private Color expandedTextColor() {
    return currentTheme() == AppTheme.DARK
        ? Color.web(EXPANDED_TEXT_COLOR_DARK)
        : Color.web(EXPANDED_TEXT_COLOR_LIGHT);
  }

  private AppTheme currentTheme() {
    return store == null || store.getTheme() == null ? AppTheme.LIGHT : store.getTheme();
  }

  private String surfaceColor() {
    NoteColor color = note.getColor() == null ? NoteColor.DEFAULT : note.getColor();
    return color.surfaceForTheme(currentTheme());
  }

  private double measureStyledText(String value, TextStyle style, Font baseFont) {
    textMeasure.setFont(fontForStyle(style, baseFont));
    textMeasure.setText(value);
    return textMeasure.getLayoutBounds().getWidth();
  }

  private void ensureRichTextEditor() {
    if (richTextEditorController != null) {
      return;
    }

    LoadedView<TextNoteEditorController> view = ViewLoader.load("/fxml/note/TextNoteEditor.fxml");
    richTextEditorController = view.controller();
    richTextEditorController.setContext(note, store);
    richTextEditorController.setTabTraversalHandler(this::moveFocusByTab);
    richTextEditorController.updatePalette(surfaceColor(), currentTheme());
    richTextEditorBox.getChildren().setAll(view.root());
  }

  private Font fontForStyle(TextStyle style, Font baseFont) {
    TextStyle safeStyle = style == null ? TextStyle.PLAIN : style;
    Font sourceFont = baseFont == null ? Font.getDefault() : baseFont;
    FontWeight weight = safeStyle.bold() ? FontWeight.BOLD : FontWeight.NORMAL;
    FontPosture posture = safeStyle.italic() ? FontPosture.ITALIC : FontPosture.REGULAR;
    return Font.font(sourceFont.getFamily(), weight, posture, sourceFont.getSize());
  }

  private void syncQuickTextMode() {
    boolean richText = note.getType() == NoteType.TEXT && note.hasRichTextFormatting();
    quickTextArea.setEditable(!richText);
    quickTextArea.setFocusTraversable(!richText);
  }

  private HBox buildChecklistRow(ChecklistItemModel item) {
    HBox row = new HBox(8);
    row.getStyleClass().add("inline-checklist-row");

    CheckBox checkBox = new CheckBox();
    checkBox.setFocusTraversable(true);
    registerLoopingTabStop(checkBox);
    markChecklistFocusNode(checkBox, item, ChecklistFocusRole.CHECKBOX);
    checkBox.setSelected(item.isChecked());
    checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (item.isChecked() != newValue) {
        store.toggleChecklistItem(note, item);
      }
    });

    TextField textField = new TextField(item.getText());
    textField.setFocusTraversable(true);
    registerLoopingTabStop(textField);
    markChecklistFocusNode(textField, item, ChecklistFocusRole.TEXT_FIELD);
    textField.getStyleClass().add("inline-checklist-field");
    HBox.setHgrow(textField, Priority.ALWAYS);
    textField.textProperty().addListener((obs, oldValue, newValue) -> {
      store.updateChecklistItemText(note, item, newValue);
      Platform.runLater(() -> applyChecklistTextDecoration(textField, item.isChecked()));
    });
    item.textProperty().addListener((obs, oldValue, newValue) -> {
      if (!textField.isFocused() && !textField.getText().equals(newValue)) {
        textField.setText(newValue);
      }
    });
    textField.skinProperty().addListener((obs, oldValue, newValue) ->
        Platform.runLater(() -> applyChecklistTextDecoration(textField, item.isChecked())));
    item.checkedProperty().addListener((obs, oldValue, newValue) -> {
      if (checkBox.isSelected() != newValue) {
        checkBox.setSelected(newValue);
      }
      syncChecklistRowState(textField, newValue);
    });
    syncChecklistRowState(textField, item.isChecked());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button removeButton = new Button();
    removeButton.setFocusTraversable(true);
    registerLoopingTabStop(removeButton);
    markChecklistFocusNode(removeButton, item, ChecklistFocusRole.REMOVE_BUTTON);
    removeButton.getStyleClass().add("mini-icon-button");
    IconFactory.apply(removeButton, "codicon-close", 12, "mini-action-icon");
    removeButton.setText(null);
    MotionSupport.installButtonMotion(removeButton);
    removeButton.setOnAction(event -> store.removeChecklistItem(note, item));

    row.getChildren().addAll(checkBox, textField, spacer, removeButton);
    return row;
  }

  private void handleTabTraversal(KeyEvent event) {
    if (event.getCode() != KeyCode.TAB || !expanded.get()) {
      return;
    }

    if (moveFocusByTab(event.isShiftDown())) {
      event.consume();
    }
  }

  private void installQuickTextAreaTabGuards() {
    registerTabGuard(quickTextArea.lookup(".content"));
    quickTextArea.lookupAll(".content").forEach(this::registerTabGuard);
  }

  private void registerTabGuard(Node node) {
    if (node == null || node.getProperties().containsKey("noteCard.tabGuard")) {
      return;
    }

    node.getProperties().put("noteCard.tabGuard", Boolean.TRUE);
    node.addEventFilter(KeyEvent.KEY_PRESSED, this::handleTabTraversal);
    node.addEventFilter(KeyEvent.KEY_TYPED, this::consumeTabCharacterInsertion);
  }

  private boolean moveFocusByTab(boolean backward) {
    List<Node> focusTargets = collectFocusTargets();
    if (focusTargets.isEmpty()) {
      return false;
    }

    Node focusOwner = root.getScene() == null ? null : root.getScene().getFocusOwner();
    int currentIndex = indexOfFocusTarget(focusTargets, focusOwner);
    int targetIndex = currentIndex < 0
        ? (backward ? focusTargets.size() - 1 : 0)
        : Math.floorMod(currentIndex + (backward ? -1 : 1), focusTargets.size());
    pendingTabRedirectTarget = focusTargets.get(targetIndex);
    requestFocusForTabTarget(pendingTabRedirectTarget);
    return true;
  }

  private void consumeTabCharacterInsertion(KeyEvent event) {
    if (!expanded.get()) {
      return;
    }

    String character = event.getCharacter();
    if (character != null && character.contains("\t")) {
      event.consume();
    }
  }

  private List<Node> collectFocusTargets() {
    List<Node> targets = new ArrayList<>();
    appendFocusTarget(targets, titleField);
    appendFocusTarget(targets, pinButton);
    appendFocusTarget(targets, openWindowButton);
    appendFocusTarget(targets, quickTextArea);
    appendFocusTarget(targets, richTextEditorFocusTarget());

    if (checklistEditorBox.isVisible() && checklistEditorBox.isManaged()) {
      for (Node rowNode : checklistItemsBox.getChildren()) {
        if (!(rowNode instanceof HBox row) || !row.isVisible() || !row.isManaged()) {
          continue;
        }
        for (Node child : row.getChildren()) {
          appendFocusTarget(targets, child);
        }
      }
      appendFocusTarget(targets, addChecklistItemButton);
    }

    return targets;
  }

  List<Node> focusTargetsForTesting() {
    return collectFocusTargets();
  }

  private void registerLoopingTabStop(Node node) {
    if (node != null) {
      node.addEventFilter(KeyEvent.KEY_PRESSED, this::handleTabTraversal);
      node.focusedProperty().addListener((obs, oldValue, newValue) -> {
        if (newValue && pendingTabRedirectTarget == node) {
          pendingTabRedirectTarget = null;
        }
        if (node == pinButton || node == openWindowButton) {
          refreshActionButtonsVisibility();
        }
      });
    }
  }

  private void requestFocusForTabTarget(Node target) {
    focusTabTarget(target);
    Platform.runLater(() -> {
      if (!isSameNodeOrDescendant(target, root.getScene() == null ? null : root.getScene().getFocusOwner())) {
        focusTabTarget(target);
        Platform.runLater(() -> {
          if (!isSameNodeOrDescendant(target, root.getScene() == null ? null : root.getScene().getFocusOwner())) {
            focusTabTarget(target);
          }
        });
      }
    });
  }

  private void attachSceneFocusOwnerListener(Scene scene) {
    if (scene == null) {
      return;
    }
    scene.focusOwnerProperty().addListener(sceneFocusOwnerListener);
    attachedScene = scene;
  }

  private void detachSceneFocusOwnerListener(Scene scene) {
    if (scene == null) {
      return;
    }
    scene.focusOwnerProperty().removeListener(sceneFocusOwnerListener);
    if (scene == attachedScene) {
      attachedScene = null;
    }
  }

  private void focusTabTarget(Node target) {
    if (target == null
        || target.getScene() == null
        || !isEffectivelyVisible(target)
        || target.isDisabled()) {
      return;
    }

    if (target == richTextEditorFocusTarget() && richTextEditorController != null) {
      richTextEditorController.requestEditorFocus();
      return;
    }

    target.requestFocus();
    if (target instanceof TextInputControl textInputControl) {
      textInputControl.positionCaret(textInputControl.getLength());
    }
  }

  private Node richTextEditorFocusTarget() {
    return richTextEditorController == null ? null : richTextEditorController.focusTarget();
  }

  private void appendFocusTarget(List<Node> targets, Node candidate) {
    if (candidate != null
        && isEffectivelyVisible(candidate)
        && candidate.isFocusTraversable()
        && !candidate.isDisabled()
        && !candidate.isMouseTransparent()) {
      targets.add(candidate);
    }
  }

  private boolean isEffectivelyVisible(Node node) {
    if (node == null || node.getScene() == null) {
      return false;
    }

    Node current = node;
    while (current != null) {
      if (!current.isVisible() || !current.isManaged()) {
        return false;
      }
      current = current.getParent();
    }
    return true;
  }

  private int indexOfFocusTarget(List<Node> focusTargets, Node focusOwner) {
    for (int index = 0; index < focusTargets.size(); index++) {
      if (isSameNodeOrDescendant(focusTargets.get(index), focusOwner)) {
        return index;
      }
    }
    return -1;
  }

  private ChecklistFocusTarget captureChecklistFocusTarget() {
    if (!expanded.get() || note.getType() != NoteType.CHECKLIST || root.getScene() == null) {
      return null;
    }

    Node focusOwner = root.getScene().getFocusOwner();
    if (focusOwner == null || !isSameNodeOrDescendant(checklistEditorBox, focusOwner)) {
      return null;
    }

    if (isSameNodeOrDescendant(addChecklistItemButton, focusOwner)) {
      return new ChecklistFocusTarget(null, ChecklistFocusRole.ADD_BUTTON, note.getChecklistItems().size());
    }

    Node roleNode = findChecklistFocusNode(focusOwner);
    if (roleNode == null) {
      return null;
    }

    ChecklistFocusRole role =
        roleNode.getProperties().get("noteCard.checklistRole") instanceof ChecklistFocusRole roleValue
            ? roleValue
        : null;
    ChecklistItemModel item =
        roleNode.getProperties().get("noteCard.checklistItem") instanceof ChecklistItemModel itemValue
            ? itemValue
        : null;
    if (role == null) {
      return null;
    }
    return new ChecklistFocusTarget(item, role, item == null ? -1 : note.getChecklistItems().indexOf(item));
  }

  private void restoreChecklistFocus(ChecklistFocusTarget focusTarget) {
    if (focusTarget == null) {
      return;
    }

    Platform.runLater(() -> {
      Node focusNode = resolveChecklistFocusNode(focusTarget);
      if (focusNode != null) {
        focusNode.requestFocus();
      }
    });
  }

  private Node resolveChecklistFocusNode(ChecklistFocusTarget focusTarget) {
    if (focusTarget.role() == ChecklistFocusRole.ADD_BUTTON) {
      return addChecklistItemButton;
    }

    ChecklistItemModel targetItem = focusTarget.item();
    if (targetItem == null || !note.getChecklistItems().contains(targetItem)) {
      if (note.getChecklistItems().isEmpty()) {
        return addChecklistItemButton;
      }
      int safeIndex = Math.min(Math.max(focusTarget.index(), 0), note.getChecklistItems().size() - 1);
      targetItem = note.getChecklistItems().get(safeIndex);
    }

    for (Node rowNode : checklistItemsBox.getChildren()) {
      if (!(rowNode instanceof HBox row)) {
        continue;
      }
      for (Node rowChild : row.getChildren()) {
        if (rowChild.getProperties().get("noteCard.checklistItem") == targetItem
            && rowChild.getProperties().get("noteCard.checklistRole") == focusTarget.role()) {
          return rowChild;
        }
      }
    }

    return addChecklistItemButton;
  }

  private Node findChecklistFocusNode(Node node) {
    Node current = node;
    while (current != null) {
      if (current.getProperties().containsKey("noteCard.checklistRole")) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  private void markChecklistFocusNode(Node node, ChecklistItemModel item, ChecklistFocusRole role) {
    node.getProperties().put("noteCard.checklistItem", item);
    node.getProperties().put("noteCard.checklistRole", role);
  }

  private boolean isSameNodeOrDescendant(Node ancestor, Node node) {
    Node current = node;
    while (current != null) {
      if (current == ancestor) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private String buildChecklistPreviewText() {
    List<String> items = new ArrayList<>();
    for (ChecklistItemModel item : note.getChecklistItems()) {
      if (item.getText() != null && !item.getText().isBlank()) {
        items.add(item.getText().trim());
      }
    }

    if (items.isEmpty()) {
      return EMPTY_CHECKLIST_PLACEHOLDER;
    }

    List<String> wrappedLines = wrapTextToLines(
        String.join(", ", items),
        availableChecklistPreviewWidth(),
        previewLabel.getFont());
    return truncateWrappedLines(wrappedLines, COLLAPSED_TEXT_PREVIEW_LINES, previewLabel.getFont());
  }

  private double availableChecklistPreviewWidth() {
    double width = checklistPreviewBox.getWidth();
    if (width <= 0) {
      width = root.getWidth();
    }
    return Math.max(FALLBACK_TEXT_WIDTH, width);
  }

  private void syncChecklistRowState(TextField textField, boolean checked) {
    toggleStyleClass(textField, CHECKLIST_ITEM_CHECKED_CLASS, checked);
    Platform.runLater(() -> applyChecklistTextDecoration(textField, checked));
  }

  private void applyChecklistTextDecoration(TextField textField, boolean checked) {
    if (textField == null) {
      return;
    }

    textField.applyCss();
    textField.lookupAll(".text").forEach(node -> {
      if (node instanceof Text text) {
        text.setStrikethrough(checked);
      }
    });
  }

  private void toggleStyleClass(Node node, String styleClass, boolean enabled) {
    if (node == null) {
      return;
    }

    if (enabled) {
      if (!node.getStyleClass().contains(styleClass)) {
        node.getStyleClass().add(styleClass);
      }
    } else {
      node.getStyleClass().remove(styleClass);
    }
  }

  private void configureActionButton(Button button) {
    button.setVisible(true);
    button.setManaged(true);
    button.setFocusTraversable(true);
    button.setPickOnBounds(true);
    configureActionGraphic(button);
    button.setOpacity(0.0);
    button.setMouseTransparent(true);
  }

  private void refreshActionButtonsVisibility() {
    boolean showPin = shouldShowPinButton();
    boolean showOpen = shouldShowOpenWindowButton();
    updateActionButtonVisibility(pinButton, showPin);
    updateActionButtonVisibility(openWindowButton, showOpen);
  }

  private boolean shouldShowPinButton() {
    return note != null && (note.isPinned() || root.isHover() || expanded.get() || pinButton.isFocused());
  }

  private boolean shouldShowOpenWindowButton() {
    return root.isHover() || expanded.get() || openWindowButton.isFocused();
  }

  private void updateActionButtonVisibility(Button button, boolean visible) {
    if (button == null) {
      return;
    }
    if (button.getScene() == null) {
      button.setOpacity(visible ? 1.0 : 0.0);
      button.setMouseTransparent(!visible);
      return;
    }
    animateActionButton(button, visible);
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

  private void configureActionGraphic(Button button) {
    Node graphic = button.getGraphic();
    if (graphic == null) {
      return;
    }

    graphic.setPickOnBounds(true);
    graphic.setOnMousePressed(MouseEvent::consume);
    graphic.setOnMouseReleased(event -> handleGraphicRelease(button, event));
  }

  private void handleGraphicRelease(Button button, MouseEvent event) {
    if (!button.isDisabled() && !button.isMouseTransparent()) {
      button.fire();
    }
    event.consume();
  }

  private boolean isInteractiveTarget(Object target) {
    if (!(target instanceof Node node)) {
      return false;
    }

    Node current = node;
    while (current != null) {
      if (current instanceof Button || current instanceof CheckBox || current instanceof TextInputControl) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private void ensurePinGraphic() {
    pinButton.setGraphic(note.isPinned()
        ? createSolidPinGraphic()
        : IconFactory.icon("codicon-pinned", 16, "card-action-icon", "pinned-icon"));
    configureActionGraphic(pinButton);
  }

  private void refreshInitialPinGraphic() {
    ensurePinGraphic();
    pinButton.applyCss();
    pinButton.layout();
  }

  private void applyExpandedVisualState() {
    applyExpandedVisualState(false);
  }

  private void applyExpandedVisualState(boolean animateTextTransition) {
    boolean showTitleField = hasUserTitle() || editingEmptyTitle;
    titleLabel.setVisible(!showTitleField);
    titleLabel.setManaged(!showTitleField);
    titleField.setVisible(showTitleField);
    titleField.setManaged(showTitleField);
    Platform.runLater(this::syncTitleFieldIfEditable);
    refreshPreview();
    refreshEditorVisibility();
    if (!root.getStyleClass().contains("note-card-selected")) {
      root.getStyleClass().add("note-card-selected");
    }
    if (animateTextTransition) {
      animateTextContentTransition();
    }
  }

  private void applyCollapsedVisualState() {
    applyCollapsedVisualState(false);
  }

  private void applyCollapsedVisualState(boolean animateTextTransition) {
    editingEmptyTitle = false;
    titleLabel.setVisible(true);
    titleLabel.setManaged(true);
    titleField.setVisible(false);
    titleField.setManaged(false);
    refreshPreview();
    refreshEditorVisibility();
    root.getStyleClass().remove("note-card-selected");
    if (animateTextTransition) {
      animateTextContentTransition();
    }
  }

  private boolean isExpandedContainerOpen() {
    return expandedContainer.isVisible()
        || expandedContainer.isManaged()
        || expandedContainer.getMaxHeight() > 0.0
        || expandedContainer.getOpacity() > 0.0;
  }

  private void resetCollapsedContainerState() {
    expandedContainer.setVisible(false);
    expandedContainer.setManaged(false);
    expandedContainer.setMaxHeight(0.0);
    expandedContainer.setOpacity(0.0);
    expandedContainer.setTranslateY(-6.0);
  }

  private void syncTitleFieldFromModel() {
    syncingTitleField = true;
    titleField.setText(note.getTitle() == null ? "" : note.getTitle());
    syncingTitleField = false;
  }

  private void syncTitleFieldIfEditable() {
    if (expanded.get() && !titleField.isFocused()) {
      syncTitleFieldFromModel();
    }
  }

  private double availableTextContentWidth() {
    double width = textContentStack.getWidth();
    if (width <= 0) {
      width = previewLabel.getWidth();
    }
    if (width <= 0) {
      width = quickTextArea.getWidth();
    }
    return Math.max(FALLBACK_TEXT_WIDTH, width);
  }

  private List<String> wrapTextToLines(String value, double maxWidth, Font font) {
    List<String> lines = new ArrayList<>();
    if (value == null || value.isBlank()) {
      return lines;
    }

    String[] paragraphs = value.replace("\t", " ").split("\\R", -1);
    for (String paragraph : paragraphs) {
      if (paragraph.isBlank()) {
        lines.add("");
        continue;
      }

      String currentLine = "";
      for (String word : paragraph.trim().split("\\s+")) {
        currentLine = appendWord(lines, currentLine, word, maxWidth, font);
      }

      if (!currentLine.isEmpty()) {
        lines.add(currentLine);
      }
    }

    return lines;
  }

  private String appendWord(List<String> lines, String currentLine, String word, double maxWidth, Font font) {
    if (currentLine.isEmpty()) {
      if (fitsWithinWidth(word, maxWidth, font)) {
        return word;
      }
      return splitOversizedWord(lines, word, maxWidth, font);
    }

    String candidate = currentLine + " " + word;
    if (fitsWithinWidth(candidate, maxWidth, font)) {
      return candidate;
    }

    lines.add(currentLine);
    if (fitsWithinWidth(word, maxWidth, font)) {
      return word;
    }
    return splitOversizedWord(lines, word, maxWidth, font);
  }

  private String splitOversizedWord(List<String> lines, String word, double maxWidth, Font font) {
    String remaining = word;
    while (!remaining.isEmpty()) {
      int splitIndex = 1;
      while (splitIndex < remaining.length()
          && fitsWithinWidth(remaining.substring(0, splitIndex + 1), maxWidth, font)) {
        splitIndex++;
      }

      if (splitIndex >= remaining.length()) {
        return remaining;
      }

      lines.add(remaining.substring(0, splitIndex));
      remaining = remaining.substring(splitIndex);
    }
    return "";
  }

  private String truncateWrappedLines(List<String> wrappedLines, int maxLines, Font font) {
    if (wrappedLines.isEmpty()) {
      return EMPTY_TEXT_NOTE_PLACEHOLDER;
    }

    if (wrappedLines.size() <= maxLines) {
      return String.join("\n", wrappedLines);
    }

    List<String> previewLines = new ArrayList<>(wrappedLines.subList(0, maxLines));
    String lastLine = previewLines.get(maxLines - 1).stripTrailing();
    if (lastLine.isEmpty()) {
      previewLines.set(maxLines - 1, "...");
      return String.join("\n", previewLines);
    }

    while (!lastLine.isEmpty() && !fitsWithinWidth(lastLine + "...", availableTextContentWidth(), font)) {
      lastLine = lastLine.substring(0, lastLine.length() - 1).stripTrailing();
    }

    previewLines.set(maxLines - 1, lastLine.isEmpty() ? "..." : lastLine + "...");
    return String.join("\n", previewLines);
  }

  private boolean fitsWithinWidth(String value, double maxWidth, Font font) {
    textMeasure.setFont(font == null ? Font.getDefault() : font);
    textMeasure.setText(value);
    return textMeasure.getLayoutBounds().getWidth() <= maxWidth;
  }

  private void animateTextContentTransition() {
    if (note == null || note.getType() != NoteType.TEXT) {
      return;
    }

    Node target = visibleTextContentNode();
    if (target == null) {
      return;
    }

    if (textContentTimeline != null) {
      textContentTimeline.stop();
    }

    resetTextContentNodeState();
    target.setOpacity(0.86);
    target.setTranslateY(TEXT_SWAP_OFFSET);

    textContentTimeline = new Timeline(
        new KeyFrame(
            TEXT_SWAP_DURATION,
            new KeyValue(target.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
            new KeyValue(target.translateYProperty(), 0.0, Interpolator.EASE_BOTH)));
    textContentTimeline.setOnFinished(event -> resetTextContentNodeState());
    textContentTimeline.playFromStart();
  }

  private Node visibleTextContentNode() {
    if (richTextEditorBox.isVisible()) {
      return richTextEditorBox;
    }
    if (textEditorBox.isVisible()) {
      return textEditorBox;
    }
    if (richPreviewFlow.isVisible()) {
      return richPreviewFlow;
    }
    if (previewLabel.isVisible()) {
      return previewLabel;
    }
    return null;
  }

  private void resetTextContentNodeState() {
    previewLabel.setOpacity(1.0);
    previewLabel.setTranslateY(0.0);
    richPreviewFlow.setOpacity(1.0);
    richPreviewFlow.setTranslateY(0.0);
    richTextEditorBox.setOpacity(1.0);
    richTextEditorBox.setTranslateY(0.0);
    textEditorBox.setOpacity(1.0);
    textEditorBox.setTranslateY(0.0);
  }

  private void normalizeQuickTextInsets() {
    normalizeRegionInsets(quickTextArea.lookup(".scroll-pane"));
    normalizeRegionInsets(quickTextArea.lookup(".viewport"));
    quickTextArea.lookupAll(".content").forEach(this::normalizeRegionInsets);
  }

  private void normalizeRegionInsets(Node node) {
    if (node instanceof Region region) {
      region.setPadding(Insets.EMPTY);
    }
  }

  private SVGPath createSolidPinGraphic() {
    SVGPath icon = new SVGPath();
    icon.setContent(SOLID_PIN_PATH);
    icon.setScaleX(0.56);
    icon.setScaleY(0.56);
    icon.getStyleClass().add("solid-pin-icon");
    return icon;
  }

  private void syncTitlePlaceholderStyle() {
    titleLabel.getStyleClass().remove("note-card-title-placeholder");
    if (!hasUserTitle()) {
      titleLabel.getStyleClass().add("note-card-title-placeholder");
    }
  }

  private boolean hasUserTitle() {
    return note.getTitle() != null && !note.getTitle().isBlank();
  }

  private void beginEditingEmptyTitle() {
    editingEmptyTitle = true;
    applyExpandedVisualState(false);
    Platform.runLater(() -> {
      titleField.requestFocus();
      titleField.positionCaret(titleField.getText().length());
    });
  }
}
