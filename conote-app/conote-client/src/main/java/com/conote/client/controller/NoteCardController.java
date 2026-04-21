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
import javafx.animation.Timeline;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
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
  private static final int COLLAPSED_TEXT_PREVIEW_LINES = 3;
  private static final int MIN_EXPANDED_TEXT_LINES = 3;
  private static final int MAX_EXPANDED_TEXT_LINES = 12;
  private static final double FALLBACK_TEXT_WIDTH = 220.0;
  private static final String PREVIEW_TEXT_COLOR_LIGHT = "#64748b";
  private static final String PREVIEW_TEXT_COLOR_DARK = "#cbd5e1";
  private static final String EXPANDED_TEXT_COLOR_LIGHT = "#334155";
  private static final String EXPANDED_TEXT_COLOR_DARK = "#f8fafc";
  private static final String SOLID_PIN_PATH =
      "M16 9V3H17V1H7V3H8V9C8 10.1 7.55 11.1 6.76 11.9L5 13.66V15H11V23H13V15H19V13.66L17.24 11.9C16.45 11.1 16 10.1 16 9Z";
  private static final DateTimeFormatter CARD_DATE =
      DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
  private static final String ACTION_FADE_KEY = "noteCard.actionFade";

  private enum RichTokenType {
    WORD,
    SPACE,
    NEWLINE
  }

  private record StyledToken(String text, TextStyle style, RichTokenType type) {
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
  private BooleanBinding pinButtonVisibleState;
  private final Text textMeasure = new Text();
  private TextNoteEditorController richTextEditorController;

  public void setContext(NoteModel note, CoNoteStore store, MainWindowController mainController) {
    this.note = note;
    this.store = store;
    this.mainController = mainController;

    expandedClip.widthProperty().bind(expandedContainer.widthProperty());
    expandedClip.heightProperty().bind(expandedContainer.maxHeightProperty());
    expandedContainer.setClip(expandedClip);
    root.setFocusTraversable(true);
    richPreviewFlow.setLineSpacing(0);
    MotionSupport.installCardMotion(root, expanded);
    MotionSupport.installButtonMotion(addChecklistItemButton);
    titleField.setPromptText(UNTITLED_NOTE_PLACEHOLDER);
    quickTextArea.setPromptText(EMPTY_TEXT_NOTE_PLACEHOLDER);
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
      if (note.getType() == NoteType.TEXT && quickTextArea.isEditable()) {
        store.updatePlainTextContent(note, newValue);
        updateQuickTextHeight();
      }
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
    note.getTags().addListener((ListChangeListener<String>) change -> refreshTags());
    note.getChecklistItems().addListener((ListChangeListener<ChecklistItemModel>) this::handleChecklistItemsChanged);
    textContentStack.widthProperty().addListener((obs, oldValue, newValue) -> refreshTextPresentation());
    root.sceneProperty().addListener((obs, oldValue, newValue) -> {
      if (newValue != null) {
        Platform.runLater(this::refreshTextPresentation);
        Platform.runLater(this::refreshInitialPinGraphic);
      }
    });

    pinButtonVisibleState = note.pinnedProperty().or(root.hoverProperty());
    configureActionButton(pinButton, pinButtonVisibleState);
    configureActionButton(openWindowButton, root.hoverProperty());

    syncExpandedState();
    syncPinState();
    refreshFromModel();
    Platform.runLater(this::refreshInitialPinGraphic);
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

    if (selected) {
      applyExpandedVisualState();
      if (note.getType() == NoteType.CHECKLIST) {
        animateExpandedState(true);
      } else {
        resetCollapsedContainerState();
      }
    } else if (note.getType() == NoteType.CHECKLIST && isExpandedContainerOpen()) {
      animateExpandedState(false);
    } else {
      applyCollapsedVisualState();
      resetCollapsedContainerState();
    }

    focusExpandedEditor(selected);
  }

  private void refreshFromModel() {
    updateTitle();
    refreshPreview();
    refreshDate();
    refreshTags();
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
    if (showRichEditor) {
      ensureRichTextEditor();
    }
    if (richTextEditorController != null) {
      richTextEditorController.setVisible(showRichEditor);
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
    if (!selected || note.getType() != NoteType.TEXT || note.getPlainTextContent().isBlank()) {
      return;
    }

    Platform.runLater(root::requestFocus);
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
    refreshPreview();
    syncQuickTextMode();
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
    String plainText = note.getPlainTextContent();
    if (!quickTextArea.isFocused() && !quickTextArea.getText().equals(plainText)) {
      quickTextArea.setText(plainText);
    }
    updateQuickTextHeight();
    if (expanded.get() && note.getType() == NoteType.CHECKLIST) {
      expandedContainer.setMaxHeight(measureExpandedHeight());
    }
  }

  private void refreshTextPresentation() {
    if (note == null || note.getType() != NoteType.TEXT) {
      return;
    }

    if (note.hasRichTextFormatting()) {
      renderRichTextPresentation();
      return;
    }

    richPreviewFlow.getChildren().clear();

    String plainText = note.getPlainTextContent();
    if (plainText == null || plainText.isBlank()) {
      previewLabel.setText(EMPTY_TEXT_NOTE_PLACEHOLDER);
      updateQuickTextHeight();
      return;
    }

    List<String> wrappedLines = wrapTextToLines(plainText, availableTextContentWidth(), previewLabel.getFont());
    previewLabel.setText(truncateWrappedLines(wrappedLines, COLLAPSED_TEXT_PREVIEW_LINES, previewLabel.getFont()));
    updateQuickTextHeight();
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
    checkBox.setSelected(item.isChecked());
    checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (item.isChecked() != newValue) {
        store.toggleChecklistItem(note, item);
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
    configureActionGraphic(button);
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
  }

  private void applyCollapsedVisualState() {
    editingEmptyTitle = false;
    titleLabel.setVisible(true);
    titleLabel.setManaged(true);
    titleField.setVisible(false);
    titleField.setManaged(false);
    refreshPreview();
    refreshEditorVisibility();
    root.getStyleClass().remove("note-card-selected");
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
    applyExpandedVisualState();
    Platform.runLater(() -> {
      titleField.requestFocus();
      titleField.positionCaret(titleField.getText().length());
    });
  }
}
