// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Táboa {@code sport_catalog}: catálogo de deportes da plataforma, igual para todos os concellos e
 * sen {@code municipality_id} (ADR-0006, {@code TenancyAllowlist}). Só lectura: a API non ten
 * permiso de escritura nela (migración {@code facility_data}).
 */
@Entity
@Immutable
@Table(name = "sport_catalog")
public class SportCatalogEntity {

  @Id private String code;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, String> label;

  protected SportCatalogEntity() {}

  String code() {
    return code;
  }

  Map<String, String> label() {
    return label;
  }
}
