package com.conote.client.network;

import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.protocol.FrameCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Asynchronous TCP gateway. Each request has a dedicated connection and exactly one response. */
public final class SocketClient implements AutoCloseable {
  private static final int DEFAULT_PORT = 8080;
  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);
  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  private final String host;
  private final int port;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final ExecutorService executor;
  private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean closed = new AtomicBoolean();

  public SocketClient(String host, int port) {
    this(host, port, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
  }

  public SocketClient(String host, int port, Duration connectTimeout, Duration readTimeout) {
    this.host = Objects.requireNonNull(host, "host").trim();
    if (this.host.isEmpty() || port < 1 || port > 65535) {
      throw new IllegalArgumentException("Invalid server host or port");
    }
    this.port = port;
    this.connectTimeoutMillis = timeoutMillis(connectTimeout);
    this.readTimeoutMillis = timeoutMillis(readTimeout);
    this.executor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(64), task -> {
      Thread thread = new Thread(task, "conote-socket-client");
      thread.setDaemon(true);
      return thread;
    });
  }

  public static SocketClient fromConfiguration() {
    String host = setting("conote.server.host", "CONOTE_SERVER_HOST", "127.0.0.1");
    String configuredPort = setting("conote.server.port", "CONOTE_SERVER_PORT", String.valueOf(DEFAULT_PORT));
    try {
      return new SocketClient(host, Integer.parseInt(configuredPort));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid conote.server.port", exception);
    }
  }

  /** CompletableFuture completion always occurs off the JavaFX Application Thread. */
  public CompletableFuture<ProtocolResponse> send(Operation operation, String token, Object payload) {
    Objects.requireNonNull(operation, "operation");
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IllegalStateException("Socket client is closed"));
    }
    String requestId = UUID.randomUUID().toString();
    try {
      return CompletableFuture.supplyAsync(() -> exchange(new ProtocolRequest(
          requestId, operation, token, GSON.toJsonTree(payload))), executor);
    } catch (RejectedExecutionException exception) {
      return CompletableFuture.failedFuture(new IllegalStateException("Socket client is closed", exception));
    }
  }

  private ProtocolResponse exchange(ProtocolRequest request) {
    Socket socket = new Socket();
    openSockets.add(socket);
    try (socket) {
      socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
      socket.setSoTimeout(readTimeoutMillis);
      FrameCodec.write(socket.getOutputStream(), GSON.toJson(request));
      String responseJson = FrameCodec.read(socket.getInputStream());
      ProtocolResponse response;
      try {
        response = GSON.fromJson(responseJson, ProtocolResponse.class);
      } catch (JsonParseException exception) {
        throw new TransportException(TransportException.Reason.PROTOCOL,
            "Server returned invalid JSON", exception);
      }
      if (response == null || !request.requestId().equals(response.requestId())
          || (response.success() && response.code() != null)
          || (!response.success() && response.code() == null)) {
        throw new TransportException(TransportException.Reason.PROTOCOL,
            "Server returned an invalid response envelope", null);
      }
      if (!response.success()) {
        throw new RemoteOperationException(response.code(), response.message(), response.data());
      }
      return response;
    } catch (FrameCodec.InvalidFrameException exception) {
      throw new TransportException(TransportException.Reason.PROTOCOL,
          "Server returned an invalid frame", exception);
    } catch (SocketTimeoutException exception) {
      throw new TransportException(TransportException.Reason.TIMEOUT,
          "Timed out waiting for CoNote server", exception);
    } catch (IOException exception) {
      TransportException.Reason reason = socket.isConnected()
          ? TransportException.Reason.IO : TransportException.Reason.CONNECTION;
      throw new TransportException(reason, "CoNote server is unavailable", exception);
    } finally {
      openSockets.remove(socket);
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    for (Socket socket : openSockets) {
      try {
        socket.close();
      } catch (IOException ignored) {
        // A request already in flight will complete as a transport failure.
      }
    }
    executor.shutdownNow();
  }

  private static int timeoutMillis(Duration duration) {
    Objects.requireNonNull(duration, "timeout");
    long millis = duration.toMillis();
    if (millis < 1 || millis > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Timeout must be between 1 ms and Integer.MAX_VALUE ms");
    }
    return (int) millis;
  }

  private static String setting(String property, String environment, String fallback) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      value = System.getenv(environment);
    }
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
