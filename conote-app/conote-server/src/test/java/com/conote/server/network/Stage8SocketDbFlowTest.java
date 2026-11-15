package com.conote.server.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.dto.share.ShareDtos;
import com.conote.common.dto.tag.TagDtos;
import com.conote.common.enums.NoteType;
import com.conote.common.enums.SharePermission;
import com.conote.common.protocol.FrameCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Real two-socket protocol path through server handlers and MariaDB. */
class Stage8SocketDbFlowTest {
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    @Test
    void twoClientsRefreshAfterShareRevocationAndSeeVersionConflicts() throws Exception {
        String url = System.getenv("CONOTE_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "No dedicated MariaDB test URL configured");
        assertTrue(url.matches("(?i)^jdbc:mariadb://[^/]+/conote_stage2_test(?:\\?.*)?$"),
                "Refusing to run integration test outside conote_stage2_test");
        String user = System.getenv("CONOTE_DB_USER");
        String password = System.getenv("CONOTE_DB_PASSWORD");
        assertTrue(user != null && password != null, "DB test credentials are required");
        applySchema(url, user, password);
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "socket-owner+" + suffix + "@example.test";
        String recipientEmail = "socket-recipient+" + suffix + "@example.test";
        String noteId = UUID.randomUUID().toString();
        String lostResponseNoteId = UUID.randomUUID().toString();
        try (SocketServerBootstrap server = new SocketServerBootstrap("127.0.0.1", 0,
                new RequestDispatcher())) {
            server.start();
            try (Socket ownerSocket = new Socket("127.0.0.1", server.boundPort());
                 Socket recipientSocket = new Socket("127.0.0.1", server.boundPort())) {
                ownerSocket.setSoTimeout(5_000);
                recipientSocket.setSoTimeout(5_000);
                success(send(ownerSocket, Operation.AUTH_REGISTER, null,
                        new RegisterRequest(ownerEmail, "password123", "Owner")));
                success(send(recipientSocket, Operation.AUTH_REGISTER, null,
                        new RegisterRequest(recipientEmail, "password123", "Recipient")));
                String owner = data(send(ownerSocket, Operation.AUTH_LOGIN, null,
                        new LoginRequest(ownerEmail, "password123"))).get("token").getAsString();
                String recipient = data(send(recipientSocket, Operation.AUTH_LOGIN, null,
                        new LoginRequest(recipientEmail, "password123"))).get("token").getAsString();
                success(send(ownerSocket, Operation.NOTE_CREATE, owner,
                        new NoteDtos.Create(noteId, NoteType.TEXT, "Bản chung", "Nội dung 1", "default", List.of())));
                assertEquals(0, data(send(ownerSocket, Operation.NOTE_LIST, owner,
                        new NoteDtos.ListQuery("%", List.of(), NoteDtos.SortBy.UPDATED_AT, true)))
                        .getAsJsonArray("notes").size());
                assertEquals(0, data(send(ownerSocket, Operation.NOTE_LIST, owner,
                        new NoteDtos.ListQuery("_", List.of(), NoteDtos.SortBy.UPDATED_AT, true)))
                        .getAsJsonArray("notes").size());
                long shareId = data(send(ownerSocket, Operation.SHARE_CREATE, owner,
                        new ShareDtos.Create(noteId, recipientEmail, SharePermission.EDIT)))
                        .get("shareId").getAsLong();
                assertEquals(1, data(send(recipientSocket, Operation.SYNC_SNAPSHOT, recipient, null))
                        .getAsJsonArray("notes").size());
                success(send(ownerSocket, Operation.NOTE_UPDATE, owner,
                        new NoteDtos.Update(noteId, 1, "Bản chung", "Nội dung 2", "blue")));
                ProtocolResponse stale = send(recipientSocket, Operation.NOTE_UPDATE, recipient,
                        new NoteDtos.Update(noteId, 1, "Ghi đè", "Nội dung cũ", "red"));
                assertEquals(ErrorCode.CONFLICT, stale.code());
                assertEquals(2, stale.data().getAsJsonObject().get("version").getAsLong());
                long tagId = data(send(ownerSocket, Operation.TAG_CREATE, owner,
                        new TagDtos.Create("Review"))).get("tagId").getAsLong();
                assertEquals(3, data(send(ownerSocket, Operation.TAG_ATTACH, owner,
                        new TagDtos.Link(noteId, tagId, 2))).get("version").getAsLong());
                assertEquals(ErrorCode.CONFLICT, send(ownerSocket, Operation.TAG_ATTACH, owner,
                        new TagDtos.Link(noteId, tagId, 2)).code());
                assertEquals(4, data(send(ownerSocket, Operation.TAG_DETACH, owner,
                        new TagDtos.Link(noteId, tagId, 3))).get("version").getAsLong());
                assertEquals(ErrorCode.CONFLICT, send(ownerSocket, Operation.TAG_DETACH, owner,
                        new TagDtos.Link(noteId, tagId, 3)).code());
                success(send(ownerSocket, Operation.SHARE_REVOKE, owner, new ShareDtos.Revoke(shareId)));
                assertEquals(0, data(send(recipientSocket, Operation.SYNC_SNAPSHOT, recipient, null))
                        .getAsJsonArray("notes").size());
                assertEquals(ErrorCode.NOT_FOUND, send(recipientSocket, Operation.NOTE_GET, recipient,
                        new NoteDtos.Id(noteId)).code());
                assertEquals(ErrorCode.UNAUTHENTICATED, send(recipientSocket, Operation.NOTE_LIST,
                        "invalid-token", null).code());

                // The client closes immediately after sending, before it can read the ACK.
                try (Socket droppedResponse = new Socket("127.0.0.1", server.boundPort())) {
                    FrameCodec.write(droppedResponse.getOutputStream(), gson.toJson(new ProtocolRequest(
                            UUID.randomUUID().toString(), Operation.NOTE_CREATE, owner,
                            gson.toJsonTree(new NoteDtos.Create(lostResponseNoteId, NoteType.TEXT,
                                    "Mất ACK", "Vẫn được lưu", "default", List.of())))));
                    droppedResponse.shutdownOutput();
                }
                ProtocolResponse resolved = null;
                for (int attempt = 0; attempt < 40; attempt++) {
                    resolved = send(ownerSocket, Operation.NOTE_GET, owner,
                            new NoteDtos.Id(lostResponseNoteId));
                    if (resolved.success()) break;
                    Thread.sleep(50);
                }
                assertEquals("Vẫn được lưu", data(resolved).get("content").getAsString());
                assertEquals(ErrorCode.CONFLICT, send(ownerSocket, Operation.NOTE_CREATE, owner,
                        new NoteDtos.Create(lostResponseNoteId, NoteType.TEXT,
                                "Mất ACK", "Vẫn được lưu", "default", List.of())).code());
            }
        } finally {
            cleanup(url, user, password, noteId, lostResponseNoteId, ownerEmail, recipientEmail);
        }
    }

    private ProtocolResponse send(Socket socket, Operation operation, String token, Object payload) throws Exception {
        String id = UUID.randomUUID().toString();
        FrameCodec.write(socket.getOutputStream(), gson.toJson(new ProtocolRequest(id, operation, token,
                payload == null ? null : gson.toJsonTree(payload))));
        ProtocolResponse response = gson.fromJson(FrameCodec.read(socket.getInputStream()), ProtocolResponse.class);
        assertEquals(id, response.requestId());
        return response;
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
                                String lostResponseNoteId, String... emails) throws Exception {
        try (var connection = DriverManager.getConnection(url, user, password);
             var deleteNote = connection.prepareStatement("DELETE FROM notes WHERE note_id IN (?, ?)");
             var deleteTags = connection.prepareStatement("DELETE FROM tags WHERE user_id IN "
                     + "(SELECT user_id FROM users WHERE email IN (?, ?))");
             var deleteUser = connection.prepareStatement("DELETE FROM users WHERE email = ?")) {
            deleteNote.setString(1, noteId);
            deleteNote.setString(2, lostResponseNoteId);
            deleteNote.executeUpdate();
            deleteTags.setString(1, emails[0]);
            deleteTags.setString(2, emails[1]);
            deleteTags.executeUpdate();
            for (String email : emails) {
                deleteUser.setString(1, email);
                deleteUser.executeUpdate();
            }
        }
    }
}
