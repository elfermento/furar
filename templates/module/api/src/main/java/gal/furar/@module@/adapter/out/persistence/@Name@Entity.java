// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/** Táboa {@code @name_snake@}. Concello automático con {@code @TenantId} (A7, ADR-0006). */
@Entity
@Table(name = "@name_snake@")
public class @Name@Entity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private @Nullable Long version;

  protected @Name@Entity() {}

  @Name@Entity(UUID id, Instant createdAt) {
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
  void apply(String name, Instant updatedAt) {
    this.name = name;
    this.updatedAt = updatedAt;
  }
}
