// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

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

/** Táboa {@code event}. Concello automático con {@code @TenantId} (A7, ADR-0006). */
@Entity
@Table(name = "event")
public class EventEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "name_gl", nullable = false)
  private String nameGl;

  @Column(name = "name_es", nullable = false)
  private String nameEs;

  @Column(name = "description_gl")
  private @Nullable String descriptionGl;

  @Column(name = "description_es")
  private @Nullable String descriptionEs;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "facility_id")
  private @Nullable UUID facilityId;

  @Column(name = "place")
  private @Nullable String place;

  @Column(name = "capacity")
  private @Nullable Integer capacity;

  @Column(name = "price_cents")
  private @Nullable Integer priceCents;

  @Column(name = "registration_deadline")
  private @Nullable Instant registrationDeadline;

  @Column(nullable = false)
  private boolean published;

  @Column(nullable = false)
  private boolean approval;

  @Column(name = "cancelled_at")
  private @Nullable Instant cancelledAt;

  /** {@code ONE_DAY} ou {@code SCHEDULED} (ADR-0041). */
  @Column(nullable = false)
  private String kind;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "use_id")
  private @Nullable UUID useId;

  @Column(name = "part_id")
  private @Nullable UUID partId;

  /** Un bit por día, luns = 1 … domingo = 64 (coma os usos de facility). */
  @Column(name = "weekdays")
  private @Nullable Short weekdays;

  @Column(name = "start_minute")
  private @Nullable Integer startMinute;

  @Column(name = "end_minute")
  private @Nullable Integer endMinute;

  @Column(name = "first_date")
  private @Nullable LocalDate firstDate;

  @Column(name = "last_date")
  private @Nullable LocalDate lastDate;

  @Column(name = "ended_from")
  private @Nullable LocalDate endedFrom;

  @Column(name = "birth_year_from")
  private @Nullable Short birthYearFrom;

  @Column(name = "birth_year_to")
  private @Nullable Short birthYearTo;

  @Column(name = "price_period", nullable = false)
  private String pricePeriod;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private @Nullable Long version;

  protected EventEntity() {}

  EventEntity(UUID id, Instant createdAt) {
    this.id = id;
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

  UUID municipalityId() {
    return municipalityId;
  }

  String nameGl() {
    return nameGl;
  }

  String nameEs() {
    return nameEs;
  }

  @Nullable String descriptionGl() {
    return descriptionGl;
  }

  @Nullable String descriptionEs() {
    return descriptionEs;
  }

  Instant startsAt() {
    return startsAt;
  }

  @Nullable UUID facilityId() {
    return facilityId;
  }

  @Nullable String place() {
    return place;
  }

  @Nullable Integer capacity() {
    return capacity;
  }

  @Nullable Integer priceCents() {
    return priceCents;
  }

  @Nullable Instant registrationDeadline() {
    return registrationDeadline;
  }

  boolean published() {
    return published;
  }

  boolean approval() {
    return approval;
  }

  @Nullable Instant cancelledAt() {
    return cancelledAt;
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

  String kind() {
    return kind;
  }

  Instant endsAt() {
    return endsAt;
  }

  @Nullable UUID useId() {
    return useId;
  }

  @Nullable UUID partId() {
    return partId;
  }

  @Nullable Short weekdays() {
    return weekdays;
  }

  @Nullable Integer startMinute() {
    return startMinute;
  }

  @Nullable Integer endMinute() {
    return endMinute;
  }

  @Nullable LocalDate firstDate() {
    return firstDate;
  }

  @Nullable LocalDate lastDate() {
    return lastDate;
  }

  @Nullable LocalDate endedFrom() {
    return endedFrom;
  }

  @Nullable Short birthYearFrom() {
    return birthYearFrom;
  }

  @Nullable Short birthYearTo() {
    return birthYearTo;
  }

  String pricePeriod() {
    return pricePeriod;
  }

  /** O tipo, o horario, o límite de idade e o período do prezo (ADR-0041). */
  void applyActivity(
      String kind,
      Instant endsAt,
      @Nullable UUID useId,
      @Nullable UUID partId,
      @Nullable Short weekdays,
      @Nullable Integer startMinute,
      @Nullable Integer endMinute,
      @Nullable LocalDate firstDate,
      @Nullable LocalDate lastDate,
      @Nullable LocalDate endedFrom,
      @Nullable Short birthYearFrom,
      @Nullable Short birthYearTo,
      String pricePeriod) {
    this.kind = kind;
    this.endsAt = endsAt;
    this.useId = useId;
    this.partId = partId;
    this.weekdays = weekdays;
    this.startMinute = startMinute;
    this.endMinute = endMinute;
    this.firstDate = firstDate;
    this.lastDate = lastDate;
    this.endedFrom = endedFrom;
    this.birthYearFrom = birthYearFrom;
    this.birthYearTo = birthYearTo;
    this.pricePeriod = pricePeriod;
  }

  /** Copia os atributos editables (nunca {@code merge}: ADR-0010). */
  void apply(
      String nameGl,
      String nameEs,
      @Nullable String descriptionGl,
      @Nullable String descriptionEs,
      Instant startsAt,
      @Nullable UUID facilityId,
      @Nullable String place,
      @Nullable Integer capacity,
      @Nullable Integer priceCents,
      @Nullable Instant registrationDeadline,
      boolean published,
      boolean approval,
      @Nullable Instant cancelledAt,
      Instant updatedAt) {
    this.nameGl = nameGl;
    this.nameEs = nameEs;
    this.descriptionGl = descriptionGl;
    this.descriptionEs = descriptionEs;
    this.startsAt = startsAt;
    this.facilityId = facilityId;
    this.place = place;
    this.capacity = capacity;
    this.priceCents = priceCents;
    this.registrationDeadline = registrationDeadline;
    this.published = published;
    this.approval = approval;
    this.cancelledAt = cancelledAt;
    this.updatedAt = updatedAt;
  }
}
