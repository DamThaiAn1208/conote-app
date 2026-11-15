package com.conote.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.conote.common.enums.NoteType;
import org.junit.jupiter.api.Test;

class NoteContentTest {
    @Test
    void extractsSearchTextWithoutChangingRichOrChecklistPayload() {
        assertEquals("Tiếng Việt", NoteContent.searchText(NoteType.TEXT,
                "__CONOTE_RICH_TEXT__::{\"version\":1,\"segments\":[{\"text\":\"Tiếng \"},{\"text\":\"Việt\"}] }"));
        assertEquals("Mua sữa\nĐọc sách", NoteContent.searchText(NoteType.CHECKLIST,
                "__CONOTE_CHECKLIST__::[{\"text\":\"Mua sữa\",\"checked\":true},{\"text\":\"Đọc sách\",\"checked\":false}]"));
        assertEquals("ordinary", NoteContent.searchText(NoteType.TEXT, "ordinary"));
    }

    @Test
    void rejectsMalformedEncodedContent() {
        assertThrows(IllegalArgumentException.class, () -> NoteContent.searchText(NoteType.TEXT,
                "__CONOTE_RICH_TEXT__::{\"version\":2,\"segments\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> NoteContent.searchText(NoteType.CHECKLIST,
                "__CONOTE_CHECKLIST__::[{}]"));
        assertThrows(IllegalArgumentException.class, () -> NoteContent.searchText(NoteType.TEXT,
                "__CONOTE_RICH_TEXT__::{\"version\":1,\"segments\":[null]}"));
    }
}
