package com.conote.client.controller;

import com.conote.client.model.AppTheme;
import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.RichTextContentCodec;
import com.conote.client.util.RichTextContentCodec.Segment;
import com.conote.client.util.RichTextContentCodec.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;

public class TextNoteEditorController {
  private static final Duration CONTENT_SYNC_DELAY = Duration.millis(220);
  private static final String DEFAULT_SURFACE = "#fff6d8";
  private static final String LIGHT_TEXT = "#1e3a5f";
  private static final String DARK_TEXT = "#f8fafc";
  private static final String PROMPT_COLOR = "#94a3b8";
  private static final String LIGHT_SELECTION = "rgba(252, 186, 3, 0.20)";
  private static final String DARK_SELECTION = "rgba(252, 186, 3, 0.30)";

  public record FormattingState(boolean bold, boolean italic, boolean underline, boolean strikethrough) {
    public static final FormattingState PLAIN = new FormattingState(false, false, false, false);

    private FormattingState withBold(boolean value) {
      return new FormattingState(value, italic, underline, strikethrough);
    }

    private FormattingState withItalic(boolean value) {
      return new FormattingState(bold, value, underline, strikethrough);
    }

    private FormattingState withUnderline(boolean value) {
      return new FormattingState(bold, italic, value, strikethrough);
    }

    private FormattingState withStrikethrough(boolean value) {
      return new FormattingState(bold, italic, underline, value);
    }

    private TextStyle toTextStyle() {
      return new TextStyle(bold, italic, underline, strikethrough);
    }
  }

  @FXML
  private VBox root;

  @FXML
  private StackPane editorContainer;

  @FXML
  private Label promptLabel;

  private final PauseTransition contentSyncDelay = new PauseTransition(CONTENT_SYNC_DELAY);
  private final ChangeListener<String> noteContentListener = (obs, oldValue, newValue) -> syncFromModel(newValue);
  private final EventHandler<WindowEvent> windowHidingHandler = event -> handleWindowHiding();
  private final ChangeListener<Window> sceneWindowListener = (obs, oldWindow, newWindow) -> {
    if (oldWindow != null) {
      oldWindow.removeEventHandler(WindowEvent.WINDOW_HIDING, windowHidingHandler);
    }
    attachedWindow = newWindow;
    if (newWindow != null) {
      newWindow.addEventHandler(WindowEvent.WINDOW_HIDING, windowHidingHandler);
    }
  };

  private InlineCssTextArea editorArea;
  private VirtualizedScrollPane<InlineCssTextArea> editorScrollPane;
  private boolean editorFocused;
  private boolean syncingFromModel;
  private boolean contentDirty;
  private String latestSerializedContent = "";

  private NoteModel note;
  private CoNoteStore store;
  private Scene attachedScene;
  private Window attachedWindow;
  private String surfaceColor = DEFAULT_SURFACE;
  private AppTheme theme = AppTheme.LIGHT;
  private Consumer<FormattingState> formattingStateListener = state -> {
  };

  @FXML
  private void initialize() {
    root.setFillWidth(true);
    contentSyncDelay.setOnFinished(event -> flushEditorContent());
    root.sceneProperty().addListener((obs, oldScene, newScene) -> attachWindowListeners(oldScene, newScene));
    createEditor();
  }

  public void setContext(NoteModel note, CoNoteStore store) {
    flushEditorContent();
    if (this.note != null) {
      this.note.contentProperty().removeListener(noteContentListener);
    }

    this.note = note;
    this.store = store;

    String content = note == null || note.getContent() == null ? "" : note.getContent();
    latestSerializedContent = content;
    contentDirty = false;
    syncingFromModel = true;
    applyEditorContent(content, true);
    syncingFromModel = false;
    if (note != null) {
      note.contentProperty().addListener(noteContentListener);
    }
    updatePromptVisibility();
    publishCurrentFormattingState();
  }

  public void setVisible(boolean visible) {
    if (!visible) {
      flushEditorContent();
    }
    root.setVisible(visible);
    root.setManaged(visible);
  }

  public void setFormattingStateListener(Consumer<FormattingState> formattingStateListener) {
    this.formattingStateListener = formattingStateListener == null ? state -> {
    } : formattingStateListener;
    publishCurrentFormattingState();
  }

  public void setBoldSelected(boolean selected) {
    applyFormatting(state -> state.withBold(selected));
  }

  public void setItalicSelected(boolean selected) {
    applyFormatting(state -> state.withItalic(selected));
  }

  public void setUnderlineSelected(boolean selected) {
    applyFormatting(state -> state.withUnderline(selected));
  }

  public void setStrikethroughSelected(boolean selected) {
    applyFormatting(state -> state.withStrikethrough(selected));
  }

