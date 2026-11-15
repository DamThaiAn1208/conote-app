package com.conote.client.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.network.NoteClient;
import com.conote.client.network.SocketClient;
import com.conote.common.dto.auth.AuthDtos;
import com.conote.common.dto.note.NoteDtos;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.dto.protocol.ProtocolResponse;
import com.conote.common.dto.sync.SyncDtos;
import com.conote.common.enums.NoteType;
import com.conote.common.protocol.FrameCodec;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

@ExtendWith(ApplicationExtension.class)
class ServerBackedCoNoteStoreTest {
  private static final Gson GSON = new Gson();
  private static final AuthDtos.UserView USER =
      new AuthDtos.UserView(101, "owner", "owner@example.test", "Owner");
  private Path storage;

  @Start
  void start(Stage stage) { stage.hide(); }

  @BeforeEach
  void setUp() throws Exception {
    storage = Files.createTempDirectory("conote-server-store-");
    System.setProperty("conote.storage.dir", storage.toString());
    ClientStoragePaths.resetForTesting();
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("conote.storage.dir");
    ClientStoragePaths.resetForTesting();
  }

  @Test
  void createAppearsOnlyAfterServerAckAndWritesUserSnapshot() throws Exception {
    CountDownLatch requestArrived = new CountDownLatch(1);
    CountDownLatch allowAck = new CountDownLatch(1);
    try (FakeServer server = new FakeServer(request -> {
      if (request.operation() == Operation.NOTE_GET) return failure(request, ErrorCode.NOT_FOUND);
      if (request.operation() == Operation.NOTE_CREATE) {
        requestArrived.countDown();
        try { allowAck.await(5, TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        NoteDtos.Create create = GSON.fromJson(request.payload(), NoteDtos.Create.class);
        return success(request, view(create.noteId(), create.title()));
      }
      return failure(request, ErrorCode.VALIDATION);
    }); SocketClient socket = new SocketClient("127.0.0.1", server.port())) {
      ServerBackedCoNoteStore store = fx(() -> new ServerBackedCoNoteStore(USER, new NoteClient(socket, "token")));
      fx(() -> { store.createNoteAsync(NoteType.TEXT, ignored -> { }); return null; });
      assertTrue(requestArrived.await(5, TimeUnit.SECONDS));
      assertEquals(0, fx(() -> store.getNotes().size()));
      assertFalse(Files.exists(ClientStoragePaths.userServerSnapshotFile(USER.userId())));
      allowAck.countDown();
      await(() -> fx(() -> store.getNotes().size()) == 1);
      String snapshot = Files.readString(ClientStoragePaths.userServerSnapshotFile(USER.userId()));
      assertTrue(snapshot.contains("Untitled Text Note"));
      assertFalse(snapshot.contains("token"));
      fx(() -> { store.close(); return null; });
    }
  }

  @Test
  void conflictKeepsDraftAndPreventsSilentReload() throws Exception {
    NoteDtos.View original = view(UUID.randomUUID().toString(), "Original");
    AtomicInteger snapshots = new AtomicInteger();
    try (FakeServer server = new FakeServer(request -> {
      if (request.operation() == Operation.SYNC_SNAPSHOT) {
        snapshots.incrementAndGet();
        return success(request, new SyncDtos.Snapshot(List.of(original), List.of(), "2026-11-06T00:00:00Z"));
      }
      if (request.operation() == Operation.NOTE_UPDATE) return failure(request, ErrorCode.CONFLICT);
      return failure(request, ErrorCode.VALIDATION);
    }); SocketClient socket = new SocketClient("127.0.0.1", server.port())) {
      ServerBackedCoNoteStore store = fx(() -> new ServerBackedCoNoteStore(USER, new NoteClient(socket, "token")));
      fx(() -> { store.refreshFromServer(); return null; });
      await(() -> fx(() -> store.getNotes().size()) == 1);
      fx(() -> { store.updateTitle(store.getNotes().getFirst(), "Draft"); return null; });
      await(() -> fx(() -> store.canOverwriteBlockedDraft()));
      fx(() -> { store.refreshFromServer(); return null; });
      assertEquals("Draft", fx(() -> store.getNotes().getFirst().getTitle()));
      assertEquals(1, snapshots.get());
      String snapshot = Files.readString(ClientStoragePaths.userServerSnapshotFile(USER.userId()));
      assertTrue(snapshot.contains("Original"));
      assertFalse(snapshot.contains("Draft"));
      fx(() -> { store.close(); return null; });
    }
  }

  @Test
  void favoriteChangesOnlyAfterServerAckAndSurvivesSnapshot() throws Exception {
    NoteDtos.View original = view(UUID.randomUUID().toString(), "Favorite candidate");
    CountDownLatch stateArrived = new CountDownLatch(1);
    CountDownLatch allowAck = new CountDownLatch(1);
    try (FakeServer server = new FakeServer(request -> {
      if (request.operation() == Operation.SYNC_SNAPSHOT) {
        return success(request, new SyncDtos.Snapshot(List.of(original), List.of(),
            "2026-11-06T00:00:00Z"));
      }
      if (request.operation() == Operation.NOTE_STATE_SET) {
        NoteDtos.StateSet state = GSON.fromJson(request.payload(), NoteDtos.StateSet.class);
        stateArrived.countDown();
        try { allowAck.await(5, TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        return success(request, new NoteDtos.View(original.noteId(), original.ownerId(),
            original.ownerName(), original.noteType(), original.title(), original.content(),
            original.color(), original.version(), state.pinned(), state.favorite(),
            original.sortOrder(), false, null, List.of(), original.createdAt(),
            original.updatedAt()));
      }
      return failure(request, ErrorCode.VALIDATION);
    }); SocketClient socket = new SocketClient("127.0.0.1", server.port())) {
      ServerBackedCoNoteStore store = fx(() -> new ServerBackedCoNoteStore(USER,
          new NoteClient(socket, "token")));
      fx(() -> { store.refreshFromServer(); return null; });
      await(() -> fx(() -> store.getNotes().size()) == 1);
      fx(() -> { store.toggleFavorite(store.getNotes().getFirst()); return null; });
      assertTrue(stateArrived.await(5, TimeUnit.SECONDS));
      assertFalse(fx(() -> store.getNotes().getFirst().isFavorite()));
      allowAck.countDown();
      await(() -> fx(() -> store.getNotes().getFirst().isFavorite()));
      String snapshot = Files.readString(ClientStoragePaths.userServerSnapshotFile(USER.userId()));
      assertTrue(snapshot.contains("\"favorite\" : true"));
      fx(() -> { store.close(); return null; });
    }
  }

  @Test
  void nextAuthorizedSnapshotRemovesRevokedNoteAndLogoutClearsMemory() throws Exception {
    NoteDtos.View shared = new NoteDtos.View(UUID.randomUUID().toString(), 202, "Other",
        NoteType.TEXT, "Shared", "Body", "default", 1, false, false, 0,
        true, com.conote.common.enums.SharePermission.VIEW, List.of(),
        "2026-11-06T00:00:00Z", "2026-11-06T00:00:00Z");
    AtomicInteger snapshots = new AtomicInteger();
    try (FakeServer server = new FakeServer(request -> {
      if (request.operation() == Operation.SYNC_SNAPSHOT) {
        List<NoteDtos.View> notes = snapshots.incrementAndGet() == 1 ? List.of(shared) : List.of();
        return success(request, new SyncDtos.Snapshot(notes, List.of(), "2026-11-06T00:00:00Z"));
      }
      return failure(request, ErrorCode.VALIDATION);
    }); SocketClient socket = new SocketClient("127.0.0.1", server.port())) {
      ServerBackedCoNoteStore store = fx(() -> new ServerBackedCoNoteStore(USER, new NoteClient(socket, "token")));
      fx(() -> { store.refreshFromServer(); return null; });
      await(() -> fx(() -> store.getNotes().size()) == 1);
      fx(() -> { store.refreshFromServer(); return null; });
      await(() -> fx(() -> store.getNotes().isEmpty()));
      String snapshot = Files.readString(ClientStoragePaths.userServerSnapshotFile(USER.userId()));
      assertFalse(snapshot.contains(shared.noteId()));
      fx(() -> { store.close(); return null; });
      assertEquals(0, fx(() -> store.getNotes().size()));
      assertFalse(Files.exists(ClientStoragePaths.userServerSnapshotFile(303)));
    }
  }

  private static NoteDtos.View view(String id, String title) {
    return new NoteDtos.View(id, USER.userId(), USER.fullName(), NoteType.TEXT,
        title, "", "default", 1, false, false, 0, false, null, List.of(),
        "2026-11-06T00:00:00Z", "2026-11-06T00:00:00Z");
  }

  private static ProtocolResponse success(ProtocolRequest request, Object data) {
    return new ProtocolResponse(request.requestId(), true, null, "OK", GSON.toJsonTree(data));
  }

  private static ProtocolResponse failure(ProtocolRequest request, ErrorCode code) {
    return new ProtocolResponse(request.requestId(), false, code, code.name(), JsonNull.INSTANCE);
  }

  private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    Platform.runLater(task);
    return task.get(10, TimeUnit.SECONDS);
  }

  private static void await(java.util.concurrent.Callable<Boolean> condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < deadline) {
      if (condition.call()) return;
      Thread.sleep(25);
    }
    assertTrue(condition.call(), "Condition was not reached");
  }

  private static final class FakeServer implements AutoCloseable {
    private final ServerSocket server;
    private final Thread worker;

    private FakeServer(Function<ProtocolRequest, ProtocolResponse> responder) throws Exception {
      server = new ServerSocket(0);
      worker = new Thread(() -> {
        while (!server.isClosed()) {
          try (Socket socket = server.accept()) {
            ProtocolRequest request = GSON.fromJson(FrameCodec.read(socket.getInputStream()), ProtocolRequest.class);
            FrameCodec.write(socket.getOutputStream(), GSON.toJson(responder.apply(request)));
          } catch (Exception exception) {
            if (!server.isClosed()) throw new IllegalStateException(exception);
          }
        }
      }, "fake-note-server");
      worker.setDaemon(true);
      worker.start();
    }

    private int port() { return server.getLocalPort(); }

    @Override
    public void close() throws Exception {
      server.close();
      worker.join(1000);
    }
  }
}
