package com.conote.client.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.app.ClientApplication;
import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.model.ChecklistItemModel;
import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.RichTextContentCodec;
import com.conote.client.util.RichTextContentCodec.Segment;
import com.conote.client.util.RichTextContentCodec.TextStyle;
import com.conote.client.util.ViewLoader;
import com.conote.common.enums.NoteType;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.fxmisc.richtext.InlineCssTextArea;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class NoteCardDisplayConsistencyTest {
  private static final String STORAGE_OVERRIDE_PROPERTY = "conote.storage.dir";

  @TempDir
  Path tempDir;

  private VBox host;
  private Stage stage;
  private CoNoteStore store;

  @BeforeEach
  void setUpStorage() {
    System.setProperty(STORAGE_OVERRIDE_PROPERTY, tempDir.resolve("conote-storage").toString());
    ClientStoragePaths.resetForTesting();
    store = new CoNoteStore();
  }

  @AfterEach
  void tearDownStorage() {
    System.clearProperty(STORAGE_OVERRIDE_PROPERTY);
    ClientStoragePaths.resetForTesting();
  }

  @Start
  private void start(Stage stage) {
    host = new VBox();
    this.stage = stage;

    Scene scene = new Scene(host, 440, 320);
    scene.getStylesheets().add(ClientApplication.stylesheetUrl());

    stage.setScene(scene);
    stage.show();
    stage.toFront();
    stage.requestFocus();
  }

  @Test
  void textNoteKeepsSameContentFontSizeWhenSelected(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    AtomicReference<NoteModel> noteRef = new AtomicReference<>();
    robot.interact(() -> {
      NoteModel created = store.createNote(NoteType.TEXT);
      store.updatePlainTextContent(created,
          "Discussed goals for the next quarter. Focus on expanding the user base and improving the onboarding experience. "
              + "Assign tasks to the marketing team by Friday. Review the campaign brief with design, finalize launch milestones, "
              + "and share the updated ownership tracker before the weekly sync.");
      store.setExpandedNoteId(null);
      mountCard(created);
      noteRef.set(created);
    });
    WaitForAsyncUtils.waitForFxEvents();

    NoteModel note = noteRef.get();
    Label previewLabel = robot.lookup("#previewLabel").queryAs(Label.class);
    double previewFontSize = previewLabel.getFont().getSize();
    double previewTopY = previewLabel.localToScene(previewLabel.getBoundsInLocal()).getMinY();
    double previewTextMinX = visibleTextMinX(previewLabel);
    String previewText = previewLabel.getText();

    assertTrue(previewText.split("\\R").length <= 3,
        "Collapsed text note preview should be clamped to three lines");
    assertTrue(previewText.endsWith("..."),
        "Collapsed text note preview should end with ellipsis when content exceeds three lines");

    robot.interact(() -> store.setExpandedNoteId(note.getId()));
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(260);
    WaitForAsyncUtils.waitForFxEvents();

    TextArea quickTextArea = robot.lookup("#quickTextArea").queryAs(TextArea.class);
    double selectedTopY = quickTextArea.localToScene(quickTextArea.getBoundsInLocal()).getMinY();
    assertEquals(previewFontSize, quickTextArea.getFont().getSize(), 0.01,
        "Text note content font size should stay unchanged between collapsed and selected states");
    assertEquals(previewTopY, selectedTopY, 1.0,
        "Selecting a text note should keep the content block anchored in the same vertical position");
    assertEquals(previewTextMinX, visibleTextMinX(quickTextArea), 1.0,
        "Selecting a text note should keep the text aligned to the same left edge as the collapsed preview");
    assertFalse(quickTextArea.isFocused(),
        "Selecting a text note should not auto-focus the editor and scroll away from the top content");
  }

  @Test
  void collapsedNoteTagsAreMouseTransparentInMainWindow(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    robot.interact(() -> {
      NoteModel created = store.createNote(NoteType.TEXT);
      created.getTags().setAll("personal");
      store.setExpandedNoteId(null);
      mountCard(created);
    });
    WaitForAsyncUtils.waitForFxEvents();

    HBox tagChip = robot.lookup(".tag-chip").queryAs(HBox.class);
    assertTrue(tagChip.isMouseTransparent(),
        "Tags in main window note cards should not behave like separate clickable targets");
  }

  @Test
  void reselectingLongTextNoteResetsScrollToTop(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    AtomicReference<NoteModel> noteRef = new AtomicReference<>();
    robot.interact(() -> {
      NoteModel created = store.createNote(NoteType.TEXT);
      store.updatePlainTextContent(created,
          "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6\nLine 7\nLine 8\nLine 9\nLine 10");
      mountCard(created);
      store.setExpandedNoteId(created.getId());
      noteRef.set(created);
    });
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(260);
    WaitForAsyncUtils.waitForFxEvents();

    TextArea quickTextArea = robot.lookup("#quickTextArea").queryAs(TextArea.class);
    robot.interact(() -> quickTextArea.setScrollTop(9999));
    WaitForAsyncUtils.waitForFxEvents();

    robot.interact(() -> store.setExpandedNoteId(null));
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(220);
    robot.interact(() -> store.setExpandedNoteId(noteRef.get().getId()));
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(260);
    WaitForAsyncUtils.waitForFxEvents();

    assertEquals(0.0, quickTextArea.getScrollTop(), 0.01,
        "Re-selecting a long text note should reset the main window text viewport to the top");
  }

  @Test
  void checklistNoteMatchesPrototypeAcrossCollapsedSelectedAndCheckedStates(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    AtomicReference<NoteModel> noteRef = new AtomicReference<>();
    robot.interact(() -> {
      NoteModel created = store.createNote(NoteType.CHECKLIST);
      created.getChecklistItems().setAll(
          new ChecklistItemModel("Milk", false),
          new ChecklistItemModel("Eggs", false),
          new ChecklistItemModel("Bread", false),
          new ChecklistItemModel("SupercalifragilisticexpialidociousSupercalifragilisticexpialidocious"
              + "SupercalifragilisticexpialidociousSupercalifragilisticexpialidocious", false),
          new ChecklistItemModel("Coffee beans tomatoes apples yogurt pasta juice crackers spinach bananas oranges", false));
      store.setExpandedNoteId(null);
      mountCard(created);
      noteRef.set(created);
    });
    WaitForAsyncUtils.waitForFxEvents();

    NoteModel note = noteRef.get();
    Label previewText = robot.lookup(".checklist-preview-inline").queryAs(Label.class);
    assertTrue(previewText.getText().contains(", "),
        "Collapsed checklist preview should render items inline separated by commas");
    assertTrue(previewText.getText().split("\\R").length <= 3,
        "Collapsed checklist preview should be clamped to three lines");
    assertTrue(previewText.getText().endsWith("..."),
        "Collapsed checklist preview should end with ellipsis when content exceeds three lines");

    robot.interact(() -> store.setExpandedNoteId(note.getId()));
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(260);
    WaitForAsyncUtils.waitForFxEvents();

    VBox checklistItemsBox = robot.lookup("#checklistItemsBox").queryAs(VBox.class);
    CheckBox checkBox = robot.lookup(".check-box").queryAs(CheckBox.class);
    TextField editorField = robot.lookup(".inline-checklist-field").queryAs(TextField.class);
    HBox editorRow = robot.lookup(".inline-checklist-row").queryAs(HBox.class);
    Button removeButton = robot.lookup(".mini-icon-button").queryAs(Button.class);
    Region checkBoxBox = lookupRegion(checkBox, ".box");

    double selectedRowHeight = editorRow.getLayoutBounds().getHeight();
    double selectedItemSpacing = checklistItemsBox.getSpacing();
    double selectedRowSpacing = editorRow.getSpacing();
    double selectedCheckBoxWidth = checkBoxBox.getLayoutBounds().getWidth();
    double selectedCheckBoxHeight = checkBoxBox.getLayoutBounds().getHeight();
    double removeButtonWidth = removeButton.getLayoutBounds().getWidth();
    double removeButtonHeight = removeButton.getLayoutBounds().getHeight();

    assertTrue(isTransparent(editorField.getBackground()),
        "Selected checklist item field should keep a transparent background in the main window");

    robot.clickOn(editorField);
    WaitForAsyncUtils.waitForFxEvents();
    Color textColorBeforeCheck = textFill(editorField);

    assertEquals(selectedRowHeight, editorRow.getLayoutBounds().getHeight(), 0.75,
        "Checklist row height should stay stable in edit mode");
    assertEquals(selectedItemSpacing, checklistItemsBox.getSpacing(), 0.01,
        "Checklist item spacing should stay stable in edit mode");
    assertEquals(selectedRowSpacing, editorRow.getSpacing(), 0.01,
        "Checklist row spacing should stay stable in edit mode");
    assertEquals(selectedCheckBoxWidth, checkBoxBox.getLayoutBounds().getWidth(), 0.01,
        "Checklist checkbox width should stay stable in edit mode");
    assertEquals(selectedCheckBoxHeight, checkBoxBox.getLayoutBounds().getHeight(), 0.01,
        "Checklist checkbox height should stay stable in edit mode");
    assertEquals(removeButtonWidth, removeButton.getLayoutBounds().getWidth(), 0.01,
        "Checklist remove button width should stay stable in edit mode");
    assertEquals(removeButtonHeight, removeButton.getLayoutBounds().getHeight(), 0.01,
        "Checklist remove button height should stay stable in edit mode");

    robot.interact(checkBox::fire);
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(120);
    WaitForAsyncUtils.waitForFxEvents();

    Region mark = lookupRegion(checkBox, ".mark");
    assertTrue(note.getChecklistItems().getFirst().isChecked(),
        "Checking an item in the selected checklist note should update the note model");
    assertTrue(hasStrikethrough(editorField),
        "Checked checklist items should render with strikethrough text in the main window");
    assertColorNear(textColorBeforeCheck, textFill(editorField), 0.03,
        "Checked checklist items should keep the same text color instead of fading out");
    assertColorNear(Color.web("#fcba03"), backgroundFill(checkBoxBox), 0.03,
        "Checked checklist checkbox should use the prototype yellow fill");
    assertColorNear(Color.WHITE, backgroundFill(mark), 0.03,
        "Checked checklist checkbox should render a white tick mark");
  }

  @Test
  void richTextNotePreviewAndSelectedViewKeepInlineFormatting(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    AtomicReference<NoteModel> noteRef = new AtomicReference<>();
    robot.interact(() -> {
      NoteModel created = store.createNote(NoteType.TEXT);
      created.setContent(RichTextContentCodec.encode(List.of(
          new Segment("Bold ", new TextStyle(true, false, false, false)),
          new Segment("Italic ", new TextStyle(false, true, false, false)),
          new Segment("Underline ", new TextStyle(false, false, true, false)),
          new Segment("Strike", new TextStyle(false, false, false, true)))));
      store.setExpandedNoteId(null);
      mountCard(created);
      noteRef.set(created);
    });
    WaitForAsyncUtils.waitForFxEvents();

    TextFlow previewFlow = robot.lookup("#richPreviewFlow").queryAs(TextFlow.class);
    assertTrue(previewFlow.isVisible(), "Collapsed rich text note should use the rich preview flow");
    assertRichFormattingPresent(previewFlow);

    robot.interact(() -> store.setExpandedNoteId(noteRef.get().getId()));
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(260);
    WaitForAsyncUtils.waitForFxEvents();

    InlineCssTextArea editorArea = robot.lookup(".text-note-editor-area").queryAs(InlineCssTextArea.class);
    assertTrue(editorArea.isEditable(), "Selected rich text note should use an editable rich text area");
    assertTrue(editorArea.getStyleOfChar(0).contains("-fx-font-weight: bold;"),
        "Selected rich text note should preserve bold formatting in the editor");
    assertTrue(editorArea.getStyleOfChar(5).contains("-fx-font-style: italic;"),
        "Selected rich text note should preserve italic formatting in the editor");
    assertTrue(editorArea.getStyleOfChar(12).contains("-fx-underline: true;"),
        "Selected rich text note should preserve underline formatting in the editor");
    assertTrue(editorArea.getStyleOfChar(editorArea.getLength() - 1).contains("-fx-strikethrough: true;"),
        "Selected rich text note should preserve strikethrough formatting in the editor");
  }

  @Test
  void clickingRichTextPreviewSelectsTheNote(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    AtomicReference<NoteModel> noteRef = new AtomicReference<>();
    robot.interact(() -> {
      NoteModel created = store.createNote(NoteType.TEXT);
      created.setContent(RichTextContentCodec.encode(List.of(
          new Segment("Bold preview", new TextStyle(true, false, false, false)))));
      store.setExpandedNoteId(null);
      mountCard(created);
      noteRef.set(created);
    });
    WaitForAsyncUtils.waitForFxEvents();

    TextFlow previewFlow = robot.lookup("#richPreviewFlow").queryAs(TextFlow.class);
    Text previewText = previewFlow.getChildren().stream()
        .filter(Text.class::isInstance)
        .map(Text.class::cast)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected rich preview text node"));
    robot.interact(() -> Event.fireEvent(previewText, new MouseEvent(
        MouseEvent.MOUSE_PRESSED,
        1,
        1,
        1,
        1,
        MouseButton.PRIMARY,
        1,
        false,
        false,
        false,
        false,
        true,
        false,
        false,
        true,
        false,
        false,
        null)));
    WaitForAsyncUtils.waitForFxEvents();

    assertEquals(noteRef.get().getId(), store.expandedNoteIdProperty().get(),
        "Clicking formatted rich preview text in a note card should select that note");
  }

  @Test
  void richTextNoteCanDeleteFormattedContentFromMainWindow(FxRobot robot) throws Exception {
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> stage.isFocused());

    AtomicReference<NoteModel> noteRef = new AtomicReference<>();
    robot.interact(() -> {
      NoteModel created = store.createNote(NoteType.TEXT);
      created.setContent(RichTextContentCodec.encode(List.of(
          new Segment("Bold", new TextStyle(true, false, false, false)),
          new Segment(" ", TextStyle.PLAIN),
          new Segment("Underlined", new TextStyle(false, false, true, false)))));
      mountCard(created);
      store.setExpandedNoteId(created.getId());
      noteRef.set(created);
    });
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(260);
    WaitForAsyncUtils.waitForFxEvents();

    InlineCssTextArea editorArea = robot.lookup(".text-note-editor-area").queryAs(InlineCssTextArea.class);
    robot.interact(() -> editorArea.replaceText(0, editorArea.getLength(), ""));
    WaitForAsyncUtils.waitForFxEvents();
    Thread.sleep(350);
    WaitForAsyncUtils.waitForFxEvents();

    assertTrue(noteRef.get().getPlainTextContent().isEmpty(),
        "Deleting formatted text from the main window should update the note content");
  }

  private void mountCard(NoteModel note) {
    LoadedView<NoteCardController> view = ViewLoader.load("/fxml/shared/NoteCard.fxml");
    view.controller().setContext(note, store, null);
    host.getChildren().setAll(view.root());
    host.applyCss();
    host.layout();
  }

  private void assertRichFormattingPresent(TextFlow flow) {
    List<Text> textNodes = flow.getChildren().stream()
        .filter(Text.class::isInstance)
        .map(Text.class::cast)
        .toList();

    assertTrue(textNodes.stream().anyMatch(node -> node.getFont().getStyle().toLowerCase().contains("bold")),
        "Rich text view should preserve bold segments");
    assertTrue(textNodes.stream().anyMatch(node -> node.getFont().getStyle().toLowerCase().contains("italic")),
        "Rich text view should preserve italic segments");
    assertTrue(textNodes.stream().anyMatch(Text::isUnderline),
        "Rich text view should preserve underline segments");
    assertTrue(textNodes.stream().anyMatch(Text::isStrikethrough),
        "Rich text view should preserve strikethrough segments");
  }

  private double visibleTextMinX(Node parent) {
    return parent.lookupAll(".text").stream()
        .filter(Text.class::isInstance)
        .map(Text.class::cast)
        .filter(text -> text.isVisible() && text.getText() != null && !text.getText().isBlank())
        .mapToDouble(text -> text.localToScene(text.getBoundsInLocal()).getMinX())
        .min()
        .orElseThrow(() -> new AssertionError("Expected visible text node inside " + parent));
  }

  private Region lookupRegion(Node parent, String selector) {
    Node node = parent.lookup(selector);
    if (!(node instanceof Region region)) {
      throw new AssertionError("Expected region for selector " + selector);
    }
    return region;
  }

  private boolean isTransparent(Background background) {
    return background == null
        || background.getFills().isEmpty()
        || background.getFills().stream().allMatch(fill -> fill.getFill() instanceof Color color
            && color.getOpacity() == 0.0);
  }

  private boolean hasStrikethrough(TextField field) {
    return field.lookupAll(".text").stream()
        .filter(Text.class::isInstance)
        .map(Text.class::cast)
        .anyMatch(Text::isStrikethrough);
  }

  private Color textFill(TextField field) {
    return field.lookupAll(".text").stream()
        .filter(Text.class::isInstance)
        .map(Text.class::cast)
        .map(Text::getFill)
        .filter(Color.class::isInstance)
        .map(Color.class::cast)
        .findFirst()
        .orElse(Color.TRANSPARENT);
  }

  private Color backgroundFill(Region region) {
    if (region.getBackground() == null || region.getBackground().getFills().isEmpty()) {
      return Color.TRANSPARENT;
    }
    return region.getBackground().getFills().getFirst().getFill() instanceof Color color
        ? color
        : Color.TRANSPARENT;
  }

  private void assertColorNear(Color expected, Color actual, double tolerance, String message) {
    assertEquals(expected.getRed(), actual.getRed(), tolerance, message);
    assertEquals(expected.getGreen(), actual.getGreen(), tolerance, message);
    assertEquals(expected.getBlue(), actual.getBlue(), tolerance, message);
    assertEquals(expected.getOpacity(), actual.getOpacity(), tolerance, message);
  }
}