  public void updatePalette(String surfaceColor, AppTheme theme) {
    this.surfaceColor = surfaceColor == null || surfaceColor.isBlank() ? DEFAULT_SURFACE : surfaceColor;
    this.theme = theme == null ? AppTheme.LIGHT : theme;
    applyEditorAppearance();
  }

  private void syncFromModel(String content) {
    String nextValue = content == null ? "" : content;
    if (syncingFromModel || editorFocused || contentDirty || Objects.equals(latestSerializedContent, nextValue)) {
      return;
    }

    syncingFromModel = true;
    latestSerializedContent = nextValue;
    applyEditorContent(nextValue, false);
    syncingFromModel = false;
  }

  private void flushEditorContent() {
    contentSyncDelay.stop();
    if (syncingFromModel || note == null || store == null || !contentDirty || editorArea == null) {
      return;
    }

    String serialized = serializeDocument();
    contentDirty = false;
    latestSerializedContent = serialized;
    if (!Objects.equals(note.getContent(), serialized)) {
      store.updateContent(note, serialized);
    }
  }

  private void attachWindowListeners(Scene oldScene, Scene newScene) {
    if (oldScene != null) {
      oldScene.windowProperty().removeListener(sceneWindowListener);
    }
    if (attachedWindow != null) {
      attachedWindow.removeEventHandler(WindowEvent.WINDOW_HIDING, windowHidingHandler);
      attachedWindow = null;
    }

    attachedScene = newScene;
    if (attachedScene != null) {
      attachedWindow = attachedScene.getWindow();
      if (attachedWindow != null) {
        attachedWindow.addEventHandler(WindowEvent.WINDOW_HIDING, windowHidingHandler);
      }
      attachedScene.windowProperty().addListener(sceneWindowListener);
    }
  }

  private void handleWindowHiding() {
    flushEditorContent();
  }

  private void createEditor() {
    editorArea = new InlineCssTextArea();
    editorArea.setWrapText(true);
    editorArea.setEditable(true);
    editorArea.setUseInitialStyleForInsertion(true);
    editorArea.setPadding(Insets.EMPTY);
    editorArea.setCursor(Cursor.TEXT);
    editorArea.getStyleClass().add("text-note-editor-area");
    editorArea.textProperty().addListener((obs, oldValue, newValue) -> updatePromptVisibility());
    editorArea.richChanges().subscribe(change -> handleEditorMutation());
    editorArea.focusedProperty().addListener((obs, oldValue, newValue) -> {
      editorFocused = newValue;
      updatePromptVisibility();
      publishCurrentFormattingState();
      if (!newValue) {
        flushEditorContent();
      }
    });
    editorArea.caretPositionProperty().addListener((obs, oldValue, newValue) -> publishCurrentFormattingState());
    editorArea.selectionProperty().addListener((obs, oldValue, newValue) -> publishCurrentFormattingState());

    editorScrollPane = new VirtualizedScrollPane<>(editorArea);
    editorScrollPane.getStyleClass().add("text-note-editor-scroll");
    editorScrollPane.setCursor(Cursor.TEXT);

    editorContainer.setCursor(Cursor.TEXT);
    editorContainer.getChildren().add(0, editorScrollPane);
    applyEditorAppearance();
    updatePromptVisibility();
  }

  private void handleEditorMutation() {
    updatePromptVisibility();
    if (syncingFromModel) {
      return;
    }

    contentDirty = true;
    contentSyncDelay.playFromStart();
  }

  private void applyFormatting(UnaryOperator<FormattingState> formatter) {
    if (editorArea == null) {
      return;
    }

    IndexRange selection = editorArea.getSelection();
    if (selection == null || selection.getLength() == 0) {
      editorArea.requestFocus();
      publishCurrentFormattingState();
      return;
    }

    int start = selection.getStart();
    int end = selection.getEnd();
    int offset = start;
    StyleSpans<String> spans = editorArea.getStyleSpans(start, end);
    for (StyleSpan<String> span : spans) {
      int length = span.getLength();
      if (length <= 0) {
        continue;
      }
      FormattingState updated = formatter.apply(formattingStateFromCss(span.getStyle()));
      editorArea.setStyle(offset, offset + length, cssFor(updated.toTextStyle()));
      offset += length;
    }

    editorArea.selectRange(start, end);
    editorArea.requestFocus();
    publishCurrentFormattingState();
  }

