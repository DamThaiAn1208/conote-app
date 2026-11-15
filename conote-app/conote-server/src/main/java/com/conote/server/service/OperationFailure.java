package com.conote.server.service;

import com.conote.common.dto.protocol.ErrorCode;

/** Expected protocol failure, optionally carrying the current note after a version conflict. */
public final class OperationFailure extends RuntimeException {
    private final ErrorCode code;
    private final Object data;

    public OperationFailure(ErrorCode code, String message) {
        this(code, message, null);
    }

    public OperationFailure(ErrorCode code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public ErrorCode code() { return code; }
    public Object data() { return data; }
}
