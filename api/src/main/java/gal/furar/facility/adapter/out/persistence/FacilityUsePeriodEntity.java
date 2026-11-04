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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.springframework.data.domain.Persistable;

/** Táboa {@code facility_use_period}: substitúense enteiros en cada gardado do uso. */
@Entity
@Table(name = "facility_use_period")
public class FacilityUsePeriodEntity implements Persistable<UUID> {

  @Id private UUID id;

  @TenantId
  @Column(name = "municipality_id", nullable = false, updatable = false)
  private UUID municipalityId;

  @Column(name = "use_id", nullable = false, updatable = false)
  private UUID useId;

  @Column(name = "from_date", nullable = false)
  private LocalDate fromDate;

  @Column(name = "to_date", nullable = false)
  private LocalDate toDate;

  @Transient private boolean fresh;

  protected FacilityUsePeriodEntity() {}

  FacilityUsePeriodEntity(UUID id, UUID useId, LocalDate fromDate, LocalDate toDate) {
    this.id = id;
    this.useId = useId;
    this.fromDate = fromDate;
    this.toDate = toDate;
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

  UUID useId() {
    return useId;
  }

  LocalDate fromDate() {
    return fromDate;
  }

  LocalDate toDate() {
    return toDate;
  }
}
