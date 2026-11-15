package com.conote.common.dto.protocol;

import com.google.gson.JsonElement;

/** code is null on success; data is null unless the operation defines it. */
public record ProtocolResponse(
        String requestId,
        boolean success,
        ErrorCode code,
        String message,
        JsonElement data
) { }
