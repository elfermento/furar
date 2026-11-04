// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.domain.FacilityStatus;
import gal.furar.facility.domain.FacilityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code facility}. Concello automático con {@code @TenantId} (A7, ADR-0006). Os deportes e
 * os servizos van nas súas táboas ({@link FacilitySportEntity}, {@link FacilityAmenityEntity}).
 */
@Entity
@Table(name = "facility")
public class FacilityEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FacilityStatus status;

  @Enumerated(EnumType.STRING)
  private @Nullable FacilityType type;

  @Column(name = "ridega_code")
  private @Nullable String ridegaCode;

  private @Nullable String phone;

  private @Nullable String email;

  @Column(name = "online_booking", nullable = false)
  private boolean onlineBooking = true;

  @Column(name = "show_free_slots", nullable = false)
  private boolean showFreeSlots;

  private @Nullable Boolean covered;

  /** {@code {"gl": …, "es": …}} ou nulo. */
  @JdbcTypeCode(SqlTypes.JSON)
  private @Nullable Map<String, String> description;

  /** Normas da instalación, {@code {"gl": …, "es": …}} ou nulo. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "house_rules")
  private @Nullable Map<String, String> houseRules;

  /** Pago e devolución da instalación, {@code {"gl": …, "es": …}} ou nulo. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payment_note")
  private @Nullable Map<String, String> paymentNote;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "refund_note")
  private @Nullable Map<String, String> refundNote;

  /**
   * O enderezo vai enteiro ou non vai (borrador sen publicar: {@code
   * facility_address_complete_ck}).
   */
  private @Nullable String street;

  @Column(name = "street_number")
  private @Nullable String streetNumber;

  @Column(name = "postal_code")
  private @Nullable String postalCode;

  private @Nullable String locality;

  @Column(name = "ine_code")
  private @Nullable String ineCode;

  @Column(precision = 8, scale = 6)
  private @Nullable BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private @Nullable BigDecimal longitude;

  @Column(nullable = false)
  private boolean published;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private @Nullable Long version;

  protected FacilityEntity() {}

  FacilityEntity(UUID id, Instant createdAt) {
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

  String name() {
    return name;
  }

  FacilityStatus status() {
    return status;
  }

  @Nullable FacilityType type() {
    return type;
  }

  @Nullable String ridegaCode() {
    return ridegaCode;
  }

  @Nullable String phone() {
    return phone;
  }

  @Nullable String email() {
    return email;
  }

  boolean onlineBooking() {
    return onlineBooking;
  }

  boolean showFreeSlots() {
    return showFreeSlots;
  }

  @Nullable Boolean covered() {
    return covered;
  }

  @Nullable Map<String, String> description() {
    return description;
  }

  @Nullable Map<String, String> houseRules() {
    return houseRules;
  }

  void applyHouseRules(@Nullable Map<String, String> houseRules) {
    this.houseRules = houseRules;
  }

  @Nullable Map<String, String> paymentNote() {
    return paymentNote;
  }

  @Nullable Map<String, String> refundNote() {
    return refundNote;
  }

  void applyPaymentNotes(
      @Nullable Map<String, String> paymentNote, @Nullable Map<String, String> refundNote) {
    this.paymentNote = paymentNote;
    this.refundNote = refundNote;
  }

  @Nullable String street() {
    return street;
  }

  @Nullable String streetNumber() {
    return streetNumber;
  }

  @Nullable String postalCode() {
    return postalCode;
  }

  @Nullable String locality() {
    return locality;
  }

  @Nullable String ineCode() {
    return ineCode;
  }

  @Nullable BigDecimal latitude() {
    return latitude;
  }

  @Nullable BigDecimal longitude() {
    return longitude;
  }

  boolean published() {
    return published;
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
  void apply(String name, FacilityStatus status, boolean published, Instant updatedAt) {
    this.name = name;
    this.status = status;
    this.published = published;
    this.updatedAt = updatedAt;
  }

  void applyDetails(
      @Nullable FacilityType type,
      @Nullable String ridegaCode,
      @Nullable String phone,
      @Nullable String email,
      boolean onlineBooking,
      boolean showFreeSlots,
      @Nullable Boolean covered,
      @Nullable Map<String, String> description) {
    this.type = type;
    this.ridegaCode = ridegaCode;
    this.phone = phone;
    this.email = email;
    this.onlineBooking = onlineBooking;
    this.showFreeSlots = showFreeSlots;
    this.covered = covered;
    this.description = description;
  }

  void applyAddress(
      @Nullable String street,
      @Nullable String streetNumber,
      @Nullable String postalCode,
      @Nullable String locality,
      @Nullable String ineCode,
      @Nullable BigDecimal latitude,
      @Nullable BigDecimal longitude) {
    this.street = street;
    this.streetNumber = streetNumber;
    this.postalCode = postalCode;
    this.locality = locality;
    this.ineCode = ineCode;
    this.latitude = latitude;
    this.longitude = longitude;
  }
}
