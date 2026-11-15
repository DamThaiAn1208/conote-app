package com.conote.client.util;

import java.io.IOException;
import java.net.URL;
import java.util.function.Function;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public final class ViewLoader {
  private ViewLoader() {
  }

  public static <T> LoadedView<T> load(String resourcePath) {
    return load(resourcePath, null);
  }

  public static <T> LoadedView<T> load(String resourcePath,
                                       Function<Class<?>, Object> controllerFactory) {
    URL resource = ViewLoader.class.getResource(resourcePath);
    if (resource == null) {
      throw new IllegalStateException("FXML resource not found: " + resourcePath);
    }
    FXMLLoader loader = new FXMLLoader(resource);
    if (controllerFactory != null) {
      loader.setControllerFactory(controllerFactory::apply);
    }
    try {
      Parent root = loader.load();
      @SuppressWarnings("unchecked")
      T controller = (T) loader.getController();
      return new LoadedView<>(root, controller);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load FXML: " + resourcePath, exception);
    }
  }
}
