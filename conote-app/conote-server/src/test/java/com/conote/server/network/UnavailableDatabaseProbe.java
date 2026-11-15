package com.conote.server.network;

import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.server.security.AuthSessionManager;
import java.util.UUID;

/** Launched in its own JVM so EntityManagerUtil initializes against an unreachable test URL. */
public final class UnavailableDatabaseProbe {
    private UnavailableDatabaseProbe() { }

    public static void main(String[] args) {
        String token = AuthSessionManager.createSession(1L).token();
        var response = new RequestDispatcher().dispatch(new ProtocolRequest(
                UUID.randomUUID().toString(), Operation.NOTE_LIST, token, null));
        if (response.code() != ErrorCode.UNAVAILABLE) {
            System.err.println("Expected UNAVAILABLE, got " + response.code());
            System.exit(1);
        }
        System.out.println("DB_UNAVAILABLE_OK");
    }
}
