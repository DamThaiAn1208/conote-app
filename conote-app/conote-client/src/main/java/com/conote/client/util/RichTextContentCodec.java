package com.conote.client.util;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RichTextContentCodec {
  private static final String PREFIX = "__CONOTE_RICH_TEXT__::";
  private static final int VERSION = 1;
  private static final Gson GSON = new Gson();

  private RichTextContentCodec() {
  }

  public static String plainText(String value) {
    return value == null ? "" : value;
  }

  public static boolean isEncodedRichText(String value) {
    return value != null && value.startsWith(PREFIX);
  }

  public static boolean hasRichFormatting(String value) {
    return decode(value).stream().anyMatch(segment -> !segment.style().isPlain());
  }

  public static String toPlainText(String value) {
    StringBuilder builder = new StringBuilder();
    for (Segment segment : decode(value)) {
      builder.append(segment.text());
    }
    return builder.toString();
  }

  public static List<Segment> decode(String value) {
    if (value == null || value.isEmpty()) {
      return List.of();
    }

    if (!isEncodedRichText(value)) {
      return List.of(new Segment(value, TextStyle.PLAIN));
    }

    try {
      StoredDocument storedDocument =
          GSON.fromJson(value.substring(PREFIX.length()), StoredDocument.class);
      if (storedDocument == null || storedDocument.segments == null || storedDocument.segments.isEmpty()) {
        return List.of(new Segment(value, TextStyle.PLAIN));
      }

      List<Segment> decoded = new ArrayList<>();
      for (StoredSegment storedSegment : storedDocument.segments) {
        if (storedSegment == null) {
          continue;
        }
        decoded.add(new Segment(
            storedSegment.text,
            new TextStyle(
                storedSegment.bold,
                storedSegment.italic,
                storedSegment.underline,
                storedSegment.strikethrough)));
      }
      return normalize(decoded);
    } catch (JsonParseException exception) {
      return List.of(new Segment(value, TextStyle.PLAIN));
    }
  }

  public static String encode(List<Segment> segments) {
    List<Segment> normalized = normalize(segments);
    if (normalized.isEmpty()) {
      return "";
    }

    boolean hasFormatting = normalized.stream().anyMatch(segment -> !segment.style().isPlain());
    if (!hasFormatting) {
      return toPlainText(normalized);
    }

    StoredDocument storedDocument = new StoredDocument();
    storedDocument.version = VERSION;
    storedDocument.segments = new ArrayList<>();
    for (Segment segment : normalized) {
      storedDocument.segments.add(new StoredSegment(segment));
    }
    return PREFIX + GSON.toJson(storedDocument);
  }

  public static String toPlainText(List<Segment> segments) {
    StringBuilder builder = new StringBuilder();
    for (Segment segment : normalize(segments)) {
      builder.append(segment.text());
    }
    return builder.toString();
  }

  private static List<Segment> normalize(List<Segment> segments) {
    if (segments == null || segments.isEmpty()) {
      return List.of();
    }

    List<Segment> normalized = new ArrayList<>();
    for (Segment candidate : segments) {
      if (candidate == null || candidate.text().isEmpty()) {
        continue;
      }

      if (!normalized.isEmpty()) {
        Segment previous = normalized.get(normalized.size() - 1);
        if (Objects.equals(previous.style(), candidate.style())) {
          normalized.set(
              normalized.size() - 1,
              new Segment(previous.text() + candidate.text(), previous.style()));
          continue;
        }
      }

      normalized.add(candidate);
    }
    return List.copyOf(normalized);
  }

  public record TextStyle(boolean bold, boolean italic, boolean underline, boolean strikethrough) {
    public static final TextStyle PLAIN = new TextStyle(false, false, false, false);

    public boolean isPlain() {
      return !bold && !italic && !underline && !strikethrough;
    }
  }

  public record Segment(String text, TextStyle style) {
    public Segment {
      text = text == null ? "" : text;
      style = style == null ? TextStyle.PLAIN : style;
    }
  }

  private static final class StoredDocument {
    private int version;
    private List<StoredSegment> segments;
  }

  private static final class StoredSegment {
    private String text;
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strikethrough;

    private StoredSegment(Segment segment) {
      this.text = segment.text();
      this.bold = segment.style().bold();
      this.italic = segment.style().italic();
      this.underline = segment.style().underline();
      this.strikethrough = segment.style().strikethrough();
    }
  }
}
