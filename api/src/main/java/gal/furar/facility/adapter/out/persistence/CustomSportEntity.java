// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** Táboa {@code custom_sport}: deporte creado polo concello con «+ crear». */
@Entity
@Immutable
@Table(name = "custom_sport")
public class CustomSportEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, String> label;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Transient private boolean fresh = true;

  protected CustomSportEntity() {}

  CustomSportEntity(UUID id, Map<String, String> label, Instant createdAt) {
    this.id = id;
    this.label = label;
    this.createdAt = createdAt;
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

  Map<String, String> label() {
    return label;
  }

  Instant createdAt() {
    return createdAt;
  }
}
