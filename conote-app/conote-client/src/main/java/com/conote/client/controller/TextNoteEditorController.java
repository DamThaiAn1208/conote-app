package com.conote.client.controller;

import com.conote.client.model.AppTheme;
import com.conote.client.model.NoteModel;
import com.conote.client.service.CoNoteStore;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingNode;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;

public class TextNoteEditorController {
  private static final Duration CONTENT_SYNC_DELAY = Duration.millis(220);
  private static final String PROMPT_TEXT = "Start writing your note...";
  private static final String DEFAULT_SURFACE = "#fff6d8";
  private static final String LIGHT_TEXT = "#1e3a5f";
  private static final String DARK_TEXT = "#f8fafc";
  private static final String PROMPT_COLOR = "#94a3b8";
  private static final Color LIGHT_SELECTION = new Color(252, 186, 3, 78);
  private static final Color DARK_SELECTION = new Color(252, 186, 3, 110);
  private static final Color LIGHT_SCROLLBAR = new Color(148, 163, 184, 87);
  private static final Color DARK_SCROLLBAR = new Color(203, 213, 225, 82);

  @FXML
  private VBox root;

  @FXML
  private StackPane editorContainer;

  @FXML
  private SwingNode editorHost;

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

  private volatile PlaceholderTextArea swingEditor;
  private volatile JScrollPane swingScrollPane;
  private volatile boolean editorFocused;
  private volatile boolean syncingFromModel;
  private volatile String latestEditorText = "";

  private NoteModel note;
  private CoNoteStore store;
  private Scene attachedScene;
  private Window attachedWindow;
  private boolean bold;
  private boolean italic;
  private boolean underline;
  private String surfaceColor = DEFAULT_SURFACE;
  private AppTheme theme = AppTheme.LIGHT;

  @FXML
  private void initialize() {
    root.setFillWidth(true);
    editorContainer.setCursor(Cursor.TEXT);
    editorHost.setCursor(Cursor.TEXT);
    editorHost.setPickOnBounds(true);
    contentSyncDelay.setOnFinished(event -> flushEditorContent());
    root.sceneProperty().addListener((obs, oldScene, newScene) -> attachWindowListeners(oldScene, newScene));
    createSwingEditor();
  }

  public void setContext(NoteModel note, CoNoteStore store) {
    flushEditorContent();
    if (this.note != null) {
      this.note.contentProperty().removeListener(noteContentListener);
    }

    this.note = note;
    this.store = store;

    String content = note.getContent() == null ? "" : note.getContent();
    latestEditorText = content;
    syncingFromModel = true;
    setEditorText(content, true);
    syncingFromModel = false;
    note.contentProperty().addListener(noteContentListener);
  }

  public void setVisible(boolean visible) {
    if (!visible) {
      flushEditorContent();
    }
    root.setVisible(visible);
    root.setManaged(visible);
  }

  public void applyTypography(boolean bold, boolean italic, boolean underline) {
    this.bold = bold;
    this.italic = italic;
    this.underline = underline;
    applyEditorAppearance();
  }

  public void updatePalette(String surfaceColor, AppTheme theme) {
    this.surfaceColor = (surfaceColor == null || surfaceColor.isBlank()) ? DEFAULT_SURFACE : surfaceColor;
    this.theme = theme == null ? AppTheme.LIGHT : theme;
    applyEditorAppearance();
  }

  private void syncFromModel(String content) {
    String nextValue = content == null ? "" : content;
    if (syncingFromModel || editorFocused || Objects.equals(latestEditorText, nextValue)) {
      return;
    }

    syncingFromModel = true;
    latestEditorText = nextValue;
    setEditorText(nextValue, false);
    syncingFromModel = false;
  }

