// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.BookingStatistics.CancellationBucket;
import gal.furar.booking.BookingStatistics.FacilityMoney;
import gal.furar.booking.BookingStatistics.PeopleBucket;
import gal.furar.booking.BookingStatistics.UseBucket;
import gal.furar.booking.BookingStatistics.UseDay;
import gal.furar.booking.BookingStatistics.UseWeekdayHour;
import gal.furar.booking.StatsUnit;
import gal.furar.booking.application.port.out.BookingStatsRepository;
import gal.furar.booking.application.port.out.StatsWindow;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Os agregados das reservas no concello actual: falla sen concello ou sen transacción. */
@Component
class BookingStatsPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements BookingStatsRepository {

  private final BookingStatsSql sql;

  BookingStatsPersistenceAdapter(BookingStatsSql sql) {
    this.sql = sql;
  }

  @Override
  public List<UseDay> perUseAndDay(StatsWindow window) {
    return sql.perUseAndDay(tenant().id(), window);
  }

  @Override
  public List<UseWeekdayHour> perUseWeekdayHour(StatsWindow window) {
    return sql.perUseWeekdayHour(tenant().id(), window);
  }

  @Override
  public List<UseBucket> perBucket(StatsWindow window, StatsUnit unit) {
    return sql.perBucket(tenant().id(), window, unit);
  }

  @Override
  public List<PeopleBucket> peoplePerBucket(StatsWindow window, StatsUnit unit) {
    return sql.peoplePerBucket(tenant().id(), window, unit);
  }

  @Override
  public long people(StatsWindow window) {
    return sql.people(tenant().id(), window);
  }

  @Override
  public List<CancellationBucket> cancellations(StatsWindow window, StatsUnit unit) {
    return sql.cancellations(tenant().id(), window, unit);
  }

  @Override
  public List<FacilityMoney> money(StatsWindow window) {
    return sql.money(tenant().id(), window);
  }

  @Override
  public Set<UUID> useIds(StatsWindow window) {
    return sql.useIds(tenant().id(), window);
  }
}
