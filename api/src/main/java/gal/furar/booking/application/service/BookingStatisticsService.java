// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingStatistics;
import gal.furar.booking.StatsQuery;
import gal.furar.booking.StatsUnit;
import gal.furar.booking.application.port.out.BookingStatsRepository;
import gal.furar.booking.application.port.out.StatsWindow;
import gal.furar.booking.domain.BookingRules;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa {@link BookingStatistics}: pasa os días do filtro a instantes en Europe/Madrid ({@code
 * [from 00:00, to+1 00:00)}, así os días de 23 e de 25 horas quedan enteiros) e le os agregados.
 * Sempre no concello actual; sen permisos propios porque non é un caso de uso.
 */
@Service
@Transactional(readOnly = true)
class BookingStatisticsService implements BookingStatistics {

  private final BookingStatsRepository repository;

  BookingStatisticsService(BookingStatsRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<UseDay> perUseAndDay(StatsQuery query) {
    return repository.perUseAndDay(window(query));
  }

  @Override
  public List<UseWeekdayHour> perUseWeekdayHour(StatsQuery query) {
    return repository.perUseWeekdayHour(window(query));
  }

  @Override
  public List<UseBucket> perBucket(StatsQuery query, StatsUnit unit) {
    return repository.perBucket(window(query), unit);
  }

  @Override
  public List<PeopleBucket> peoplePerBucket(StatsQuery query, StatsUnit unit) {
    return repository.peoplePerBucket(window(query), unit);
  }

  @Override
  public long people(StatsQuery query) {
    return repository.people(window(query));
  }

  @Override
  public List<CancellationBucket> cancellations(StatsQuery query, StatsUnit unit) {
    return repository.cancellations(window(query), unit);
  }

  @Override
  public List<FacilityMoney> money(StatsQuery query) {
    return repository.money(window(query));
  }

  @Override
  public Set<UUID> useIds(StatsQuery query) {
    return repository.useIds(window(query));
  }

  static StatsWindow window(StatsQuery query) {
    return new StatsWindow(
        query.from().atStartOfDay(BookingRules.ZONE).toInstant(),
        query.to().plusDays(1).atStartOfDay(BookingRules.ZONE).toInstant(),
        query.facilityId(),
        query.useIds());
  }
}
