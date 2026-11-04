// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code custom_field_value}: un valor por entidade e campo, na columna do seu tipo (a base
 * compróbao). Non se edita: o almacén substitúe os valores da entidade (borra e insire).
 */
@Entity
@Immutable
@Table(name = "custom_field_value")
public class CustomFieldValueEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "field_id", nullable = false)
  private UUID fieldId;

  @Column(name = "field_type", nullable = false)
  private String fieldType;

  @Column(nullable = false)
  private String target;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "text_value")
  private @Nullable String textValue;

  @Column(name = "number_value")
  private @Nullable BigDecimal numberValue;

  @Column(name = "date_value")
  private @Nullable LocalDate dateValue;

  @Column(name = "bool_value")
  private @Nullable Boolean boolValue;

  @Column(name = "option_id")
  private @Nullable UUID optionId;

  @Transient private boolean fresh = true;

  protected CustomFieldValueEntity() {}

  CustomFieldValueEntity(UUID id, UUID fieldId, String fieldType, String target, UUID entityId) {
    this.id = id;
    this.fieldId = fieldId;
    this.fieldType = fieldType;
    this.target = target;
    this.entityId = entityId;
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

  UUID fieldId() {
    return fieldId;
  }

  String fieldType() {
    return fieldType;
  }

  UUID entityId() {
    return entityId;
  }

  @Nullable String textValue() {
    return textValue;
  }

  @Nullable BigDecimal numberValue() {
    return numberValue;
  }

  @Nullable LocalDate dateValue() {
    return dateValue;
  }

  @Nullable Boolean boolValue() {
    return boolValue;
  }

  @Nullable UUID optionId() {
    return optionId;
  }

  CustomFieldValueEntity text(String value) {
    this.textValue = value;
    return this;
  }

  CustomFieldValueEntity number(BigDecimal value) {
    this.numberValue = value;
    return this;
  }

  CustomFieldValueEntity date(LocalDate value) {
    this.dateValue = value;
    return this;
  }

  CustomFieldValueEntity bool(boolean value) {
    this.boolValue = value;
    return this;
  }

  CustomFieldValueEntity option(UUID value) {
    this.optionId = value;
    return this;
  }
}
