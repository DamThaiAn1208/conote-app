package com.conote.client.network;

import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolResponse;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javafx.application.Platform;

/** Delivers asynchronous socket outcomes to JavaFX controllers on the UI thread. */
public final class FxSocketGateway {
  private final SocketClient client;

  public FxSocketGateway(SocketClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  public void send(Operation operation, String token, Object payload,
                   Consumer<ProtocolResponse> onSuccess, Consumer<Throwable> onFailure) {
    Objects.requireNonNull(onSuccess, "onSuccess");
    Objects.requireNonNull(onFailure, "onFailure");
    client.send(operation, token, payload).whenComplete((response, failure) ->
        Platform.runLater(() -> {
          if (failure == null) {
            onSuccess.accept(response);
          } else {
            onFailure.accept(failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure);
          }
        }));
  }
}
