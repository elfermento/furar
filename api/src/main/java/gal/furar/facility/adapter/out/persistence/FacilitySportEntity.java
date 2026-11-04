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
 * Táboa {@code facility_sport}: un deporte dunha instalación, do catálogo ({@code sport_code}) ou
 * do concello ({@code custom_sport_id}). Non se edita: gardar a tarxeta Datos substitúe as filas.
 */
@Entity
@Immutable
@Table(name = "facility_sport")
public class FacilitySportEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "facility_id", nullable = false)
  private UUID facilityId;

  @Column(name = "sport_code")
  private @Nullable String sportCode;

  @Column(name = "custom_sport_id")
  private @Nullable UUID customSportId;

  /** Orde na tarxeta Datos, desde 0. */
  @Column(nullable = false)
  private int position;

  @Transient private boolean fresh = true;

  protected FacilitySportEntity() {}

  FacilitySportEntity(
      UUID id,
      UUID facilityId,
      @Nullable String sportCode,
      @Nullable UUID customSportId,
      int position) {
    this.id = id;
    this.position = position;
    this.facilityId = facilityId;
    this.sportCode = sportCode;
    this.customSportId = customSportId;
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

  @Nullable String sportCode() {
    return sportCode;
  }

  @Nullable UUID customSportId() {
    return customSportId;
  }
}
