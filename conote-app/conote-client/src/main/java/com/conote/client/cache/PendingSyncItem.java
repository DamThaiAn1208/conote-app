package com.conote.client.cache;

import java.time.LocalDateTime;

public class PendingSyncItem {
  private String queueId;
  private SyncActionType actionType;
  private String entityId;
  private String payloadJson;
  private int retryCount;
  private LocalDateTime createdAt;
  private String lastError;

  public PendingSyncItem() {
  }

  public PendingSyncItem(
      String queueId,
      SyncActionType actionType,
      String entityId,
      String payloadJson,
      int retryCount,
      LocalDateTime createdAt,
      String lastError) {
    this.queueId = queueId;
    this.actionType = actionType;
    this.entityId = entityId;
    this.payloadJson = payloadJson;
    this.retryCount = retryCount;
    this.createdAt = createdAt;
    this.lastError = lastError;
  }

  public String getQueueId() {
    return queueId;
  }

  public void setQueueId(String queueId) {
    this.queueId = queueId;
  }

  public SyncActionType getActionType() {
    return actionType;
  }

  public void setActionType(SyncActionType actionType) {
    this.actionType = actionType;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(int retryCount) {
    this.retryCount = retryCount;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }
}
