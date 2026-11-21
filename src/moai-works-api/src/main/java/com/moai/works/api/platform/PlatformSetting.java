package com.moai.works.api.platform;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnTransformer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 플랫폼 설정 한 줄 (정본). 시스템 관리자만 바꾸고, 바꿀 때마다 감사에 남긴다. 값은 JSON. */
@Entity
@Table(name = "platform_setting")
public class PlatformSetting {

  @Id
  @Column(length = 100)
  private String key;

  @Column(nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String value;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected PlatformSetting() {
  }

  public PlatformSetting(String key, String valueJson, UUID by, Instant now) {
    this.key = key;
    this.value = valueJson;
    this.updatedBy = by;
    this.updatedAt = now;
  }

  public void change(String valueJson, UUID by, Instant now) {
    this.value = valueJson;
    this.updatedBy = by;
    this.updatedAt = now;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