  private void flushEditorContent() {
    contentSyncDelay.stop();
    if (syncingFromModel || note == null || store == null) {
      return;
    }

    String currentText = latestEditorText;
    if (!Objects.equals(note.getContent(), currentText)) {
      store.updateContent(note, currentText);
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

  private void createSwingEditor() {
    runOnSwingThread(() -> {
      PlaceholderTextArea area = new PlaceholderTextArea(PROMPT_TEXT);
      java.awt.Cursor textCursor =
          java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.TEXT_CURSOR);
      area.setLineWrap(true);
      area.setWrapStyleWord(true);
      area.setTabSize(2);
      area.setEditable(true);
      area.setBorder(BorderFactory.createEmptyBorder());
      area.setMargin(new Insets(0, 0, 0, 0));
      area.setOpaque(true);
      area.setCursor(textCursor);
      area.setText(latestEditorText);
      area.setCaretPosition(area.getDocument().getLength());
      area.getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent event) {
          publishEditorText(area.getText());
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
          publishEditorText(area.getText());
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
          publishEditorText(area.getText());
        }
      });
      area.addFocusListener(new java.awt.event.FocusAdapter() {
        @Override
        public void focusGained(java.awt.event.FocusEvent event) {
          editorFocused = true;
          area.repaint();
        }

        @Override
        public void focusLost(java.awt.event.FocusEvent event) {
          editorFocused = false;
          area.repaint();
          Platform.runLater(TextNoteEditorController.this::flushEditorContent);
        }
      });

      JScrollPane scrollPane = new JScrollPane(area);
      scrollPane.setBorder(BorderFactory.createEmptyBorder());
      scrollPane.setOpaque(true);
      scrollPane.setCursor(java.awt.Cursor.getDefaultCursor());
      scrollPane.setViewportBorder(null);
      scrollPane.getViewport().setOpaque(true);
      scrollPane.getViewport().setCursor(textCursor);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
      scrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
      configureVerticalScrollBar(scrollPane.getVerticalScrollBar());

      swingEditor = area;
      swingScrollPane = scrollPane;
      applyEditorAppearanceOnSwingThread();
      editorHost.setContent(scrollPane);
    });
  }

  private void configureVerticalScrollBar(JScrollBar scrollBar) {
    scrollBar.setUnitIncrement(18);
    scrollBar.setOpaque(false);
    scrollBar.setCursor(java.awt.Cursor.getDefaultCursor());
    scrollBar.setBorder(BorderFactory.createEmptyBorder());
    scrollBar.setPreferredSize(new Dimension(9, Integer.MAX_VALUE));
    scrollBar.setUI(new NoteScrollBarUi(theme == AppTheme.DARK ? DARK_SCROLLBAR : LIGHT_SCROLLBAR));
  }

  private void applyEditorAppearance() {
    runOnSwingThread(this::applyEditorAppearanceOnSwingThread);
  }

  private void applyEditorAppearanceOnSwingThread() {
    PlaceholderTextArea area = swingEditor;
    JScrollPane scrollPane = swingScrollPane;
    if (area == null || scrollPane == null) {
      return;
    }

    Color background = parseColor(surfaceColor, new Color(255, 246, 216));
    Color foreground = parseColor(theme == AppTheme.DARK ? DARK_TEXT : LIGHT_TEXT, Color.BLACK);
    Color prompt = parseColor(PROMPT_COLOR, new Color(148, 163, 184));

    area.setBackground(background);
    area.setForeground(foreground);
    area.setCaretColor(foreground);
    area.setSelectionColor(theme == AppTheme.DARK ? DARK_SELECTION : LIGHT_SELECTION);
    area.setSelectedTextColor(foreground);
    area.setFont(buildEditorFont());
    area.setPromptColor(prompt);

    scrollPane.setBackground(background);
    scrollPane.getViewport().setBackground(background);
    if (scrollPane.getVerticalScrollBar().getUI() instanceof NoteScrollBarUi ui) {
      ui.setThumbColor(theme == AppTheme.DARK ? DARK_SCROLLBAR : LIGHT_SCROLLBAR);
      scrollPane.getVerticalScrollBar().repaint();
    }

    area.repaint();
    scrollPane.repaint();
  }

  private Font buildEditorFont() {
    int style = Font.PLAIN;
    if (bold) {
      style |= Font.BOLD;
    }
    if (italic) {
      style |= Font.ITALIC;
    }

    Font font = new Font(resolveFontFamily(), style, 17);
    if (!underline) {
      return font;
    }

    Map<TextAttribute, Object> attributes = new HashMap<>(font.getAttributes());
    attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
    return font.deriveFont(attributes);
  }

  private String resolveFontFamily() {
    String[] candidates = {"Inter", "Segoe UI", "Arial", Font.SANS_SERIF};
    String[] availableFamilies =
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    for (String candidate : candidates) {
      for (String family : availableFamilies) {
        if (family.equalsIgnoreCase(candidate)) {
          return family;
        }
      }
    }
    return Font.SANS_SERIF;
  }

  private void publishEditorText(String text) {
    latestEditorText = text == null ? "" : text;
    if (syncingFromModel) {
      return;
    }

    Platform.runLater(() -> {
      if (syncingFromModel) {
        return;
      }
      contentSyncDelay.playFromStart();
    });
  }

  private void setEditorText(String content, boolean moveCaretToEnd) {
    String nextValue = content == null ? "" : content;
    latestEditorText = nextValue;
    runOnSwingThreadAndWait(() -> {
      PlaceholderTextArea area = swingEditor;
      if (area == null) {
        return;
      }

      area.setText(nextValue);
      if (moveCaretToEnd) {
        area.setCaretPosition(area.getDocument().getLength());
      } else {
        area.setCaretPosition(Math.min(area.getCaretPosition(), area.getDocument().getLength()));
      }
      area.repaint();
    });
  }

  private void runOnSwingThread(Runnable action) {
    if (SwingUtilities.isEventDispatchThread()) {
      action.run();
    } else {
      SwingUtilities.invokeLater(action);
    }
  }

  private void runOnSwingThreadAndWait(Runnable action) {
    if (SwingUtilities.isEventDispatchThread()) {
      action.run();
      return;
    }

    try {
      SwingUtilities.invokeAndWait(action);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } catch (InvocationTargetException exception) {
      throw new IllegalStateException("Unable to update the note editor", exception.getCause());
    }
  }

  private Color parseColor(String color, Color fallback) {
    if (color == null) {
      return fallback;
    }

    String normalized = color.trim();
    if (normalized.startsWith("#")) {
      try {
        return Color.decode(normalized);
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }

    return fallback;
  }

  private static final class PlaceholderTextArea extends JTextArea {
    private final String promptText;
    private Color promptColor = new Color(148, 163, 184);

    private PlaceholderTextArea(String promptText) {
      this.promptText = promptText;
    }

    private void setPromptColor(Color promptColor) {
      this.promptColor = promptColor == null ? new Color(148, 163, 184) : promptColor;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return getParent() instanceof JViewport viewport
          && viewport.getHeight() > getPreferredSize().height;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      if (!getText().isEmpty() || isFocusOwner() || promptText == null || promptText.isBlank()) {
        return;
      }

      Graphics2D graphics2D = (Graphics2D) graphics.create();
      graphics2D.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      graphics2D.setColor(promptColor);
      graphics2D.setFont(getFont());

      Insets insets = getInsets();
      FontMetrics metrics = graphics2D.getFontMetrics();
      int baseline = insets.top + metrics.getAscent();
      graphics2D.drawString(promptText, insets.left + 2, baseline);
      graphics2D.dispose();
    }
  }

  private static final class NoteScrollBarUi extends BasicScrollBarUI {
    private Color thumbColor;

    private NoteScrollBarUi(Color thumbColor) {
      this.thumbColor = thumbColor;
    }

    private void setThumbColor(Color thumbColor) {
      this.thumbColor = thumbColor;
    }

    @Override
    protected void configureScrollBarColors() {
      trackColor = new Color(0, 0, 0, 0);
      thumbDarkShadowColor = new Color(0, 0, 0, 0);
      thumbHighlightColor = new Color(0, 0, 0, 0);
      thumbLightShadowColor = new Color(0, 0, 0, 0);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
      return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
      return createZeroButton();
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle trackBounds) {
      // Intentionally empty to keep the track invisible.
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle thumbBounds) {
      if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
        return;
      }

      Graphics2D graphics2D = (Graphics2D) graphics.create();
      graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics2D.setColor(thumbColor);
      int x = thumbBounds.x + 2;
      int y = thumbBounds.y + 1;
      int width = Math.max(4, thumbBounds.width - 4);
      int height = Math.max(18, thumbBounds.height - 2);
      graphics2D.fillRoundRect(x, y, width, height, width, width);
      graphics2D.dispose();
    }

    private JButton createZeroButton() {
      JButton button = new JButton();
      button.setBorder(BorderFactory.createEmptyBorder());
      button.setOpaque(false);
      button.setFocusable(false);
      button.setPreferredSize(new Dimension(0, 0));
      return button;
    }
  }
}
