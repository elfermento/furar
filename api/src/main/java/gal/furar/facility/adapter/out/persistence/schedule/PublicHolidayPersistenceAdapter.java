// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import gal.furar.facility.application.port.out.schedule.PublicHolidayRepository;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Lector do catálogo de festivos. Herda da base polo paquete (A7b), pero non pide concello: a táboa
 * é da plataforma e lese tamén sen sesión ({@code GET /api/v1/holidays}).
 */
@Component
class PublicHolidayPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements PublicHolidayRepository {

  private final PublicHolidayJpaRepository holidays;

  PublicHolidayPersistenceAdapter(PublicHolidayJpaRepository holidays) {
    this.holidays = holidays;
  }

  @Override
  public List<PublicHoliday> findBetween(LocalDate from, LocalDate to) {
    return holidays.findByDateBetweenOrderByDateAsc(from, to).stream()
        .map(h -> new PublicHoliday(h.date(), h.name().get("gl"), h.name().get("es"), h.source()))
        .toList();
  }
}
