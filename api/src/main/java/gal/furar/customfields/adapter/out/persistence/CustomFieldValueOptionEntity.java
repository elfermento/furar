// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

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

/** Táboa {@code custom_field_value_option}: unha opción marcada nunha selección múltiple. */
@Entity
@Immutable
@Table(name = "custom_field_value_option")
@IdClass(CustomFieldValueOptionEntity.Key.class)
public class CustomFieldValueOptionEntity implements Persistable<CustomFieldValueOptionEntity.Key> {

  /** Clave primaria {@code (value_id, option_id)}. */
  public record Key(UUID valueId, UUID optionId) implements Serializable {}

  @Id
  @Column(name = "value_id", nullable = false)
  private UUID valueId;

  @Id
  @Column(name = "option_id", nullable = false)
  private UUID optionId;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "field_id", nullable = false)
  private UUID fieldId;

  @Transient private boolean fresh = true;

  protected CustomFieldValueOptionEntity() {}

  CustomFieldValueOptionEntity(UUID valueId, UUID fieldId, UUID optionId) {
    this.valueId = valueId;
    this.fieldId = fieldId;
    this.optionId = optionId;
  }

  @PostLoad
  @PrePersist
  void markStored() {
    fresh = false;
  }

  @Override
  public Key getId() {
    return new Key(valueId, optionId);
  }

  @Override
  public boolean isNew() {
    return fresh;
  }

  UUID valueId() {
    return valueId;
  }

  UUID optionId() {
    return optionId;
  }
}
