package com.conote.server.network;

import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.model.User;
import com.conote.common.protocol.FrameCodec;
import com.conote.server.dao.UserDao;
import com.conote.server.service.AuthService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocketServerBootstrapTest {
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private SocketServerBootstrap server;

    @BeforeEach
    void start() throws Exception {
        server = new SocketServerBootstrap("127.0.0.1", 0, new RequestDispatcher());
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void fragmentedFrameAndSequentialRequestsReceiveMatchingResponses() throws Exception {
        try (Socket client = connect()) {
            String firstId = UUID.randomUUID().toString();
            String json = gson.toJson(new ProtocolRequest(firstId, Operation.PING, null, null));
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            DataOutputStream output = new DataOutputStream(client.getOutputStream());
            output.writeInt(bytes.length);
            for (byte value : bytes) {
                output.write(value);
            }
            output.flush();

            ProtocolResponse first = gson.fromJson(FrameCodec.read(client.getInputStream()), ProtocolResponse.class);
            assertTrue(first.success());
            assertEquals(firstId, first.requestId());
            assertNotNull(first.data().getAsJsonObject().get("serverTime"));

            String secondId = UUID.randomUUID().toString();
            FrameCodec.write(client.getOutputStream(), gson.toJson(
                    new ProtocolRequest(secondId, Operation.ECHO, null,
                            JsonParser.parseString("{\"text\":\"xin chào\"}"))));
            ProtocolResponse second = gson.fromJson(FrameCodec.read(client.getInputStream()), ProtocolResponse.class);
            assertEquals(secondId, second.requestId());
            assertEquals("xin chào", second.data().getAsJsonObject().get("text").getAsString());
        }
    }

    @Test
    void malformedFrameDoesNotStopOtherClients() throws Exception {
        try (Socket client = connect()) {
            DataOutputStream output = new DataOutputStream(client.getOutputStream());
            output.writeInt(FrameCodec.MAX_FRAME_BYTES + 1);
            output.flush();
            ProtocolResponse response = gson.fromJson(FrameCodec.read(client.getInputStream()), ProtocolResponse.class);
            assertFalse(response.success());
            assertEquals(ErrorCode.VALIDATION, response.code());
        }
        assertTrue(ping().success());
    }

    @Test
    void concurrentClientsEachReceiveTheirOwnResponse() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<ProtocolResponse>> futures = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                futures.add(pool.submit(this::ping));
            }
            for (var future : futures) {
                assertTrue(future.get(5, TimeUnit.SECONDS).success());
            }
        }
    }

    @Test
    void invalidJsonGetsValidationAndConnectionCanContinue() throws Exception {
        try (Socket client = connect()) {
            FrameCodec.write(client.getOutputStream(), "{broken");
            ProtocolResponse error = gson.fromJson(FrameCodec.read(client.getInputStream()), ProtocolResponse.class);
            assertEquals(ErrorCode.VALIDATION, error.code());

            String id = UUID.randomUUID().toString();
            FrameCodec.write(client.getOutputStream(), gson.toJson(new ProtocolRequest(id, Operation.PING, null, null)));
            ProtocolResponse next = gson.fromJson(FrameCodec.read(client.getInputStream()), ProtocolResponse.class);
            assertTrue(next.success());
            assertEquals(id, next.requestId());
        }
    }

    @Test
    void authFlowWorksOverTcpAndLogoutRevokesToken() throws Exception {
        server.close();
        server = new SocketServerBootstrap("127.0.0.1", 0,
                new RequestDispatcher(new AuthService(new MemoryUserDao())));
        server.start();

        try (Socket client = connect()) {
            ProtocolResponse registered = exchange(client, Operation.AUTH_REGISTER, null,
                    new RegisterRequest("an@example.com", "secret123", "An"));
            assertTrue(registered.success());
            assertEquals("an@example.com", registered.data().getAsJsonObject().get("email").getAsString());
            assertFalse(registered.data().getAsJsonObject().has("passwordHash"));

            ProtocolResponse loggedIn = exchange(client, Operation.AUTH_LOGIN, null,
                    new LoginRequest("an@example.com", "secret123"));
            assertTrue(loggedIn.success());
            String token = loggedIn.data().getAsJsonObject().get("token").getAsString();
            assertNotNull(loggedIn.data().getAsJsonObject().get("expiresAt"));

            assertTrue(exchange(client, Operation.AUTH_ME, token, null).success());
            assertTrue(exchange(client, Operation.AUTH_LOGOUT, token, null).success());
            assertEquals(ErrorCode.UNAUTHENTICATED,
                    exchange(client, Operation.AUTH_ME, token, null).code());
        }
    }

    private ProtocolResponse exchange(Socket client, Operation operation, String token, Object payload)
            throws Exception {
        String id = UUID.randomUUID().toString();
        FrameCodec.write(client.getOutputStream(), gson.toJson(new ProtocolRequest(
                id, operation, token, payload == null ? null : gson.toJsonTree(payload))));
        ProtocolResponse response = gson.fromJson(FrameCodec.read(client.getInputStream()), ProtocolResponse.class);
        assertEquals(id, response.requestId());
        return response;
    }

    private static final class MemoryUserDao extends UserDao {
        private final Map<String, User> byEmail = new HashMap<>();

        @Override
        public User save(User user) {
            user.setUserId((long) byEmail.size() + 1);
            byEmail.put(user.getEmail(), user);
            return user;
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public Optional<User> findById(Long userId) {
            return byEmail.values().stream()
                    .filter(user -> userId.equals(user.getUserId()))
                    .findFirst();
        }

        @Override
        public boolean existsByEmail(String email) {
            return byEmail.containsKey(email);
        }
    }

    private ProtocolResponse ping() throws Exception {
        try (Socket client = connect()) {
            String id = UUID.randomUUID().toString();
            FrameCodec.write(client.getOutputStream(), gson.toJson(new ProtocolRequest(id, Operation.PING, null, null)));
            ProtocolResponse response = gson.fromJson(FrameCodec.read(client.getInputStream()), ProtocolResponse.class);
            assertEquals(id, response.requestId());
            return response;
        }
    }

    private Socket connect() throws Exception {
        Socket socket = new Socket("127.0.0.1", server.boundPort());
        socket.setSoTimeout(5_000);
        return socket;
    }
}
