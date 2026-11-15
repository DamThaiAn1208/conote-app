package com.conote.client.network;

import com.conote.common.dto.auth.AuthDtos;
import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.dto.protocol.Operation;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Typed auth gateway; session tokens remain in memory. */
public final class AuthClient {
  private static final Gson GSON = new Gson();
  private final SocketClient socketClient;

  public AuthClient(SocketClient socketClient) {
    this.socketClient = Objects.requireNonNull(socketClient, "socketClient");
  }

  public CompletableFuture<AuthDtos.SessionView> login(String email, String password) {
    return socketClient.send(Operation.AUTH_LOGIN, null, new LoginRequest(email, password))
        .thenApply(response -> decode(response.data(), AuthDtos.SessionView.class));
  }

  public CompletableFuture<AuthDtos.UserView> register(String email, String password,
                                                        String fullName) {
    return socketClient.send(Operation.AUTH_REGISTER, null,
            new RegisterRequest(email, password, fullName))
        .thenApply(response -> decode(response.data(), AuthDtos.UserView.class));
  }

  public CompletableFuture<AuthDtos.UserView> currentUser(String token) {
    return socketClient.send(Operation.AUTH_ME, token, null)
        .thenApply(response -> decode(response.data(), AuthDtos.UserView.class));
  }

  public CompletableFuture<Void> logout(String token) {
    return socketClient.send(Operation.AUTH_LOGOUT, token, null).thenApply(response -> null);
  }

  private static <T> T decode(JsonElement data, Class<T> type) {
    if (data == null || data.isJsonNull()) {
      throw new TransportException(TransportException.Reason.PROTOCOL,
          "Server returned an empty authentication response", null);
    }
    try {
      T decoded = GSON.fromJson(data, type);
      if (decoded == null) {
        throw new IllegalStateException("Empty authentication data");
      }
      return decoded;
    } catch (RuntimeException exception) {
      throw new TransportException(TransportException.Reason.PROTOCOL,
          "Server returned invalid authentication data", exception);
    }
  }
}
