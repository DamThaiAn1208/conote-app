package com.conote.server.network;

import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.protocol.FrameCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded TCP server; each connection processes request/response pairs in order. */
public final class SocketServerBootstrap implements AutoCloseable {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8080;
    public static final int READ_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_CLIENTS = 16;
    private static final int QUEUED_CLIENTS = 64;

    private final String host;
    private final int port;
    private final RequestDispatcher dispatcher;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ThreadPoolExecutor workers;

    public SocketServerBootstrap() {
        this(configuredHost(), configuredPort(), new RequestDispatcher());
    }

    public SocketServerBootstrap(String host, int port, RequestDispatcher dispatcher) {
        if (host == null || host.isBlank() || port < 0 || port > 65_535 || dispatcher == null) {
            throw new IllegalArgumentException("Invalid server configuration");
        }
        this.host = host;
        this.port = port;
        this.dispatcher = dispatcher;
    }

    public synchronized void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }
        ServerSocket listening = new ServerSocket();
        listening.bind(new InetSocketAddress(host, port));
        serverSocket = listening;
        workers = new ThreadPoolExecutor(
                MAX_CLIENTS, MAX_CLIENTS, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUED_CLIENTS),
                task -> {
                    Thread worker = new Thread(task, "conote-client-worker");
                    worker.setDaemon(false);
                    return worker;
                },
                new ThreadPoolExecutor.AbortPolicy());
        running = true;
        acceptThread = new Thread(this::acceptLoop, "conote-accept");
        acceptThread.setDaemon(false);
        acceptThread.start();
        System.out.println("CoNote TCP server listening on " + host + ":" + boundPort());
    }

    public synchronized int boundPort() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(READ_TIMEOUT_MILLIS);
                client.setTcpNoDelay(true);
                clients.add(client);
                try {
                    workers.execute(() -> handle(client));
                } catch (RejectedExecutionException exception) {
                    clients.remove(client);
                    client.close();
                }
            } catch (SocketException exception) {
                if (running) {
                    System.err.println("TCP accept failed: " + exception.getMessage());
                }
            } catch (IOException exception) {
                if (running) {
                    System.err.println("TCP accept failed: " + exception.getMessage());
                }
            }
        }
    }

    private void handle(Socket client) {
        try (client) {
            while (running && !client.isClosed()) {
                String requestJson;
                try {
                    requestJson = FrameCodec.read(client.getInputStream());
                } catch (EOFException | SocketTimeoutException exception) {
                    return;
                } catch (FrameCodec.InvalidFrameException exception) {
                    sendError(client, ErrorCode.VALIDATION, exception.getMessage());
                    return;
                }
                FrameCodec.write(client.getOutputStream(), dispatcher.dispatch(requestJson));
            }
        } catch (IOException exception) {
            // Disconnects and lost responses are resolved by the client with a fresh read.
        } finally {
            clients.remove(client);
        }
    }

    private void sendError(Socket client, ErrorCode code, String message) {
        try {
            ProtocolResponse response = new ProtocolResponse(null, false, code, message, null);
            FrameCodec.write(client.getOutputStream(), gson.toJson(response));
        } catch (IOException ignored) {
            // The peer may have disconnected while sending the malformed frame.
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) { }
        for (Socket client : clients) {
            try {
                client.close();
            } catch (IOException ignored) { }
        }
        workers.shutdownNow();
        try {
            workers.awaitTermination(5, TimeUnit.SECONDS);
            acceptThread.join(5_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }

    private static String configuredHost() {
        String property = System.getProperty("conote.server.host");
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv("CONOTE_SERVER_HOST");
        return environment == null || environment.isBlank() ? DEFAULT_HOST : environment;
    }

    private static int configuredPort() {
        String value = System.getProperty("conote.server.port");
        if (value == null || value.isBlank()) {
            value = System.getenv("CONOTE_SERVER_PORT");
        }
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 65_535) {
                throw new IllegalArgumentException("Server port out of range");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid server port", exception);
        }
    }
}
