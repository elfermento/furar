// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Táboa {@code facility_amenity}: un servizo ou unha axuda de accesibilidade da instalación. */
@Entity
@Immutable
@Table(name = "facility_amenity")
@IdClass(FacilityAmenityEntity.Key.class)
public class FacilityAmenityEntity implements Persistable<FacilityAmenityEntity.Key> {

  /** Clave primaria {@code (facility_id, code)}. */
  public record Key(UUID facilityId, String code) implements Serializable {}

  @Id
  @Column(name = "facility_id", nullable = false)
  private UUID facilityId;

  @Id
  @Column(nullable = false)
  private String code;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Transient private boolean fresh = true;

  protected FacilityAmenityEntity() {}

  FacilityAmenityEntity(UUID facilityId, String code) {
    this.facilityId = facilityId;
    this.code = code;
  }

  @PostLoad
  @PrePersist
  void markStored() {
    fresh = false;
  }

  @Override
  public Key getId() {
    return new Key(facilityId, code);
  }

  @Override
  public boolean isNew() {
    return fresh;
  }

  UUID facilityId() {
    return facilityId;
  }

  String code() {
    return code;
  }
}
