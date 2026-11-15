package com.conote.server.network;

import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.share.ShareDtos;
import com.conote.common.dto.tag.TagDtos;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.server.service.AuthService;
import com.conote.server.service.NoteService;
import com.conote.server.service.OperationFailure;
import com.conote.server.service.ShareService;
import com.conote.server.service.SyncService;
import com.conote.server.service.TagService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jakarta.persistence.PersistenceException;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Dispatches one parsed request without knowing anything about sockets. */
public final class RequestDispatcher {
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final AuthService authService;
    private final NoteService noteService = new NoteService();
    private final TagService tagService = new TagService();
    private final ShareService shareService = new ShareService();
    private final SyncService syncService = new SyncService();

    public RequestDispatcher() {
        this(new AuthService());
    }

    public RequestDispatcher(AuthService authService) {
        this.authService = authService;
    }

    public String dispatch(String requestJson) {
        ProtocolRequest request;
        try {
            JsonElement json = JsonParser.parseString(requestJson);
            if (!json.isJsonObject()) {
                return gson.toJson(failure(null, ErrorCode.VALIDATION, "Request must be an object"));
            }
            request = gson.fromJson(json, ProtocolRequest.class);
        } catch (JsonParseException | IllegalStateException exception) {
            return gson.toJson(failure(null, ErrorCode.VALIDATION, "Invalid JSON request"));
        }
        return gson.toJson(dispatch(request));
    }

    public ProtocolResponse dispatch(ProtocolRequest request) {
        if (request == null || !validUuid(request.requestId()) || request.operation() == null) {
            return failure(null, ErrorCode.VALIDATION, "Invalid requestId or operation");
        }
        String id = request.requestId();
        Operation operation = request.operation();
        try {
            return switch (operation) {
                case PING -> success(id, "PONG", Map.of("serverTime", Instant.now().toString()));
                case ECHO -> new ProtocolResponse(id, true, null, "Echo", request.payload());
                case AUTH_REGISTER -> success(id, "Registered",
                        AuthService.toView(authService.register(payload(request, RegisterRequest.class))));
                case AUTH_LOGIN -> success(id, "Logged in",
                        authService.login(payload(request, LoginRequest.class)));
                case AUTH_ME -> success(id, "Current user", authService.currentUser(request.token()));
                case AUTH_LOGOUT -> {
                    authService.logout(request.token());
                    yield success(id, "Logged out", null);
                }
                case NOTE_CREATE -> success(id, "Created", noteService.create(userId(request), payload(request, NoteDtos.Create.class)));
                case NOTE_LIST -> success(id, "Notes", noteService.list(userId(request), optionalPayload(request, NoteDtos.ListQuery.class)));
                case NOTE_GET -> success(id, "Note", noteService.get(userId(request), payload(request, NoteDtos.Id.class)));
                case NOTE_UPDATE -> success(id, "Updated", noteService.update(userId(request), payload(request, NoteDtos.Update.class)));
                case NOTE_DELETE -> {
                    noteService.delete(userId(request), payload(request, NoteDtos.Delete.class));
                    yield success(id, "Deleted", null);
                }
                case NOTE_STATE_SET -> success(id, "State updated", noteService.setState(userId(request), payload(request, NoteDtos.StateSet.class)));
                case NOTE_REORDER -> {
                    noteService.reorder(userId(request), payload(request, NoteDtos.Reorder.class));
                    yield success(id, "Order updated", null);
                }
                case TAG_CREATE -> success(id, "Tag created", tagService.create(userId(request), payload(request, TagDtos.Create.class)));
                case TAG_LIST -> success(id, "Tags", tagService.list(userId(request)));
                case TAG_RENAME -> success(id, "Tag renamed", tagService.rename(userId(request), payload(request, TagDtos.Rename.class)));
                case TAG_DELETE -> {
                    tagService.delete(userId(request), payload(request, TagDtos.Id.class));
                    yield success(id, "Tag deleted", null);
                }
                case TAG_ATTACH -> success(id, "Tag attached", tagService.attach(userId(request), payload(request, TagDtos.Link.class)));
                case TAG_DETACH -> success(id, "Tag detached", tagService.detach(userId(request), payload(request, TagDtos.Link.class)));
                case SHARE_LIST -> success(id, "Shares", shareService.list(userId(request), payload(request, ShareDtos.NoteId.class)));
                case SHARE_CREATE -> success(id, "Shared", shareService.create(userId(request), payload(request, ShareDtos.Create.class)));
                case SHARE_UPDATE -> success(id, "Permission updated", shareService.update(userId(request), payload(request, ShareDtos.Update.class)));
                case SHARE_REVOKE -> {
                    shareService.revoke(userId(request), payload(request, ShareDtos.Revoke.class));
                    yield success(id, "Share revoked", null);
                }
                case SYNC_SNAPSHOT -> success(id, "Snapshot", syncService.snapshot(userId(request)));
                default -> {
                    userId(request);
                    yield failure(id, ErrorCode.UNAVAILABLE, "Operation is not implemented yet");
                }
            };
        } catch (OperationFailure exception) {
            return new ProtocolResponse(id, false, exception.code(), exception.getMessage(),
                    exception.data() == null ? null : gson.toJsonTree(exception.data()));
        } catch (AuthService.DuplicateEmailException exception) {
            return failure(id, ErrorCode.CONFLICT, exception.getMessage());
        } catch (SecurityException exception) {
            return failure(id, ErrorCode.UNAUTHENTICATED, exception.getMessage());
        } catch (IllegalArgumentException | JsonParseException exception) {
            return failure(id, ErrorCode.VALIDATION, exception.getMessage());
        } catch (PersistenceException exception) {
            return failure(id, ErrorCode.UNAVAILABLE, "Database unavailable");
        } catch (ExceptionInInitializerError | NoClassDefFoundError exception) {
            return failure(id, ErrorCode.UNAVAILABLE, "Database unavailable");
        } catch (RuntimeException exception) {
            System.err.println("Request failed: " + exception.getClass().getSimpleName());
            return failure(id, ErrorCode.INTERNAL, "Internal server error");
        }
    }

    private <T> T payload(ProtocolRequest request, Class<T> type) {
        JsonElement json = request.payload();
        if (json == null || !json.isJsonObject()) {
            throw new IllegalArgumentException("Object payload is required");
        }
        return gson.fromJson(json, type);
    }

    private <T> T optionalPayload(ProtocolRequest request, Class<T> type) {
        return request.payload() == null || request.payload().isJsonNull()
                ? null : payload(request, type);
    }

    private long userId(ProtocolRequest request) {
        return authService.currentUser(request.token()).userId();
    }

    private ProtocolResponse success(String id, String message, Object data) {
        return new ProtocolResponse(id, true, null, message,
                data == null ? null : gson.toJsonTree(data));
    }

    private ProtocolResponse failure(String id, ErrorCode code, String message) {
        return new ProtocolResponse(id, false, code, message, null);
    }

    private boolean validUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
