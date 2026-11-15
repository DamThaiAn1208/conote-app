package com.conote.server.network;

import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.model.User;
import com.conote.common.protocol.FrameCodec;
import com.conote.server.dao.UserDao;
import com.conote.server.service.AuthService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mindrot.jbcrypt.BCrypt;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies independent account sessions across two TCP clients. */
class Stage3AuthIsolationTest {
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    @Test
    void twoClientsKeepSeparateIdentitiesAndLogoutRevokesOnlyOneSession() throws Exception {
        MemoryUserDao users = new MemoryUserDao();
        try (SocketServerBootstrap server = new SocketServerBootstrap("127.0.0.1", 0,
                new RequestDispatcher(new AuthService(users)))) {
            server.start();
            try (Socket alice = connect(server); Socket bob = connect(server)) {
                assertTrue(exchange(alice, Operation.AUTH_REGISTER, null,
                        new RegisterRequest("alice@example.test", "password-a", "Alice")).success());
                assertTrue(exchange(bob, Operation.AUTH_REGISTER, null,
                        new RegisterRequest("bob@example.test", "password-b", "Bob")).success());
                String storedHash = users.findByEmail("alice@example.test").orElseThrow().getPasswordHash();
                assertNotEquals("password-a", storedHash);
                assertTrue(BCrypt.checkpw("password-a", storedHash));

                ProtocolResponse aliceLogin = exchange(alice, Operation.AUTH_LOGIN, null,
                        new LoginRequest("alice@example.test", "password-a"));
                ProtocolResponse bobLogin = exchange(bob, Operation.AUTH_LOGIN, null,
                        new LoginRequest("bob@example.test", "password-b"));
                String aliceToken = aliceLogin.data().getAsJsonObject().get("token").getAsString();
                String bobToken = bobLogin.data().getAsJsonObject().get("token").getAsString();
                assertNotEquals(aliceToken, bobToken);

                long aliceId = userId(exchange(alice, Operation.AUTH_ME, aliceToken, null));
                long bobId = userId(exchange(bob, Operation.AUTH_ME, bobToken, null));
                assertNotEquals(aliceId, bobId);
                assertEquals("alice@example.test", exchange(alice, Operation.AUTH_ME, aliceToken, null)
                        .data().getAsJsonObject().get("email").getAsString());
                assertEquals("bob@example.test", exchange(bob, Operation.AUTH_ME, bobToken, null)
                        .data().getAsJsonObject().get("email").getAsString());

                assertEquals(ErrorCode.UNAUTHENTICATED,
                        exchange(alice, Operation.AUTH_ME, "invalid-token", null).code());
                assertEquals(ErrorCode.UNAUTHENTICATED,
                        exchange(bob, Operation.NOTE_LIST, null, null).code());
                assertTrue(exchange(alice, Operation.AUTH_LOGOUT, aliceToken, null).success());
                assertEquals(ErrorCode.UNAUTHENTICATED,
                        exchange(alice, Operation.AUTH_ME, aliceToken, null).code());
                assertEquals(bobId, userId(exchange(bob, Operation.AUTH_ME, bobToken, null)));
                assertFalse(exchange(bob, Operation.AUTH_LOGIN, null,
                        new LoginRequest("alice@example.test", "wrong-password")).success());
            }
        }
    }

    private Socket connect(SocketServerBootstrap server) throws Exception {
        Socket socket = new Socket("127.0.0.1", server.boundPort());
        socket.setSoTimeout(5_000);
        return socket;
    }

    private long userId(ProtocolResponse response) {
        assertTrue(response.success());
        return response.data().getAsJsonObject().get("userId").getAsLong();
    }

    private ProtocolResponse exchange(Socket socket, Operation operation, String token, Object payload)
            throws Exception {
        String requestId = UUID.randomUUID().toString();
        FrameCodec.write(socket.getOutputStream(), gson.toJson(new ProtocolRequest(requestId,
                operation, token, payload == null ? null : gson.toJsonTree(payload))));
        ProtocolResponse response = gson.fromJson(FrameCodec.read(socket.getInputStream()),
                ProtocolResponse.class);
        assertEquals(requestId, response.requestId());
        return response;
    }

    private static final class MemoryUserDao extends UserDao {
        private final Map<String, User> users = new ConcurrentHashMap<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public User save(User user) {
            user.setUserId(nextId.getAndIncrement());
            users.put(user.getEmail(), user);
            return user;
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(users.get(email));
        }

        @Override
        public Optional<User> findById(Long userId) {
            return users.values().stream().filter(user -> userId.equals(user.getUserId())).findFirst();
        }

        @Override
        public boolean existsByEmail(String email) {
            return users.containsKey(email);
        }
    }
}
