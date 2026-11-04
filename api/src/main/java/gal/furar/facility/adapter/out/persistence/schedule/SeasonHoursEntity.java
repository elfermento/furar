// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

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

/** Táboa {@code season_hours}: un tramo dun día; substitúense todos en cada gardado da semana. */
@Entity
@Table(name = "season_hours")
public class SeasonHoursEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "season_id", nullable = false, updatable = false)
  private UUID seasonId;

  @Column(nullable = false, updatable = false)
  private int day;

  @Column(name = "start_minute", nullable = false, updatable = false)
  private int startMinute;

  @Column(name = "end_minute", nullable = false, updatable = false)
  private int endMinute;

  @Transient private boolean fresh;

  protected SeasonHoursEntity() {}

  SeasonHoursEntity(UUID id, UUID seasonId, int day, int startMinute, int endMinute) {
    this.id = id;
    this.seasonId = seasonId;
    this.day = day;
    this.startMinute = startMinute;
    this.endMinute = endMinute;
    this.fresh = true;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return fresh;
  }

  @PostLoad
  @PrePersist
  void stored() {
    fresh = false;
  }

  UUID seasonId() {
    return seasonId;
  }

  int day() {
    return day;
  }

  int startMinute() {
    return startMinute;
  }

  int endMinute() {
    return endMinute;
  }
}
