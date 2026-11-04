// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code season}. Concello automático con {@code @TenantId} (A7). A versión sobe en cada
 * gardado da tempada, tamén cando só cambia a semana ({@code updated_at}).
 */
@Entity
@Table(name = "season")
public class SeasonEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "facility_id", nullable = false, updatable = false)
  private UUID facilityId;

  @Column(nullable = false)
  private String name;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  /** Nula: tempada sen fin. */
  @Column(name = "end_date")
  private @Nullable LocalDate endDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private @Nullable Long version;

  protected SeasonEntity() {}

  SeasonEntity(UUID id, UUID facilityId, Instant createdAt) {
    this.id = id;
    this.facilityId = facilityId;
    this.createdAt = createdAt;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return version == null;
  }

  UUID facilityId() {
    return facilityId;
  }

  String name() {
    return name;
  }

  LocalDate startDate() {
    return startDate;
  }

  @Nullable LocalDate endDate() {
    return endDate;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }

  @Nullable Long version() {
    return version;
  }

  /** Copia os atributos editables (nunca {@code merge}: ADR-0010). */
  void apply(String name, LocalDate startDate, @Nullable LocalDate endDate, Instant updatedAt) {
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
    this.updatedAt = updatedAt;
  }
}
