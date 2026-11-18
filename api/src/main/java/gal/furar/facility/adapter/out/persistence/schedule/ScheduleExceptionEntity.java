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

/** Táboa {@code schedule_exception}. Concello automático con {@code @TenantId} (A7). */
@Entity
@Table(name = "schedule_exception")
public class ScheduleExceptionEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "facility_id", nullable = false, updatable = false)
  private UUID facilityId;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "open", nullable = false)
  private boolean open;

  @Column(name = "start_minute")
  private @Nullable Integer startMinute;

  @Column(name = "end_minute")
  private @Nullable Integer endMinute;

  @Column(name = "reason")
  private @Nullable String reason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private @Nullable Long version;

  protected ScheduleExceptionEntity() {}

  ScheduleExceptionEntity(UUID id, UUID facilityId, Instant createdAt) {
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

  LocalDate date() {
    return date;
  }

  boolean open() {
    return open;
  }

  @Nullable Integer startMinute() {
    return startMinute;
  }

  @Nullable Integer endMinute() {
    return endMinute;
  }

  @Nullable String reason() {
    return reason;
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
  void apply(
      LocalDate date,
      @Nullable Integer startMinute,
      @Nullable Integer endMinute,
      @Nullable String reason,
      Instant updatedAt) {
    this.date = date;
    this.open = startMinute != null;
    this.startMinute = startMinute;
    this.endMinute = endMinute;
    this.reason = reason;
    this.updatedAt = updatedAt;
  }
}
