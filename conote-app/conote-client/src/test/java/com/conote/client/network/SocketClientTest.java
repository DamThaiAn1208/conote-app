package com.conote.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.protocol.FrameCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SocketClientTest {
  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  @Test
  void matchesResponseToRequestAndPreservesPayload() throws Exception {
    withServer(request -> new ProtocolResponse(request.requestId(), true, null,
        "ok", request.payload()), client -> {
      ProtocolResponse response = client.send(Operation.ECHO, null, "xin chào").join();
      assertEquals("xin chào", response.data().getAsString());
    });
  }

  @Test
  void distinguishesBusinessErrorFromNetworkFailure() throws Exception {
    withServer(request -> new ProtocolResponse(request.requestId(), false,
        ErrorCode.FORBIDDEN, "Denied", null), client -> {
      CompletionException failure = assertThrows(CompletionException.class,
          () -> client.send(Operation.NOTE_GET, "token", null).join());
      RemoteOperationException remote = assertInstanceOf(RemoteOperationException.class,
          failure.getCause());
      assertEquals(ErrorCode.FORBIDDEN, remote.code());
    });
  }

  @Test
  void rejectsResponseWithWrongRequestId() throws Exception {
    withServer(request -> new ProtocolResponse("other-id", true, null, "ok", null), client -> {
      CompletionException failure = assertThrows(CompletionException.class,
          () -> client.send(Operation.PING, null, null).join());
      TransportException transport = assertInstanceOf(TransportException.class, failure.getCause());
      assertEquals(TransportException.Reason.PROTOCOL, transport.reason());
    });
  }

  @Test
  void reportsReadTimeoutAsUnconfirmedTransportFailure() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      FutureTask<Void> task = new FutureTask<>(() -> {
        try (var socket = server.accept()) {
          FrameCodec.read(socket.getInputStream());
          Thread.sleep(300);
        }
        return null;
      });
      Thread thread = new Thread(task, "socket-client-timeout-server");
      thread.setDaemon(true);
      thread.start();
      try (SocketClient client = new SocketClient("127.0.0.1", server.getLocalPort(),
          Duration.ofSeconds(1), Duration.ofMillis(50))) {
        CompletionException failure = assertThrows(CompletionException.class,
            () -> client.send(Operation.PING, null, null).join());
        TransportException transport = assertInstanceOf(TransportException.class, failure.getCause());
        assertEquals(TransportException.Reason.TIMEOUT, transport.reason());
      }
      task.get(3, TimeUnit.SECONDS);
    }
  }

  private static void withServer(Function<ProtocolRequest, ProtocolResponse> responseFor,
                                 java.util.function.Consumer<SocketClient> assertion) throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      server.setSoTimeout(2_000);
      FutureTask<Void> task = new FutureTask<>(() -> {
        try (var socket = server.accept()) {
          ProtocolRequest request = GSON.fromJson(FrameCodec.read(socket.getInputStream()),
              ProtocolRequest.class);
          FrameCodec.write(socket.getOutputStream(), GSON.toJson(responseFor.apply(request)));
        }
        return null;
      });
      Thread thread = new Thread(task, "socket-client-test-server");
      thread.setDaemon(true);
      thread.start();
      try (SocketClient client = new SocketClient("127.0.0.1", server.getLocalPort(),
          Duration.ofSeconds(1), Duration.ofSeconds(1))) {
        assertion.accept(client);
      }
      task.get(3, TimeUnit.SECONDS);
    }
  }
}
