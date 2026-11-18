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
 * Táboa {@code drawing}: debuxos de Furar, catálogo da plataforma igual para todos os concellos e
 * sen {@code municipality_id} (ADR-0006, {@code TenancyAllowlist}). Só lectura: a API non ten
 * permiso de escritura nela (migración {@code facility_image_library}).
 */
@Entity
@Immutable
@Table(name = "drawing")
public class DrawingEntity {

  @Id private String code;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, String> name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, String> alt;

  @Column(nullable = false)
  private int width;

  @Column(nullable = false)
  private int height;

  protected DrawingEntity() {}

  String code() {
    return code;
  }

  Map<String, String> name() {
    return name;
  }

  Map<String, String> alt() {
    return alt;
  }

  int width() {
    return width;
  }

  int height() {
    return height;
  }
}
