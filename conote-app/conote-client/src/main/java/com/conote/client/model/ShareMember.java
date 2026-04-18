package com.conote.client.model;

import com.conote.common.enums.SharePermission;
import java.util.Locale;

public class ShareMember {
  private final String displayName;
  private final String email;
  private final SharePermission permission;

  public ShareMember(String displayName, String email, SharePermission permission) {
    this.displayName = normalizeName(displayName, email);
    this.email = email == null ? "" : email.trim();
    this.permission = permission == null ? SharePermission.VIEW : permission;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getEmail() {
    return email;
  }

  public SharePermission getPermission() {
    return permission;
  }

  public String getAvatarText() {
    String[] segments = displayName.trim().split("\\s+");
    if (segments.length == 0 || displayName.isBlank()) {
      return "CN";
    }
    if (segments.length == 1) {
      return segments[0].substring(0, Math.min(2, segments[0].length())).toUpperCase(Locale.ROOT);
    }
    String first = segments[0].substring(0, 1);
    String last = segments[segments.length - 1].substring(0, 1);
    return (first + last).toUpperCase(Locale.ROOT);
  }

  private static String normalizeName(String displayName, String email) {
    if (displayName != null && !displayName.isBlank()) {
      return displayName.trim();
    }
    if (email == null || email.isBlank()) {
      return "CoNote User";
    }
    int atIndex = email.indexOf('@');
    return (atIndex > 0 ? email.substring(0, atIndex) : email).trim();
  }
}
