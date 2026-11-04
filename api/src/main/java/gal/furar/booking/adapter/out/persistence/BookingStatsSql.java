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
import gal.furar.booking.application.port.out.StatsWindow;
import gal.furar.shared.persistence.NativeSql;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Os agregados dos indicadores en SQL (ADR-0026; plan do dominio 3, §1.2.2–§1.2.4). Cada reserva
 * conta no día do seu inicio en Europe/Madrid; días, semanas (de luns), meses, trimestres e anos
 * saen de {@code date_trunc} sobre a hora local. Os filtros opcionais van como {@code (:all or …)}
 * e as categorías con {@code count(*) filter (where …)}, así o nivel 0 é unha conxunción co
 * concello (A8b, {@code BookingStatsSqlTenantIT}). Só saen contas, minutos e céntimos: {@code
 * holder_subject} aparece só dentro de {@code count(distinct …)}. Os minutos son a duración real do
 * intervalo ({@code upper − lower}), tamén nos días do cambio de hora. Cada sentenza vai enteira
 * nun literal (A8b); usa o índice {@code booking_start_idx}.
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingStatsSql {

  /** Ningún valor real: o recheo das listas dun filtro que non filtra. */
  private static final UUID NONE = new UUID(0, 0);

  private final JdbcClient jdbc;

  BookingStatsSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Confirmadas e os seus minutos por uso, instalación e día. */
  List<UseDay> perUseAndDay(UUID municipality, StatsWindow window) {
    return filtered(
            jdbc.sql(
                """
                select use_id, facility_id,
                       (lower(period) at time zone 'Europe/Madrid')::date as day,
                       count(*) as bookings,
                       (sum(date_part('epoch', upper(period) - lower(period))) / 60)::bigint
                         as minutes
                from booking
                where status = 'CONFIRMED'
                  and lower(period) >= :since and lower(period) < :until
                  and (:allFacilities or facility_id = :facility)
                  and (:allUses or use_id in (:uses))
                  and municipality_id = :tenant
                group by 1, 2, 3
                order by 3, 1
                """),
            municipality,
            window)
        .query(
            (rs, n) ->
                new UseDay(
                    rs.getObject("use_id", UUID.class),
                    rs.getObject("facility_id", UUID.class),
                    rs.getObject("day", LocalDate.class),
                    rs.getLong("bookings"),
                    rs.getLong("minutes")))
        .list();
  }

  /**
   * Minutos confirmados por uso, día ISO e hora local. Cada reserva pártese nas horas que pisa
   * ({@code generate_series} desde a hora local do inicio); os desprazamentos de Europe/Madrid son
   * de horas enteiras, así que sumar unha hora real dá a hora local seguinte. A partición vai nunha
   * subconsulta (o concello, dentro e fóra) e sen {@code join lateral} nin {@code extract(… from
   * …)}: A8b e D14 len como táboa o que vai despois de {@code from} e {@code join}.
   */
  List<UseWeekdayHour> perUseWeekdayHour(UUID municipality, StatsWindow window) {
    return filtered(
            jdbc.sql(
                """
                select use_id,
                       date_part('isodow', hour_start at time zone 'Europe/Madrid')::int
                         as weekday,
                       date_part('hour', hour_start at time zone 'Europe/Madrid')::int
                         as hour_of_day,
                       (sum(date_part('epoch',
                          least(ends, hour_start + interval '1 hour')
                          - greatest(starts, hour_start))) / 60)::bigint as minutes
                from (select municipality_id, use_id, lower(period) as starts,
                             upper(period) as ends,
                             generate_series(date_trunc('hour', lower(period), 'Europe/Madrid'),
                                             upper(period) - interval '1 microsecond',
                                             interval '1 hour') as hour_start
                      from booking
                      where status = 'CONFIRMED'
                        and lower(period) >= :since and lower(period) < :until
                        and (:allFacilities or facility_id = :facility)
                        and (:allUses or use_id in (:uses))
                        and municipality_id = :tenant) as slices
                where municipality_id = :tenant
                group by 1, 2, 3
                order by 1, 2, 3
                """),
            municipality,
            window)
        .query(
            (rs, n) ->
                new UseWeekdayHour(
                    rs.getObject("use_id", UUID.class),
                    rs.getInt("weekday"),
                    rs.getInt("hour_of_day"),
                    rs.getLong("minutes")))
        .list();
  }

  /** Por período e uso: confirmadas, os seus minutos e o pagado (calquera estado). */
  List<UseBucket> perBucket(UUID municipality, StatsWindow window, StatsUnit unit) {
    return filtered(
            jdbc.sql(
                """
                select date_trunc(:unit, lower(period) at time zone 'Europe/Madrid')::date
                         as bucket_start,
                       use_id,
                       count(*) filter (where status = 'CONFIRMED') as bookings,
                       coalesce((sum(date_part('epoch', upper(period) - lower(period)))
                         filter (where status = 'CONFIRMED') / 60)::bigint, 0) as minutes,
                       coalesce(sum(amount_cents) filter (where payment_status = 'PAID'), 0)
                         as paid_cents
                from booking
                where lower(period) >= :since and lower(period) < :until
                  and (:allFacilities or facility_id = :facility)
                  and (:allUses or use_id in (:uses))
                  and municipality_id = :tenant
                group by 1, 2
                order by 1, 2
                """),
            municipality,
            window)
        .param("unit", unit(unit))
        .query(
            (rs, n) ->
                new UseBucket(
                    rs.getObject("bucket_start", LocalDate.class),
                    rs.getObject("use_id", UUID.class),
                    rs.getLong("bookings"),
                    rs.getLong("minutes"),
                    rs.getLong("paid_cents")))
        .list();
  }

  /** Persoas distintas con confirmadas en cada período. */
  List<PeopleBucket> peoplePerBucket(UUID municipality, StatsWindow window, StatsUnit unit) {
    return filtered(
            jdbc.sql(
                """
                select date_trunc(:unit, lower(period) at time zone 'Europe/Madrid')::date
                         as bucket_start,
                       count(distinct holder_subject) as people
                from booking
                where status = 'CONFIRMED'
                  and lower(period) >= :since and lower(period) < :until
                  and (:allFacilities or facility_id = :facility)
                  and (:allUses or use_id in (:uses))
                  and municipality_id = :tenant
                group by 1
                order by 1
                """),
            municipality,
            window)
        .param("unit", unit(unit))
        .query(
            (rs, n) ->
                new PeopleBucket(
                    rs.getObject("bucket_start", LocalDate.class), rs.getLong("people")))
        .list();
  }

  /** Persoas distintas con confirmadas en toda a xanela. */
  long people(UUID municipality, StatsWindow window) {
    return filtered(
            jdbc.sql(
                """
                select count(distinct holder_subject)
                from booking
                where status = 'CONFIRMED'
                  and lower(period) >= :since and lower(period) < :until
                  and (:allFacilities or facility_id = :facility)
                  and (:allUses or use_id in (:uses))
                  and municipality_id = :tenant
                """),
            municipality,
            window)
        .query(Long.class)
        .single();
  }

  /**
   * Canceladas por período: pola persoa en prazo ({@code cancelled_at < cancel_until}) e fóra de
   * prazo, e polo concello.
   */
  List<CancellationBucket> cancellations(UUID municipality, StatsWindow window, StatsUnit unit) {
    return filtered(
            jdbc.sql(
                """
                select date_trunc(:unit, lower(period) at time zone 'Europe/Madrid')::date
                         as bucket_start,
                       count(*) filter (where status = 'CANCELLED_BY_PERSON'
                                          and cancelled_at < cancel_until) as in_time,
                       count(*) filter (where status = 'CANCELLED_BY_PERSON'
                                          and cancelled_at >= cancel_until) as late,
                       count(*) filter (where status = 'CANCELLED_BY_MUNICIPALITY')
                         as by_municipality
                from booking
                where status in ('CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY')
                  and lower(period) >= :since and lower(period) < :until
                  and (:allFacilities or facility_id = :facility)
                  and (:allUses or use_id in (:uses))
                  and municipality_id = :tenant
                group by 1
                order by 1
                """),
            municipality,
            window)
        .param("unit", unit(unit))
        .query(
            (rs, n) ->
                new CancellationBucket(
                    rs.getObject("bucket_start", LocalDate.class),
                    rs.getLong("in_time"),
                    rs.getLong("late"),
                    rs.getLong("by_municipality")))
        .list();
  }

  /**
   * Os cobros das reservas de pago por instalación. O pendente, só das confirmadas: unha cancelada
   * sen pagar xa non se cobrará.
   */
  List<FacilityMoney> money(UUID municipality, StatsWindow window) {
    return filtered(
            jdbc.sql(
                """
                select facility_id,
                       coalesce(sum(amount_cents) filter (where payment_status = 'PAID'), 0)
                         as paid_cents,
                       coalesce(sum(amount_cents) filter (where payment_status = 'PENDING'
                                                            and status = 'CONFIRMED'), 0)
                         as pending_cents,
                       coalesce(sum(amount_cents) filter (where payment_status = 'TO_REFUND'), 0)
                         as to_refund_cents,
                       coalesce(sum(amount_cents) filter (where payment_status = 'REFUNDED'), 0)
                         as refunded_cents
                from booking
                where price_kind = 'PAID'
                  and lower(period) >= :since and lower(period) < :until
                  and (:allFacilities or facility_id = :facility)
                  and (:allUses or use_id in (:uses))
                  and municipality_id = :tenant
                group by 1
                order by 1
                """),
            municipality,
            window)
        .query(
            (rs, n) ->
                new FacilityMoney(
                    rs.getObject("facility_id", UUID.class),
                    rs.getLong("paid_cents"),
                    rs.getLong("pending_cents"),
                    rs.getLong("to_refund_cents"),
                    rs.getLong("refunded_cents")))
        .list();
  }

  /** Os usos con algunha reserva, calquera que sexa o seu estado. */
  Set<UUID> useIds(UUID municipality, StatsWindow window) {
    return new LinkedHashSet<>(
        filtered(
                jdbc.sql(
                    """
                    select distinct use_id
                    from booking
                    where lower(period) >= :since and lower(period) < :until
                      and (:allFacilities or facility_id = :facility)
                      and (:allUses or use_id in (:uses))
                      and municipality_id = :tenant
                    order by use_id
                    """),
                municipality,
                window)
            .query(UUID.class)
            .list());
  }

  /** O campo de {@code date_trunc}: {@code day}, {@code week} (luns), {@code month}… */
  static String unit(StatsUnit unit) {
    return unit.name().toLowerCase(Locale.ROOT);
  }

  /** A xanela, os filtros (cada un coa súa bandeira e un recheo se non filtra) e o concello. */
  private static JdbcClient.StatementSpec filtered(
      JdbcClient.StatementSpec spec, UUID municipality, StatsWindow window) {
    return spec.param("since", BookingSql.utc(window.since()))
        .param("until", BookingSql.utc(window.until()))
        .param("allFacilities", window.facilityId() == null)
        .param("facility", window.facilityId() == null ? NONE : window.facilityId())
        .param("allUses", window.useIds().isEmpty())
        .param("uses", window.useIds().isEmpty() ? List.of(NONE) : List.copyOf(window.useIds()))
        .param("tenant", municipality);
  }
}
