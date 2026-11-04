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
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Táboa {@code facility_use_part}. Sen versión propia: vai sempre co seu uso, que é o que leva o
 * {@code If-Match}. Ao quitala dun uso arquívase.
 */
@Entity
@Table(name = "facility_use_part")
public class FacilityUsePartEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "use_id", nullable = false, updatable = false)
  private UUID useId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private int position;

  @Column(name = "archived_at")
  private @Nullable Instant archivedAt;

  @Transient private boolean fresh;

  protected FacilityUsePartEntity() {}

  FacilityUsePartEntity(UUID id, UUID useId, String name, int position) {
    this.id = id;
    this.useId = useId;
    this.name = name;
    this.position = position;
    this.fresh = true;
  }

  @Override
  public UUID getId() {
    return id;
  }

  /** Id asignado polo dominio: sen isto, {@code save} faría un {@code merge} cun SELECT antes. */
  @Override
  public boolean isNew() {
    return fresh;
  }

  @PostLoad
  @PrePersist
  void stored() {
    fresh = false;
  }

  UUID useId() {
    return useId;
  }

  String name() {
    return name;
  }

  int position() {
    return position;
  }

  @Nullable Instant archivedAt() {
    return archivedAt;
  }

  void apply(String name, int position) {
    this.name = name;
    this.position = position;
  }

  void archive(Instant when) {
    this.archivedAt = when;
  }
}
