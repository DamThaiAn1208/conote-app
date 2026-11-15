package com.conote.client.network;

/** Failure to exchange a usable response with the server. The outcome may be unknown. */
public final class TransportException extends RuntimeException {
  public enum Reason { CONNECTION, TIMEOUT, IO, PROTOCOL }

  private final Reason reason;

  public TransportException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
