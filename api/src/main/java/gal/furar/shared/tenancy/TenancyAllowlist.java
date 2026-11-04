// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.Set;

/**
 * Excepcións pechadas á regra «toda táboa e entidade leva {@code municipality_id}» (ADR-0006).
 * Úsana a regra A7 e {@code SchemaTenancyIT}. Engadir algo aquí é unha decisión que se xustifica no
 * PR.
 */
public final class TenancyAllowlist {

  /** Entidades JPA sen {@code @TenantId}. */
  public static final Set<String> ENTITIES =
      Set.of(
          "gal.furar.municipality.adapter.out.persistence.MunicipalityEntity",
          // Catálogo de plataforma (ADR-0006): igual para todos os concellos e só de lectura.
          "gal.furar.facility.adapter.out.persistence.SportCatalogEntity",
          // Debuxos de Furar (ADR-0006, biblioteca de imaxes): catálogo de plataforma, só lectura.
          "gal.furar.facility.adapter.out.persistence.DrawingEntity",
          // Festivos de Galicia (ADR-0006, D1-15a): catálogo de plataforma, só de lectura.
          "gal.furar.facility.adapter.out.persistence.schedule.PublicHolidayEntity");

  /** Táboas sen {@code municipality_id}. */
  public static final Set<String> TABLES =
      Set.of(
          "municipality",
          "flyway_schema_history",
          "event_publication",
          "audit_event",
          // Administración da plataforma (ADR-0028): de toda a instalación, non dun concello.
          "platform_admin",
          // Catálogo de plataforma (ADR-0006, D1-11b): revoke de escritura a furar_app.
          "sport_catalog",
          // Debuxos de Furar (ADR-0006, biblioteca de imaxes): revoke de escritura a furar_app.
          "drawing",
          // Festivos de Galicia (ADR-0006, D1-15a): revoke de escritura a furar_app.
          "public_holiday");

  private TenancyAllowlist() {}
}
