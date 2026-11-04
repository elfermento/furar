// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.domain.RentalMode;
import gal.furar.facility.domain.UseAvailability;
import gal.furar.facility.domain.UseCapPeriod;
import gal.furar.facility.domain.UsePrice;
import gal.furar.facility.domain.UsePriceKind;
import gal.furar.facility.domain.UseRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code facility_use}. Concello automático con {@code @TenantId} (A7). A versión sobe en
 * cada gardado do uso, tamén cando só cambian as partes ou os períodos ({@code updated_at}).
 */
@Entity
@Table(name = "facility_use")
public class FacilityUseEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "facility_id", nullable = false, updatable = false)
  private UUID facilityId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "rental_mode", nullable = false)
  private RentalMode rentalMode;

  private @Nullable Integer capacity;

  @Column(name = "slot_minutes", nullable = false)
  private int slotMinutes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UseAvailability availability;

  @Column(name = "blocks_others", nullable = false)
  private boolean blocksOthers;

  @Column(nullable = false)
  private short weekdays = 127;

  @Enumerated(EnumType.STRING)
  @Column(name = "price_kind", nullable = false)
  private UsePriceKind priceKind = UsePriceKind.NONE;

  @Column(name = "price_cents")
  private @Nullable Integer priceCents;

  @Column(name = "rule_min_advance_hours")
  private @Nullable Integer ruleMinAdvanceHours;

  @Column(name = "rule_max_advance_days")
  private @Nullable Integer ruleMaxAdvanceDays;

  @Column(name = "rule_max_slots")
  private @Nullable Integer ruleMaxSlots;

  @Column(name = "rule_cap_count")
  private @Nullable Integer ruleCapCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "rule_cap_period")
  private @Nullable UseCapPeriod ruleCapPeriod;

  @Column(name = "rule_cancel_hours")
  private @Nullable Integer ruleCancelHours;

  @Column(name = "archived_at")
  private @Nullable Instant archivedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private @Nullable Long version;

  protected FacilityUseEntity() {}

  FacilityUseEntity(UUID id, UUID facilityId, Instant createdAt) {
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

  RentalMode rentalMode() {
    return rentalMode;
  }

  @Nullable Integer capacity() {
    return capacity;
  }

  int slotMinutes() {
    return slotMinutes;
  }

  UseAvailability availability() {
    return availability;
  }

  boolean blocksOthers() {
    return blocksOthers;
  }

  int weekdays() {
    return weekdays;
  }

  UsePrice price() {
    return new UsePrice(priceKind, priceCents);
  }

  UseRules rules() {
    return new UseRules(
        ruleMinAdvanceHours,
        ruleMaxAdvanceDays,
        ruleMaxSlots,
        ruleCapCount,
        ruleCapPeriod,
        ruleCancelHours);
  }

  @Nullable Instant archivedAt() {
    return archivedAt;
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
      String name,
      RentalMode rentalMode,
      @Nullable Integer capacity,
      int slotMinutes,
      UseAvailability availability,
      boolean blocksOthers,
      @Nullable Instant archivedAt,
      Instant updatedAt) {
    this.name = name;
    this.rentalMode = rentalMode;
    this.capacity = capacity;
    this.slotMinutes = slotMinutes;
    this.availability = availability;
    this.blocksOthers = blocksOthers;
    this.archivedAt = archivedAt;
    this.updatedAt = updatedAt;
  }

  /** Días da semana, prezo e normas (D2-02). */
  void applyTerms(int weekdays, UsePrice price, UseRules rules) {
    this.weekdays = (short) weekdays;
    this.priceKind = price.kind();
    this.priceCents = price.amountCents();
    this.ruleMinAdvanceHours = rules.minAdvanceHours();
    this.ruleMaxAdvanceDays = rules.maxAdvanceDays();
    this.ruleMaxSlots = rules.maxSlots();
    this.ruleCapCount = rules.capCount();
    this.ruleCapPeriod = rules.capPeriod();
    this.ruleCancelHours = rules.cancelHours();
  }
}
