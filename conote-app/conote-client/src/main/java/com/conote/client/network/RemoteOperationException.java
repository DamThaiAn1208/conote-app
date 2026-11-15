package com.conote.client.network;

import com.conote.common.dto.protocol.ErrorCode;
import com.google.gson.JsonElement;

/** The server replied with a business or authorization error. */
public final class RemoteOperationException extends RuntimeException {
  private final ErrorCode code;
  private final JsonElement data;

  public RemoteOperationException(ErrorCode code, String message, JsonElement data) {
    super(message);
    this.code = code;
    this.data = data;
  }

  public ErrorCode code() {
    return code;
  }

  public JsonElement data() {
    return data;
  }
}
