// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.BookingStatistics.CancellationBucket;
import gal.furar.booking.BookingStatistics.FacilityMoney;
import gal.furar.booking.BookingStatistics.PeopleBucket;
import gal.furar.booking.BookingStatistics.UseBucket;
import gal.furar.booking.BookingStatistics.UseDay;
import gal.furar.booking.BookingStatistics.UseWeekdayHour;
import gal.furar.booking.StatsUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Os agregados das reservas do concello actual en SQL (plan do dominio 3, §1.2.2): só contas,
 * minutos e céntimos. Falla sen concello ou sen transacción (ADR-0006).
 */
public interface BookingStatsRepository {

  List<UseDay> perUseAndDay(StatsWindow window);

  List<UseWeekdayHour> perUseWeekdayHour(StatsWindow window);

  List<UseBucket> perBucket(StatsWindow window, StatsUnit unit);

  List<PeopleBucket> peoplePerBucket(StatsWindow window, StatsUnit unit);

  long people(StatsWindow window);

  List<CancellationBucket> cancellations(StatsWindow window, StatsUnit unit);

  List<FacilityMoney> money(StatsWindow window);

  Set<UUID> useIds(StatsWindow window);
}
