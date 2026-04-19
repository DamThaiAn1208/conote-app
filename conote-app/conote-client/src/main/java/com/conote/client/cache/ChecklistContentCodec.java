package com.conote.client.cache;

import com.conote.client.model.ChecklistItemModel;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ChecklistContentCodec {
  private static final String PREFIX = "__CONOTE_CHECKLIST__::";
  private static final JsonStore JSON_STORE = new JsonStore();
  private static final TypeReference<List<StoredChecklistItem>> CHECKLIST_ITEM_LIST =
      new TypeReference<>() {
      };

  private ChecklistContentCodec() {
  }

  public static boolean isEncodedChecklist(String value) {
    return value != null && value.startsWith(PREFIX);
  }

  public static List<ChecklistItemModel> decode(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }

    if (!isEncodedChecklist(value)) {
      return fromPlainText(value);
    }

    try {
      List<StoredChecklistItem> storedItems =
          JSON_STORE.fromJson(value.substring(PREFIX.length()), CHECKLIST_ITEM_LIST);
      if (storedItems == null || storedItems.isEmpty()) {
        return List.of();
      }

      List<ChecklistItemModel> decoded = new ArrayList<>();
      for (StoredChecklistItem storedItem : storedItems) {
        if (storedItem == null) {
          continue;
        }
        decoded.add(new ChecklistItemModel(storedItem.getText(), storedItem.isChecked()));
      }
      return decoded;
    } catch (RuntimeException exception) {
      return fromPlainText(value);
    }
  }

  public static String encode(List<ChecklistItemModel> items) {
    if (items == null || items.isEmpty()) {
      return "";
    }

    List<StoredChecklistItem> storedItems = new ArrayList<>();
    for (ChecklistItemModel item : items) {
      if (item == null) {
        continue;
      }
      storedItems.add(new StoredChecklistItem(item.getText(), item.isChecked()));
    }

    return storedItems.isEmpty() ? "" : PREFIX + JSON_STORE.toJson(storedItems);
  }

  public static String toPlainText(String value) {
    return toPlainText(decode(value));
  }

  public static String toPlainText(List<ChecklistItemModel> items) {
    if (items == null || items.isEmpty()) {
      return "";
    }

    return items.stream()
        .filter(item -> item != null)
        .map(item -> item.getText() == null ? "" : item.getText())
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private static List<ChecklistItemModel> fromPlainText(String value) {
    String normalized = value == null ? "" : value.replace("\r\n", "\n");
    if (normalized.isBlank()) {
      return List.of();
    }

    List<ChecklistItemModel> items = new ArrayList<>();
    for (String line : normalized.split("\\R", -1)) {
      items.add(new ChecklistItemModel(line, false));
    }
    return items;
  }

  private static class StoredChecklistItem {
    private String text;
    private boolean checked;

    public StoredChecklistItem() {
    }

    StoredChecklistItem(String text, boolean checked) {
      this.text = text == null ? "" : text;
      this.checked = checked;
    }

    public String getText() {
      return text;
    }

    public boolean isChecked() {
      return checked;
    }
  }
}
