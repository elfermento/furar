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
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code custom_field_usage}: onde se usa un campo. {@code personal_data} é copia da do campo
 * e a base mantena ({@code on update cascade}); por iso non se escribe ao actualizar.
 */
@Entity
@Table(name = "custom_field_usage")
public class CustomFieldUsageEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "field_id", nullable = false, updatable = false)
  private UUID fieldId;

  @Column(nullable = false, updatable = false)
  private String target;

  @Column(nullable = false)
  private boolean required;

  @Column(nullable = false)
  private String visibility;

  @Column(name = "personal_data", nullable = false, updatable = false)
  private boolean personalData;

  @Transient private boolean fresh = true;

  protected CustomFieldUsageEntity() {}

  CustomFieldUsageEntity(UUID id, UUID fieldId, String target, boolean personalData) {
    this.id = id;
    this.fieldId = fieldId;
    this.target = target;
    this.personalData = personalData;
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

  String target() {
    return target;
  }

  boolean required() {
    return required;
  }

  String visibility() {
    return visibility;
  }

  void apply(boolean required, String visibility) {
    this.required = required;
    this.visibility = visibility;
  }
}