  private void applyEditorAppearance() {
    if (editorArea == null || promptLabel == null) {
      return;
    }

    String foreground = theme == AppTheme.DARK ? DARK_TEXT : LIGHT_TEXT;
    String selectionFill = theme == AppTheme.DARK ? DARK_SELECTION : LIGHT_SELECTION;

    editorContainer.setStyle("-fx-background-color: " + surfaceColor + "; -fx-background-insets: 0;");
    editorScrollPane.setStyle("-fx-background-color: transparent;");
    editorArea.setStyle(
        "-fx-background-color: transparent;"
            + "-fx-text-fill: " + foreground + ";"
            + "-fx-fill: " + foreground + ";"
            + "-fx-highlight-fill: " + selectionFill + ";"
            + "-fx-highlight-text-fill: " + foreground + ";"
            + "-fx-padding: 0;"
            + "-fx-cursor: text;");
    promptLabel.setStyle("-fx-text-fill: " + PROMPT_COLOR + ";");
  }

  private void applyEditorContent(String content, boolean moveCaretToEnd) {
    if (editorArea == null) {
      return;
    }

    int currentCaret = editorArea.getCaretPosition();
    editorArea.clear();
    for (Segment segment : RichTextContentCodec.decode(content)) {
      if (segment.text().isEmpty()) {
        continue;
      }
      int start = editorArea.getLength();
      editorArea.appendText(segment.text());
      editorArea.setStyle(start, editorArea.getLength(), cssFor(segment.style()));
    }

    int targetCaret = moveCaretToEnd
        ? editorArea.getLength()
        : Math.min(currentCaret, editorArea.getLength());
    editorArea.selectRange(targetCaret, targetCaret);
    editorArea.requestFollowCaret();
    updatePromptVisibility();
    publishCurrentFormattingState();
  }

  private void updatePromptVisibility() {
    if (promptLabel == null || editorArea == null) {
      return;
    }
    promptLabel.setVisible(editorArea.getLength() == 0 && !editorFocused);
  }

  private void publishCurrentFormattingState() {
    formattingStateListener.accept(readFormattingState());
  }

  private FormattingState readFormattingState() {
    if (editorArea == null || editorArea.getLength() == 0) {
      return FormattingState.PLAIN;
    }

    IndexRange selection = editorArea.getSelection();
    if (selection == null || selection.getLength() == 0) {
      int caretPosition = editorArea.getCaretPosition();
      int lookupIndex = caretPosition <= 0 ? 0 : Math.min(caretPosition - 1, editorArea.getLength() - 1);
      return formattingStateFromCss(editorArea.getStyleOfChar(lookupIndex));
    }

    boolean bold = true;
    boolean italic = true;
    boolean underline = true;
    boolean strikethrough = true;
    StyleSpans<String> spans = editorArea.getStyleSpans(selection.getStart(), selection.getEnd());
    for (StyleSpan<String> span : spans) {
      FormattingState spanState = formattingStateFromCss(span.getStyle());
      bold &= spanState.bold();
      italic &= spanState.italic();
      underline &= spanState.underline();
      strikethrough &= spanState.strikethrough();
    }
    return new FormattingState(bold, italic, underline, strikethrough);
  }

  private FormattingState formattingStateFromCss(String css) {
    String normalized = css == null ? "" : css.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    return new FormattingState(
        normalized.contains("-fx-font-weight:bold"),
        normalized.contains("-fx-font-style:italic"),
        normalized.contains("-fx-underline:true"),
        normalized.contains("-fx-strikethrough:true"));
  }

  private String cssFor(TextStyle style) {
    TextStyle safeStyle = style == null ? TextStyle.PLAIN : style;
    List<String> declarations = new ArrayList<>(4);
    if (safeStyle.bold()) {
      declarations.add("-fx-font-weight: bold;");
    }
    if (safeStyle.italic()) {
      declarations.add("-fx-font-style: italic;");
    }
    if (safeStyle.underline()) {
      declarations.add("-fx-underline: true;");
    }
    if (safeStyle.strikethrough()) {
      declarations.add("-fx-strikethrough: true;");
    }
    return String.join(" ", declarations);
  }

  private String serializeDocument() {
    if (editorArea == null || editorArea.getLength() == 0) {
      return "";
    }

    int length = editorArea.getLength();
    int position = 0;
    List<Segment> segments = new ArrayList<>();
    StyleSpans<String> spans = editorArea.getStyleSpans(0, length);
    for (StyleSpan<String> span : spans) {
      int spanLength = span.getLength();
      if (spanLength <= 0) {
        continue;
      }
      segments.add(new Segment(
          editorArea.getText(position, position + spanLength),
          formattingStateFromCss(span.getStyle()).toTextStyle()));
      position += spanLength;
    }

    if (position < length) {
      segments.add(new Segment(
          editorArea.getText(position, length),
          TextStyle.PLAIN));
    }
    return RichTextContentCodec.encode(segments);
  }
}
