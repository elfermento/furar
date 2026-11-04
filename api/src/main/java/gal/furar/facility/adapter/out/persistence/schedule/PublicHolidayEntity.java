// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Táboa {@code public_holiday}: festivos de Galicia, iguais para todos os concellos e sen {@code
 * municipality_id} (ADR-0006, {@code TenancyAllowlist}). Só lectura: a API non ten permiso de
 * escritura nela (migración {@code facility_seasons}).
 */
@Entity
@Immutable
@Table(name = "public_holiday")
public class PublicHolidayEntity {

  @Id
  @Column(name = "date")
  private LocalDate date;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, String> name;

  @Column(nullable = false)
  private String source;

  protected PublicHolidayEntity() {}

  LocalDate date() {
    return date;
  }

  Map<String, String> name() {
    return name;
  }

  String source() {
    return source;
  }
}
