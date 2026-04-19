package com.conote.client.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class ClientStoragePaths {
  private static final String STORAGE_DIRECTORY_OVERRIDE_PROPERTY = "conote.storage.dir";
  private static final String APP_DIRECTORY_NAME = "CoNote";
  private static final String FALLBACK_DIRECTORY_NAME = ".conote";
  private static final String NOTE_CACHE_FILE_NAME = "noteCache.json";
  private static final String UI_STATE_FILE_NAME = "uiState.json";
  private static final String SYNC_QUEUE_FILE_NAME = "syncQueue.json";

  private static volatile boolean initialized;
  private static Path appDir;
  private static Path cacheDir;
  private static Path pendingDir;
  private static Path noteCacheFile;
  private static Path uiStateFile;
  private static Path syncQueueFile;

  private ClientStoragePaths() {
  }

  public static synchronized void init() {
    if (initialized) {
      return;
    }

    appDir = resolveAppDirectory();
    cacheDir = appDir.resolve("cache");
    pendingDir = appDir.resolve("pending");
    noteCacheFile = cacheDir.resolve(NOTE_CACHE_FILE_NAME);
    uiStateFile = cacheDir.resolve(UI_STATE_FILE_NAME);
    syncQueueFile = pendingDir.resolve(SYNC_QUEUE_FILE_NAME);

    try {
      Files.createDirectories(cacheDir);
      Files.createDirectories(pendingDir);
      ensureFile(noteCacheFile, "[]");
      ensureFile(syncQueueFile, "[]");
      ensureFile(uiStateFile, "{}");
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to initialize CoNote local storage paths.", exception);
    }

    initialized = true;
  }

  public static synchronized void resetForTesting() {
    initialized = false;
    appDir = null;
    cacheDir = null;
    pendingDir = null;
    noteCacheFile = null;
    uiStateFile = null;
    syncQueueFile = null;
  }

  public static Path appDir() {
    init();
    return appDir;
  }

  public static Path cacheDir() {
    init();
    return cacheDir;
  }

  public static Path pendingDir() {
    init();
    return pendingDir;
  }

  public static Path noteCacheFile() {
    init();
    return noteCacheFile;
  }

  public static Path uiStateFile() {
    init();
    return uiStateFile;
  }

  public static Path syncQueueFile() {
    init();
    return syncQueueFile;
  }

  private static Path resolveAppDirectory() {
    String storageOverride = System.getProperty(STORAGE_DIRECTORY_OVERRIDE_PROPERTY);
    if (storageOverride != null && !storageOverride.isBlank()) {
      try {
        return Path.of(storageOverride);
      } catch (InvalidPathException ignored) {
        // Fall through to the default runtime locations below.
      }
    }

    String localAppData = System.getenv("LOCALAPPDATA");
    if (localAppData != null && !localAppData.isBlank()) {
      try {
        return Path.of(localAppData).resolve(APP_DIRECTORY_NAME);
      } catch (InvalidPathException ignored) {
        // Fall back to the user home directory below.
      }
    }

    return Path.of(System.getProperty("user.home")).resolve(FALLBACK_DIRECTORY_NAME);
  }

  private static void ensureFile(Path filePath, String defaultContents) throws IOException {
    if (Files.exists(filePath)) {
      return;
    }

    Files.createDirectories(filePath.getParent());
    Files.writeString(filePath, defaultContents, StandardCharsets.UTF_8);
  }
}
