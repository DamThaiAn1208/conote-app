package com.conote.common.dto.protocol;

import com.google.gson.JsonElement;

/** One JSON document inside a length-prefixed TCP frame. */
public record ProtocolRequest(
        String requestId,
        Operation operation,
        String token,
        JsonElement payload
) { }
