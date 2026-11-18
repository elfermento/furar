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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** Táboa {@code custom_field_option}: unha opción dun campo de selección. */
@Entity
@Table(name = "custom_field_option")
public class CustomFieldOptionEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "field_id", nullable = false, updatable = false)
  private UUID fieldId;

  @Column(name = "field_type", nullable = false, updatable = false)
  private String fieldType;

  @Column(nullable = false, updatable = false)
  private String code;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, String> label;

  @Column(nullable = false)
  private boolean inactive;

  @Transient private boolean fresh = true;

  protected CustomFieldOptionEntity() {}

  CustomFieldOptionEntity(UUID id, UUID fieldId, String fieldType, String code) {
    this.id = id;
    this.fieldId = fieldId;
    this.fieldType = fieldType;
    this.code = code;
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

  String code() {
    return code;
  }

  Map<String, String> label() {
    return label;
  }

  boolean inactive() {
    return inactive;
  }

  void apply(Map<String, String> label, boolean inactive) {
    this.label = label;
    this.inactive = inactive;
  }
}
