// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.domain.PhotoVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code library_photo_content}: os bytes dunha variante ({@code storage external}). Só se le
 * ao servir a imaxe; bórrase coa foto ({@code on delete cascade}).
 */
@Entity
@Immutable
@Table(name = "library_photo_content")
@IdClass(LibraryPhotoContentEntity.Key.class)
public class LibraryPhotoContentEntity implements Persistable<LibraryPhotoContentEntity.Key> {

  /** Clave primaria {@code (photo_id, variant)}. */
  public record Key(UUID photoId, PhotoVariant variant) implements Serializable {}

  @Id
  @Column(name = "photo_id", nullable = false)
  private UUID photoId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PhotoVariant variant;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(nullable = false)
  private byte[] bytes;

  @Transient private boolean fresh = true;

  protected LibraryPhotoContentEntity() {}

  LibraryPhotoContentEntity(UUID photoId, PhotoVariant variant, byte[] bytes) {
    this.photoId = photoId;
    this.variant = variant;
    this.bytes = bytes;
  }

  @PostLoad
  @PrePersist
  void markStored() {
    fresh = false;
  }

  @Override
  public Key getId() {
    return new Key(photoId, variant);
  }

  @Override
  public boolean isNew() {
    return fresh;
  }

  byte[] bytes() {
    return bytes;
  }
}
