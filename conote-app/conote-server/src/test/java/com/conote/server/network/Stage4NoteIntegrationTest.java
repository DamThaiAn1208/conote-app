package com.conote.server.network;

import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.dto.tag.TagDtos;
import com.conote.common.enums.NoteType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs the protocol handlers against a dedicated MariaDB test database. */
class Stage4NoteIntegrationTest {
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final RequestDispatcher dispatcher = new RequestDispatcher();

    @Test
    void notesTagsAndPerUserStateRoundTripWithVersionAndAccessChecks() throws Exception {
        String url = System.getenv("CONOTE_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "No dedicated MariaDB test URL configured");
        assertTrue(url.matches("(?i)^jdbc:mariadb://[^/]+/conote_stage2_test(?:\\?.*)?$"),
                "Refusing to run integration test outside conote_stage2_test");
        String user = System.getenv("CONOTE_DB_USER");
        String password = System.getenv("CONOTE_DB_PASSWORD");
        assertTrue(user != null && password != null, "DB test credentials are required");
        applySchema(url, user, password);

        String suffix = UUID.randomUUID().toString();
        String aliceEmail = "alice+" + suffix + "@example.test";
        String bobEmail = "bob+" + suffix + "@example.test";
        String richId = UUID.randomUUID().toString();
        String checklistId = UUID.randomUUID().toString();
        String rich = "__CONOTE_RICH_TEXT__::{\"version\":1,\"segments\":[{\"text\":\"Cà phê ☕\"}] }";
        String checklist = "__CONOTE_CHECKLIST__::[{\"text\":\"Mua sữa\",\"checked\":false}]";

        try {
            assertSuccess(send(Operation.AUTH_REGISTER, null,
                    new RegisterRequest(aliceEmail, "password-a", "Alice")));
            assertSuccess(send(Operation.AUTH_REGISTER, null,
                    new RegisterRequest(bobEmail, "password-b", "Bob")));
            String alice = token(send(Operation.AUTH_LOGIN, null,
                    new LoginRequest(aliceEmail, "password-a")));
            String bob = token(send(Operation.AUTH_LOGIN, null,
                    new LoginRequest(bobEmail, "password-b")));

            long tagId = assertSuccess(send(Operation.TAG_CREATE, alice,
                    new TagDtos.Create("Học tập"))).data().getAsJsonObject().get("tagId").getAsLong();
            ProtocolResponse created = send(Operation.NOTE_CREATE, alice,
                    new NoteDtos.Create(richId, NoteType.TEXT, "Ý tưởng", rich, "amber", List.of()));
            assertEquals(1, version(assertSuccess(created)));
            assertEquals(rich, data(send(Operation.NOTE_GET, alice, new NoteDtos.Id(richId)))
                    .get("content").getAsString());
            assertEquals(ErrorCode.CONFLICT, send(Operation.NOTE_CREATE, alice,
                    new NoteDtos.Create(richId, NoteType.TEXT, "Trùng", rich, "amber", List.of())).code());
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.NOTE_GET, bob, new NoteDtos.Id(richId)).code());
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.NOTE_UPDATE, bob,
                    new NoteDtos.Update(richId, 1, "Lạ", rich, "blue")).code());

            assertEquals(2, version(assertSuccess(send(Operation.TAG_ATTACH, alice,
                    new TagDtos.Link(richId, tagId, 1)))));
            assertTrue(data(send(Operation.NOTE_STATE_SET, alice,
                    new NoteDtos.StateSet(richId, true, true))).get("pinned").getAsBoolean());
            assertTrue(data(send(Operation.NOTE_GET, alice, new NoteDtos.Id(richId)))
                    .get("favorite").getAsBoolean());
            assertSuccess(send(Operation.NOTE_REORDER, alice,
                    new NoteDtos.Reorder(List.of(new NoteDtos.OrderItem(richId, 7)))));
            assertEquals(7, data(send(Operation.NOTE_GET, alice, new NoteDtos.Id(richId)))
                    .get("sortOrder").getAsLong());

            assertEquals(1, data(send(Operation.NOTE_LIST, alice,
                    new NoteDtos.ListQuery("Cà phê", List.of(tagId), NoteDtos.SortBy.MANUAL, false)))
                    .getAsJsonArray("notes").size());
            assertEquals(0, data(send(Operation.NOTE_LIST, bob,
                    new NoteDtos.ListQuery(null, List.of(), NoteDtos.SortBy.UPDATED_AT, true)))
                    .getAsJsonArray("notes").size());

            ProtocolResponse conflict = send(Operation.NOTE_UPDATE, alice,
                    new NoteDtos.Update(richId, 1, "Ghi đè", "Sai", "blue"));
            assertEquals(ErrorCode.CONFLICT, conflict.code());
            assertEquals(2, conflict.data().getAsJsonObject().get("version").getAsLong());
            assertEquals(3, version(assertSuccess(send(Operation.NOTE_UPDATE, alice,
                    new NoteDtos.Update(richId, 2, "Đã sửa", rich, "blue")))));
            assertEquals(4, version(assertSuccess(send(Operation.TAG_DETACH, alice,
                    new TagDtos.Link(richId, tagId, 3)))));

            assertEquals(1, version(assertSuccess(send(Operation.NOTE_CREATE, alice,
                    new NoteDtos.Create(checklistId, NoteType.CHECKLIST, "Việc cần làm",
                            checklist, "green", List.of())))));
            assertEquals(checklist, data(send(Operation.NOTE_GET, alice, new NoteDtos.Id(checklistId)))
                    .get("content").getAsString());
            assertEquals(1, data(send(Operation.NOTE_LIST, alice,
                    new NoteDtos.ListQuery("Mua sữa", List.of(), NoteDtos.SortBy.UPDATED_AT, true)))
                    .getAsJsonArray("notes").size());

            assertSuccess(send(Operation.TAG_RENAME, alice, new TagDtos.Rename(tagId, "Công việc")));
            assertEquals("Công việc", data(send(Operation.TAG_LIST, alice, null))
                    .getAsJsonArray("tags").get(0).getAsJsonObject().get("name").getAsString());
            assertSuccess(send(Operation.TAG_DELETE, alice, new TagDtos.Id(tagId)));
            assertFalse(data(send(Operation.TAG_LIST, alice, null)).getAsJsonArray("tags").iterator().hasNext());

            assertSuccess(send(Operation.NOTE_DELETE, alice, new NoteDtos.Delete(richId, 4)));
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.NOTE_GET, alice, new NoteDtos.Id(richId)).code());
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.NOTE_GET, bob, new NoteDtos.Id(richId)).code());
        } finally {
            cleanup(url, user, password, richId, checklistId, aliceEmail, bobEmail);
        }
    }

    private ProtocolResponse send(Operation operation, String token, Object payload) {
        return dispatcher.dispatch(new ProtocolRequest(UUID.randomUUID().toString(), operation, token,
                payload == null ? null : gson.toJsonTree(payload)));
    }

    private ProtocolResponse assertSuccess(ProtocolResponse response) {
        assertTrue(response.success(), () -> response.code() + ": " + response.message());
        return response;
    }

    private com.google.gson.JsonObject data(ProtocolResponse response) {
        return assertSuccess(response).data().getAsJsonObject();
    }

    private long version(ProtocolResponse response) {
        return response.data().getAsJsonObject().get("version").getAsLong();
    }

    private String token(ProtocolResponse response) {
        return data(response).get("token").getAsString();
    }

    private static void applySchema(String url, String user, String password) throws Exception {
        StringBuilder sql = new StringBuilder();
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            for (String line : Files.readAllLines(Path.of("src/main/resources/db/schema.sql"))) {
                int comment = line.indexOf("--");
                sql.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
                int end;
                while ((end = sql.indexOf(";")) >= 0) {
                    String command = sql.substring(0, end).trim();
                    if (!command.isEmpty()) statement.execute(command);
                    sql.delete(0, end + 1);
                }
            }
        }
    }

    private static void cleanup(String url, String user, String password, String firstId,
                                String secondId, String firstEmail, String secondEmail) throws Exception {
        try (var connection = DriverManager.getConnection(url, user, password);
             var deleteNotes = connection.prepareStatement("DELETE FROM notes WHERE note_id IN (?, ?)");
             var deleteTags = connection.prepareStatement("DELETE FROM tags WHERE user_id IN "
                     + "(SELECT user_id FROM users WHERE email IN (?, ?))");
             var deleteUsers = connection.prepareStatement("DELETE FROM users WHERE email IN (?, ?)")) {
            deleteNotes.setString(1, firstId);
            deleteNotes.setString(2, secondId);
            deleteNotes.executeUpdate();
            deleteTags.setString(1, firstEmail);
            deleteTags.setString(2, secondEmail);
            deleteTags.executeUpdate();
            deleteUsers.setString(1, firstEmail);
            deleteUsers.setString(2, secondEmail);
            deleteUsers.executeUpdate();
        }
    }
}
