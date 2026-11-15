package com.conote.server.network;

import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.dto.share.ShareDtos;
import com.conote.common.enums.NoteType;
import com.conote.common.enums.SharePermission;
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

/** Two-account VIEW to EDIT to revoke acceptance path on a dedicated DB. */
class Stage5ShareIntegrationTest {
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final RequestDispatcher dispatcher = new RequestDispatcher();

    @Test
    void sharePermissionChangesAndRevocationAreEnforcedByServer() throws Exception {
        String url = System.getenv("CONOTE_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "No dedicated MariaDB test URL configured");
        assertTrue(url.matches("(?i)^jdbc:mariadb://[^/]+/conote_stage2_test(?:\\?.*)?$"),
                "Refusing to run integration test outside conote_stage2_test");
        String user = System.getenv("CONOTE_DB_USER");
        String password = System.getenv("CONOTE_DB_PASSWORD");
        assertTrue(user != null && password != null, "DB test credentials are required");
        applySchema(url, user, password);

        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "owner+" + suffix + "@example.test";
        String recipientEmail = "recipient+" + suffix + "@example.test";
        String outsiderEmail = "outsider+" + suffix + "@example.test";
        String noteId = UUID.randomUUID().toString();
        try {
            for (String email : List.of(ownerEmail, recipientEmail, outsiderEmail)) {
                success(send(Operation.AUTH_REGISTER, null,
                        new RegisterRequest(email, "password123", email)));
            }
            String owner = token(ownerEmail);
            String recipient = token(recipientEmail);
            String outsider = token(outsiderEmail);
            success(send(Operation.NOTE_CREATE, owner,
                    new NoteDtos.Create(noteId, NoteType.TEXT, "Chia sẻ", "Bản đầu", "amber", List.of())));

            assertEquals(ErrorCode.VALIDATION, send(Operation.SHARE_CREATE, owner,
                    new ShareDtos.Create(noteId, ownerEmail, SharePermission.VIEW)).code());
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.SHARE_CREATE, recipient,
                    new ShareDtos.Create(noteId, outsiderEmail, SharePermission.VIEW)).code());
            long shareId = data(send(Operation.SHARE_CREATE, owner,
                    new ShareDtos.Create(noteId, recipientEmail, SharePermission.VIEW)))
                    .get("shareId").getAsLong();
            assertEquals(ErrorCode.CONFLICT, send(Operation.SHARE_CREATE, owner,
                    new ShareDtos.Create(noteId, recipientEmail, SharePermission.EDIT)).code());
            assertEquals(1, data(send(Operation.SHARE_LIST, owner,
                    new ShareDtos.NoteId(noteId))).getAsJsonArray("shares").size());
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.SHARE_LIST, recipient,
                    new ShareDtos.NoteId(noteId)).code());

            assertEquals("VIEW", data(send(Operation.NOTE_GET, recipient,
                    new NoteDtos.Id(noteId))).get("permission").getAsString());
            assertEquals(ErrorCode.FORBIDDEN, send(Operation.NOTE_UPDATE, recipient,
                    new NoteDtos.Update(noteId, 1, "Không được", "Sai", "blue")).code());
            assertEquals(ErrorCode.FORBIDDEN, send(Operation.NOTE_DELETE, recipient,
                    new NoteDtos.Delete(noteId, 1)).code());
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.NOTE_GET, outsider,
                    new NoteDtos.Id(noteId)).code());
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.SHARE_UPDATE, recipient,
                    new ShareDtos.Update(shareId, SharePermission.EDIT)).code());

            data(send(Operation.NOTE_STATE_SET, recipient,
                    new NoteDtos.StateSet(noteId, true, true)));
            assertFalse(data(send(Operation.NOTE_GET, owner, new NoteDtos.Id(noteId)))
                    .get("pinned").getAsBoolean());
            assertTrue(data(send(Operation.NOTE_GET, recipient, new NoteDtos.Id(noteId)))
                    .get("favorite").getAsBoolean());

            success(send(Operation.SHARE_UPDATE, owner,
                    new ShareDtos.Update(shareId, SharePermission.EDIT)));
            assertEquals("EDIT", data(send(Operation.NOTE_GET, recipient,
                    new NoteDtos.Id(noteId))).get("permission").getAsString());
            assertEquals(2, data(send(Operation.NOTE_UPDATE, recipient,
                    new NoteDtos.Update(noteId, 1, "Đã sửa", "Nội dung từ B", "blue")))
                    .get("version").getAsLong());
            assertEquals("Nội dung từ B", data(send(Operation.NOTE_GET, owner,
                    new NoteDtos.Id(noteId))).get("content").getAsString());

            success(send(Operation.SHARE_REVOKE, owner, new ShareDtos.Revoke(shareId)));
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.NOTE_GET, recipient,
                    new NoteDtos.Id(noteId)).code());
            assertEquals(0, data(send(Operation.NOTE_LIST, recipient, null))
                    .getAsJsonArray("notes").size());
            assertEquals(ErrorCode.NOT_FOUND, send(Operation.NOTE_UPDATE, recipient,
                    new NoteDtos.Update(noteId, 2, "Mất quyền", "Sai", "red")).code());
            assertEquals(0, data(send(Operation.SHARE_LIST, owner,
                    new ShareDtos.NoteId(noteId))).getAsJsonArray("shares").size());

            success(send(Operation.SHARE_CREATE, owner,
                    new ShareDtos.Create(noteId, recipientEmail, SharePermission.VIEW)));
            assertFalse(data(send(Operation.NOTE_GET, recipient,
                    new NoteDtos.Id(noteId))).get("favorite").getAsBoolean());
        } finally {
            cleanup(url, user, password, noteId, ownerEmail, recipientEmail, outsiderEmail);
        }
    }

    private String token(String email) {
        return data(send(Operation.AUTH_LOGIN, null,
                new LoginRequest(email, "password123"))).get("token").getAsString();
    }

    private ProtocolResponse send(Operation operation, String token, Object payload) {
        return dispatcher.dispatch(new ProtocolRequest(UUID.randomUUID().toString(), operation, token,
                payload == null ? null : gson.toJsonTree(payload)));
    }

    private ProtocolResponse success(ProtocolResponse response) {
        assertTrue(response.success(), () -> response.code() + ": " + response.message());
        return response;
    }

    private com.google.gson.JsonObject data(ProtocolResponse response) {
        return success(response).data().getAsJsonObject();
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

    private static void cleanup(String url, String user, String password, String noteId,
                                String... emails) throws Exception {
        try (var connection = DriverManager.getConnection(url, user, password);
             var deleteNote = connection.prepareStatement("DELETE FROM notes WHERE note_id = ?");
             var deleteUser = connection.prepareStatement("DELETE FROM users WHERE email = ?")) {
            deleteNote.setString(1, noteId);
            deleteNote.executeUpdate();
            for (String email : emails) {
                deleteUser.setString(1, email);
                deleteUser.executeUpdate();
            }
        }
    }
}
