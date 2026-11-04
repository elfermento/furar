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
 * Táboa {@code block}. Concello automático con {@code @TenantId} (A7). A columna {@code period}
 * xérase na base desde {@code starts_at} e {@code ends_at}: aquí non se mapea.
 */
@Entity
@Table(name = "block")
public class BlockEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "facility_id", nullable = false, updatable = false)
  private UUID facilityId;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "start_minute", nullable = false)
  private int startMinute;

  @Column(name = "end_minute", nullable = false)
  private int endMinute;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "reason")
  private @Nullable String reason;

  @Column(name = "created_by", nullable = false, updatable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private @Nullable Long version;

  protected BlockEntity() {}

  BlockEntity(UUID id, UUID facilityId, String createdBy, Instant createdAt) {
    this.id = id;
    this.facilityId = facilityId;
    this.createdBy = createdBy;
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

  int startMinute() {
    return startMinute;
  }

  int endMinute() {
    return endMinute;
  }

  Instant startsAt() {
    return startsAt;
  }

  Instant endsAt() {
    return endsAt;
  }

  @Nullable String reason() {
    return reason;
  }

  String createdBy() {
    return createdBy;
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
      int startMinute,
      int endMinute,
      Instant startsAt,
      Instant endsAt,
      @Nullable String reason,
      Instant updatedAt) {
    this.date = date;
    this.startMinute = startMinute;
    this.endMinute = endMinute;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.reason = reason;
    this.updatedAt = updatedAt;
  }
}
