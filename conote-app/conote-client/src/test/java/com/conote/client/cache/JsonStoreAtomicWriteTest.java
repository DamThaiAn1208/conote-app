package com.conote.client.cache;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonStoreAtomicWriteTest {
  @TempDir Path directory;

  @Test
  void serializationFailureLeavesPreviousCacheUntouched() throws Exception {
    Path cache = directory.resolve("notes.json");
    JsonStore store = new JsonStore();
    store.write(cache, new Saved("old"));
    String previous = Files.readString(cache);

    assertThrows(IllegalStateException.class, () -> store.write(cache, new Broken()));
    assertEquals(previous, Files.readString(cache));
  }

  @Test
  void successfulReplacementLeavesOneCompleteJsonFile() throws Exception {
    Path cache = directory.resolve("nested").resolve("notes.json");
    JsonStore store = new JsonStore();
    store.write(cache, new Saved("first"));
    store.write(cache, new Saved("second"));

    assertEquals("second", store.read(cache, Saved.class, () -> new Saved("missing")).value());
    try (var files = Files.list(cache.getParent())) {
      assertEquals(1, files.count());
    }
    assertTrue(Files.size(cache) > 0);
  }

  private record Saved(String value) { }

  private static final class Broken {
    public String getValue() {
      throw new IllegalStateException("intentional serialization failure");
    }
  }
}
