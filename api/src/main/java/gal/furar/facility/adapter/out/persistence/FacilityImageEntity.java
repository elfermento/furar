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
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code facility_image}: unha imaxe da lista dunha instalación, foto da biblioteca ({@code
 * photo_id}) ou debuxo ({@code drawing_code}). Non se edita: gardar as imaxes substitúe as filas.
 */
@Entity
@Immutable
@Table(name = "facility_image")
public class FacilityImageEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "facility_id", nullable = false)
  private UUID facilityId;

  /** Orde na galería, desde 0; a 0 é a portada. */
  @Column(nullable = false)
  private int position;

  @Column(name = "photo_id")
  private @Nullable UUID photoId;

  @Column(name = "drawing_code")
  private @Nullable String drawingCode;

  @Transient private boolean fresh = true;

  protected FacilityImageEntity() {}

  FacilityImageEntity(
      UUID id,
      UUID facilityId,
      int position,
      @Nullable UUID photoId,
      @Nullable String drawingCode) {
    this.id = id;
    this.facilityId = facilityId;
    this.position = position;
    this.photoId = photoId;
    this.drawingCode = drawingCode;
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

  UUID facilityId() {
    return facilityId;
  }

  @Nullable UUID photoId() {
    return photoId;
  }

  @Nullable String drawingCode() {
    return drawingCode;
  }
}
