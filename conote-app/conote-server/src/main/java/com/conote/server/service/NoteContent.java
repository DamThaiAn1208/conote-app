package com.conote.server.service;

import com.conote.common.enums.NoteType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;

/** Validates the client cache encoding and extracts searchable text without changing stored content. */
final class NoteContent {
    private static final String RICH = "__CONOTE_RICH_TEXT__::";
    private static final String CHECKLIST = "__CONOTE_CHECKLIST__::";

    private NoteContent() { }

    static String searchText(NoteType type, String content) {
        if (content == null || content.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
            throw new IllegalArgumentException("Content exceeds 256 KiB or is missing");
        }
        if (type == NoteType.TEXT) {
            if (!content.startsWith(RICH)) return content;
            JsonObject document = object(content.substring(RICH.length()));
            if (!document.has("version") || !document.get("version").isJsonPrimitive()
                    || !document.get("version").getAsJsonPrimitive().isNumber()
                    || document.get("version").getAsInt() != 1) {
                throw new IllegalArgumentException("Unsupported rich text version");
            }
            JsonArray segments = array(document, "segments");
            StringBuilder result = new StringBuilder();
            for (JsonElement segment : segments) {
                if (!segment.isJsonObject()) throw new IllegalArgumentException("Invalid rich text segment");
                JsonObject item = segment.getAsJsonObject();
                result.append(string(item, "text"));
            }
            return result.toString();
        }
        if (type == NoteType.CHECKLIST) {
            if (!content.startsWith(CHECKLIST)) return content;
            JsonElement document = JsonParser.parseString(content.substring(CHECKLIST.length()));
            if (!document.isJsonArray()) throw new IllegalArgumentException("Invalid checklist JSON");
            StringBuilder result = new StringBuilder();
            for (JsonElement item : document.getAsJsonArray()) {
                if (!item.isJsonObject()) throw new IllegalArgumentException("Invalid checklist item");
                JsonObject row = item.getAsJsonObject();
                if (!row.has("checked") || !row.get("checked").isJsonPrimitive()
                        || !row.get("checked").getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException("Invalid checklist item");
                }
                if (!result.isEmpty()) result.append('\n');
                result.append(string(row, "text"));
            }
            return result.toString();
        }
        throw new IllegalArgumentException("Note type is required");
    }

    private static JsonObject object(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid rich text JSON");
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return object.getAsJsonArray(key);
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return object.get(key).getAsString();
    }
}
