package com.conote.client.app;

import com.conote.client.cache.ClientStoragePaths;
import com.conote.client.cache.NoteCacheStore;
import com.conote.client.controller.AuthWindowController;
import com.conote.client.controller.MainWindowController;
import com.conote.client.network.AuthClient;
import com.conote.client.network.NoteClient;
import com.conote.client.network.SocketClient;
import com.conote.client.service.ServerBackedCoNoteStore;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import com.conote.common.dto.auth.AuthDtos;
import com.conote.common.model.Note;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class ClientApplication extends Application {
  private static final Duration SMOKE_TEST_CLOSE_DELAY = Duration.millis(250);
  private SocketClient socketClient;
  private AuthClient authClient;
  private AuthDtos.SessionView currentSession;
  private ServerBackedCoNoteStore currentStore;
  private MainWindowController currentMainController;
  private Stage primaryStage;

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    ClientStoragePaths.init();
    socketClient = SocketClient.fromConfiguration();
    authClient = new AuthClient(socketClient);
    primaryStage = stage;
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setTitle("CoNote");
    installWindowIcon(stage);
    showAuthWindow();
    stage.show();

    if (Boolean.getBoolean("conote.smokeTest")) {
      PauseTransition delay = new PauseTransition(SMOKE_TEST_CLOSE_DELAY);
      delay.setOnFinished(event -> Platform.exit());
      delay.play();
    }
  }

  private void showAuthWindow() {
    LoadedView<AuthWindowController> view = ViewLoader.load("/fxml/auth/AuthWindow.fxml");
    view.controller().setContext(authClient, this::showMainWindow);
    Scene scene = new Scene(view.root(), 440, 510);
    scene.getStylesheets().add(stylesheetUrl());
    primaryStage.setResizable(false);
    primaryStage.setMinWidth(440);
    primaryStage.setMinHeight(510);
    primaryStage.setMaxWidth(440);
    primaryStage.setMaxHeight(510);
    primaryStage.setScene(scene);
    primaryStage.setWidth(440);
    primaryStage.setHeight(510);
  }

  private void showMainWindow(AuthDtos.SessionView session) {
    currentSession = session;
    currentStore = new ServerBackedCoNoteStore(session.user(),
        new NoteClient(socketClient, session.token()));
    LoadedView<MainWindowController> view = ViewLoader.load("/fxml/main/MainWindow.fxml",
        type -> {
          if (type == MainWindowController.class) {
            return new MainWindowController(currentStore);
          }
          try {
            return type.getDeclaredConstructor().newInstance();
          } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create FXML controller: " + type, exception);
          }
        });
    double initialWidth = view.controller().getInitialWindowWidth();
    double initialHeight = view.controller().getInitialWindowHeight();

    Scene scene = new Scene(view.root(), initialWidth, initialHeight);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(stylesheetUrl());

    primaryStage.setWidth(initialWidth);
    primaryStage.setHeight(initialHeight);
    primaryStage.setMinWidth(initialWidth);
    primaryStage.setMaxWidth(initialWidth);
    primaryStage.setMinHeight(initialHeight);
    primaryStage.setMaxHeight(initialHeight);
    primaryStage.setResizable(false);
    primaryStage.setScene(scene);
    view.controller().bindPrimaryStage(primaryStage);
    view.controller().setOnLogout(this::logout);
    currentMainController = view.controller();
    currentStore.refreshFromServer(() -> offerLegacyImport(session.user().userId()));
  }

  private void offerLegacyImport(long userId) {
    Path marker = ClientStoragePaths.appDir().resolve("users")
        .resolve(Long.toString(userId)).resolve("legacy-import.done");
    if (Files.exists(marker)) return;
    CompletableFuture.supplyAsync(() -> new NoteCacheStore().findAll().stream()
        .filter(note -> !Boolean.TRUE.equals(note.getDeleted())).toList())
        .whenComplete((legacy, failure) -> Platform.runLater(() -> {
          if (currentSession == null || currentSession.user().userId() != userId) return;
          if (failure != null || legacy == null || legacy.isEmpty()) return;
          Alert choice = new Alert(Alert.AlertType.CONFIRMATION,
              "Tìm thấy " + legacy.size() + " ghi chú cục bộ cũ. Nhập chúng vào tài khoản này? "
                  + "Dữ liệu cũ vẫn được giữ nguyên trên máy.", ButtonType.YES, ButtonType.NO);
          choice.initOwner(primaryStage);
          if (choice.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
          ServerBackedCoNoteStore selectedStore = currentStore;
          CompletableFuture.supplyAsync(() -> {
            try { return backupLegacyFiles(); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
          }).whenComplete((backup, backupFailure) -> Platform.runLater(() -> {
            if (currentStore != selectedStore) return;
            if (backupFailure != null) {
              Alert error = new Alert(Alert.AlertType.ERROR,
                  "Không sao lưu được ghi chú và hàng đợi cũ. Chưa nhập dữ liệu.", ButtonType.OK);
              error.initOwner(primaryStage);
              error.show();
              return;
            }
            selectedStore.importLegacyNotes(legacy).whenComplete((count, importFailure) ->
                Platform.runLater(() -> {
                  if (currentStore != selectedStore || importFailure != null) return;
                  try {
                    Files.createDirectories(marker.getParent());
                    Files.writeString(marker, "Imported " + count + " local notes; backup: " + backup,
                        StandardCharsets.UTF_8);
                  } catch (Exception ignored) {
                    // Import succeeded; the choice may be offered again on the next login.
                  }
                }));
          }));
        }));
  }

  private Path backupLegacyFiles() throws IOException {
    Path destination = ClientStoragePaths.appDir().resolve("backups")
        .resolve("legacy-import-" + System.currentTimeMillis());
    Files.createDirectories(destination);
    copyVerified(ClientStoragePaths.noteCacheFile(), destination);
    copyVerified(ClientStoragePaths.noteOrderCacheFile(), destination);
    copyVerified(ClientStoragePaths.uiStateFile(), destination);
    copyVerified(ClientStoragePaths.syncQueueFile(), destination);
    return destination;
  }

  private void copyVerified(Path source, Path destination) throws IOException {
    Path copy = destination.resolve(source.getFileName());
    Files.copy(source, copy, StandardCopyOption.COPY_ATTRIBUTES);
    if (Files.mismatch(source, copy) != -1) {
      throw new IOException("Backup verification failed: " + source.getFileName());
    }
  }

  private void logout() {
    AuthDtos.SessionView session = currentSession;
    currentSession = null;
    if (currentMainController != null) {
      currentMainController.unbindPrimaryStage();
      currentMainController = null;
    }
    if (currentStore != null) {
      currentStore.close();
      currentStore = null;
    }
    showAuthWindow();
    if (session != null) {
      authClient.logout(session.token());
    }
  }

  @Override
  public void stop() {
    currentSession = null;
    if (socketClient != null) {
      socketClient.close();
    }
  }

  public static String stylesheetUrl() {
    URL stylesheet = ClientApplication.class.getResource("/css/conote.css");
    if (stylesheet == null) {
      throw new IllegalStateException("Unable to locate stylesheet: /css/conote.css");
    }
    return stylesheet.toExternalForm();
  }

  private void installWindowIcon(Stage stage) {
    try (InputStream stream = ClientApplication.class.getResourceAsStream("/icons/logo.png")) {
      if (stream != null) {
        stage.getIcons().add(new Image(stream));
      }
    } catch (Exception ignored) {
      // The app can still run without a custom icon.
    }
  }
}
