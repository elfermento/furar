// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code library_photo}: metadato dunha foto da biblioteca do concello (os bytes van en
 * {@link LibraryPhotoContentEntity}). Sen {@code @Version} propia: o texto alternativo cámbiase ao
 * gardar as imaxes dunha instalación, coa versión dela.
 */
@Entity
@Table(name = "library_photo")
public class LibraryPhotoEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  /** {@code {"gl": …, "es": …}}; nulo só mentres está pendente. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column
  private @Nullable Map<String, String> alt;

  @Column(nullable = false, updatable = false)
  private int width;

  @Column(nullable = false, updatable = false)
  private int height;

  @Column(name = "content_type", nullable = false, updatable = false)
  private String contentType;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "pending_until")
  private @Nullable Instant pendingUntil;

  @Transient private boolean fresh = true;

  protected LibraryPhotoEntity() {}

  LibraryPhotoEntity(UUID id, int width, int height, String contentType, Instant createdAt) {
    this.id = id;
    this.width = width;
    this.height = height;
    this.contentType = contentType;
    this.createdAt = createdAt;
  }

  @PostLoad
  @PrePersist
  void markStored() {
    fresh = false;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return fresh;
  }

  void describe(@Nullable Map<String, String> alt, @Nullable Instant pendingUntil) {
    this.alt = alt;
    this.pendingUntil = pendingUntil;
  }

  @Nullable Map<String, String> alt() {
    return alt;
  }

  int width() {
    return width;
  }

  int height() {
    return height;
  }

  String contentType() {
    return contentType;
  }

  Instant createdAt() {
    return createdAt;
  }

  @Nullable Instant pendingUntil() {
    return pendingUntil;
  }
}
