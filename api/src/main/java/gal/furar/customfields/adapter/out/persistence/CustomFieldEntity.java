// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/** Táboa {@code custom_field}: a definición, coas regras en columnas tipadas. */
@Entity
@Table(name = "custom_field")
public class CustomFieldEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(nullable = false, updatable = false)
  private String code;

  @Column(nullable = false, updatable = false)
  private String type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, String> label;

  @JdbcTypeCode(SqlTypes.JSON)
  private @Nullable Map<String, String> help;

  @Column(name = "personal_data", nullable = false)
  private boolean personalData;

  @Column(name = "min_length")
  private @Nullable Integer minLength;

  @Column(name = "max_length")
  private @Nullable Integer maxLength;

  @Column(name = "min_number")
  private @Nullable BigDecimal minNumber;

  @Column(name = "max_number")
  private @Nullable BigDecimal maxNumber;

  private @Nullable Integer decimals;

  @Column(name = "min_date")
  private @Nullable LocalDate minDate;

  @Column(name = "max_date")
  private @Nullable LocalDate maxDate;

  @Column(name = "text_format")
  private @Nullable String textFormat;

  @Column(name = "min_choices")
  private @Nullable Integer minChoices;

  @Column(name = "max_choices")
  private @Nullable Integer maxChoices;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private @Nullable Long version;

  protected CustomFieldEntity() {}

  CustomFieldEntity(UUID id, String code, String type, Instant createdAt) {
    this.id = id;
    this.code = code;
    this.type = type;
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

  String code() {
    return code;
  }

  String type() {
    return type;
  }

  Map<String, String> label() {
    return label;
  }

  @Nullable Map<String, String> help() {
    return help;
  }

  boolean personalData() {
    return personalData;
  }

  @Nullable Integer minLength() {
    return minLength;
  }

  @Nullable Integer maxLength() {
    return maxLength;
  }

  @Nullable BigDecimal minNumber() {
    return minNumber;
  }

  @Nullable BigDecimal maxNumber() {
    return maxNumber;
  }

  @Nullable Integer decimals() {
    return decimals;
  }

  @Nullable LocalDate minDate() {
    return minDate;
  }

  @Nullable LocalDate maxDate() {
    return maxDate;
  }

  @Nullable String textFormat() {
    return textFormat;
  }

  @Nullable Integer minChoices() {
    return minChoices;
  }

  @Nullable Integer maxChoices() {
    return maxChoices;
  }

  String status() {
    return status;
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

  void applyTexts(Map<String, String> label, @Nullable Map<String, String> help) {
    this.label = label;
    this.help = help;
  }

  void applyRules(
      @Nullable Integer minLength,
      @Nullable Integer maxLength,
      @Nullable BigDecimal minNumber,
      @Nullable BigDecimal maxNumber,
      @Nullable Integer decimals,
      @Nullable LocalDate minDate,
      @Nullable LocalDate maxDate,
      @Nullable String textFormat,
      @Nullable Integer minChoices,
      @Nullable Integer maxChoices) {
    this.minLength = minLength;
    this.maxLength = maxLength;
    this.minNumber = minNumber;
    this.maxNumber = maxNumber;
    this.decimals = decimals;
    this.minDate = minDate;
    this.maxDate = maxDate;
    this.textFormat = textFormat;
    this.minChoices = minChoices;
    this.maxChoices = maxChoices;
  }

  void applyState(boolean personalData, String status, Instant updatedAt) {
    this.personalData = personalData;
    this.status = status;
    this.updatedAt = updatedAt;
  }
}
