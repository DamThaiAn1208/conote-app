package com.conote.client.app;

import com.conote.client.controller.MainWindowController;
import com.conote.client.util.LoadedView;
import com.conote.client.util.ViewLoader;
import java.io.InputStream;
import java.net.URL;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class ClientApplication extends Application {
  private static final Duration SMOKE_TEST_CLOSE_DELAY = Duration.millis(250);

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    LoadedView<MainWindowController> view = ViewLoader.load("/fxml/main/MainWindow.fxml");

    Scene scene = new Scene(view.root(), 500, 760);
    scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(stylesheetUrl());

    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setTitle("CoNote");
    stage.setMinWidth(500);
    stage.setMinHeight(620);
    stage.setScene(scene);
    installWindowIcon(stage);
    stage.show();

    if (Boolean.getBoolean("conote.smokeTest")) {
      PauseTransition delay = new PauseTransition(SMOKE_TEST_CLOSE_DELAY);
      delay.setOnFinished(event -> Platform.exit());
      delay.play();
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
    try (InputStream stream = ClientApplication.class.getResourceAsStream("/icons/icon.png")) {
      if (stream != null) {
        stage.getIcons().add(new Image(stream));
      }
    } catch (Exception ignored) {
      // The app can still run without a custom icon.
    }
  }
}
