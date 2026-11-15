package com.conote.client.cache;

import com.conote.common.model.Share;
import com.conote.common.model.Tag;
import com.conote.common.model.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.AccessDeniedException;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

public class JsonStore {
  private final ObjectMapper objectMapper;

  public JsonStore() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.addMixIn(User.class, UserMixIn.class);
    objectMapper.addMixIn(Tag.class, TagMixIn.class);
    objectMapper.addMixIn(Share.class, ShareMixIn.class);
  }

  public <T> T read(Path filePath, Class<T> valueType, Supplier<T> defaultSupplier) {
    try {
      if (filePath == null || !Files.exists(filePath)) {
        return defaultSupplier.get();
      }

      String json = Files.readString(filePath, StandardCharsets.UTF_8);
      if (json == null || json.isBlank()) {
        return defaultSupplier.get();
      }

      return objectMapper.readValue(json, valueType);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read JSON file: " + filePath, exception);
    }
  }

  public <T> T read(Path filePath, TypeReference<T> typeReference, Supplier<T> defaultSupplier) {
    try {
      if (filePath == null || !Files.exists(filePath)) {
        return defaultSupplier.get();
      }

      String json = Files.readString(filePath, StandardCharsets.UTF_8);
      if (json == null || json.isBlank()) {
        return defaultSupplier.get();
      }

      return objectMapper.readValue(json, typeReference);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read JSON file: " + filePath, exception);
    }
  }

  public void write(Path filePath, Object value) {
    if (filePath == null) {
      return;
    }
    Path target = filePath.toAbsolutePath().normalize();
    Path temporary = null;
    try {
      byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
      Files.createDirectories(target.getParent());
      temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
      Files.write(temporary, json);
      replaceWithRetry(temporary, target);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to write JSON file: " + filePath, exception);
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // A failed cleanup does not change the result of the cache write.
        }
      }
    }
  }

  private void replaceWithRetry(Path temporary, Path target) throws IOException {
    for (int attempt = 0; attempt < 5; attempt++) {
      try {
        try {
          Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
          Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return;
      } catch (AccessDeniedException exception) {
        if (attempt == 4) throw exception;
        try {
          Thread.sleep(20L * (attempt + 1));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted while replacing cache file", interrupted);
        }
      }
    }
  }

  public String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize value to JSON.", exception);
    }
  }

  public <T> T fromJson(String json, Class<T> valueType) {
    try {
      return objectMapper.readValue(json, valueType);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to deserialize JSON value.", exception);
    }
  }

  public <T> T fromJson(String json, TypeReference<T> typeReference) {
    try {
      return objectMapper.readValue(json, typeReference);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to deserialize JSON value.", exception);
    }
  }

  @JsonIgnoreProperties({"notes", "tags", "emailOtps", "sharesGiven", "sharesReceived"})
  private abstract static class UserMixIn {
  }

  @JsonIgnoreProperties({"notes"})
  private abstract static class TagMixIn {
  }

  @JsonIgnoreProperties({"note"})
  private abstract static class ShareMixIn {
  }
}
