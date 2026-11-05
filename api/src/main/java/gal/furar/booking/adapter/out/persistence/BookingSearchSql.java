// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.BookingFilter;
import gal.furar.booking.application.port.out.StartCount;
import gal.furar.booking.domain.Booking;
import gal.furar.shared.persistence.NativeSql;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As lecturas de xestión das reservas en SQL (plan do dominio 2, §2.2; maquetas 26 e 27): a lista
 * con filtros e o calendario. Os filtros opcionais van dentro de parénteses cunha bandeira ({@code
 * (:all or …)}), así cada sentenza é unha soa e o concello queda no nivel 0 (A8b, {@code
 * BookingSearchSqlTenantIT}). O nome e o correo compáranse en minúsculas e sen acentos; o texto que
 * chega xa vén así: {@code translate} quita os acentos (maiúsculas e minúsculas) antes de {@code
 * lower}, que só cambia o ASCII en calquera colación. Cada sentenza vai enteira nun literal (A8b).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingSearchSql {

  /** Ningún valor real: o recheo das listas dun filtro que non filtra. */
  private static final UUID NONE = new UUID(0, 0);

  private static final String NOTHING = "-";

  /** Calquera instante: o recheo dunha data que non filtra. */
  private static final Instant ANY = Instant.EPOCH;

  private final JdbcClient jdbc;

  BookingSearchSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Unha páxina, das máis recentes ás máis antigas (cando se reservaron). */
  List<Booking> recent(UUID municipality, BookingFilter filter, int limit, long offset) {
    return filtered(
            jdbc.sql(
                """
                select id, code, facility_id, use_id, part_id, rental_mode,
                       lower(period) as start_at, upper(period) as end_at, slots, status,
                       payment_status, price_kind,
                       amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                       rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                       holder_subject, holder_name, holder_email, holder_phone, holder_language,
                   booked_by, cancelled_at,
                       cancelled_by, created_at, updated_at, version
                from booking
                where (:anySince or lower(period) >= :since)
                  and (:anyUntil or lower(period) < :until)
                  and (:allFacilities or facility_id in (:facilities))
                  and (:allPayments or payment_status in (:payments))
                  and (:allStatuses or status in (:statuses))
                  and (:anyText
                       or lower(translate(holder_name,
                            'áàâäãéèêëíìîïóòôöõúùûüñçÁÀÂÄÃÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇ',
                            'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC'))
                         like :pattern escape '!'
                       or lower(translate(holder_email,
                            'áàâäãéèêëíìîïóòôöõúùûüñçÁÀÂÄÃÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇ',
                            'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC'))
                         like :pattern escape '!'
                       or code = :code)
                  and municipality_id = :tenant
                order by created_at desc, id desc
                limit :limit offset :offset
                """),
            municipality,
            filter)
        .param("limit", limit)
        .param("offset", offset)
        .query((rs, n) -> BookingSql.booking(rs))
        .list();
  }

  /** Unha páxina, por día e hora da reserva. */
  List<Booking> byStart(UUID municipality, BookingFilter filter, int limit, long offset) {
    return filtered(
            jdbc.sql(
                """
                select id, code, facility_id, use_id, part_id, rental_mode,
                       lower(period) as start_at, upper(period) as end_at, slots, status,
                       payment_status, price_kind,
                       amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                       rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                       holder_subject, holder_name, holder_email, holder_phone, holder_language,
                   booked_by, cancelled_at,
                       cancelled_by, created_at, updated_at, version
                from booking
                where (:anySince or lower(period) >= :since)
                  and (:anyUntil or lower(period) < :until)
                  and (:allFacilities or facility_id in (:facilities))
                  and (:allPayments or payment_status in (:payments))
                  and (:allStatuses or status in (:statuses))
                  and (:anyText
                       or lower(translate(holder_name,
                            'áàâäãéèêëíìîïóòôöõúùûüñçÁÀÂÄÃÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇ',
                            'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC'))
                         like :pattern escape '!'
                       or lower(translate(holder_email,
                            'áàâäãéèêëíìîïóòôöõúùûüñçÁÀÂÄÃÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇ',
                            'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC'))
                         like :pattern escape '!'
                       or code = :code)
                  and municipality_id = :tenant
                order by lower(period), id
                limit :limit offset :offset
                """),
            municipality,
            filter)
        .param("limit", limit)
        .param("offset", offset)
        .query((rs, n) -> BookingSql.booking(rs))
        .list();
  }

  long count(UUID municipality, BookingFilter filter) {
    return filtered(
            jdbc.sql(
                """
                select count(*)
                from booking
                where (:anySince or lower(period) >= :since)
                  and (:anyUntil or lower(period) < :until)
                  and (:allFacilities or facility_id in (:facilities))
                  and (:allPayments or payment_status in (:payments))
                  and (:allStatuses or status in (:statuses))
                  and (:anyText
                       or lower(translate(holder_name,
                            'áàâäãéèêëíìîïóòôöõúùûüñçÁÀÂÄÃÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇ',
                            'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC'))
                         like :pattern escape '!'
                       or lower(translate(holder_email,
                            'áàâäãéèêëíìîïóòôöõúùûüñçÁÀÂÄÃÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇ',
                            'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC'))
                         like :pattern escape '!'
                       or code = :code)
                  and municipality_id = :tenant
                """),
            municipality,
            filter)
        .query(Long.class)
        .single();
  }

  /** As confirmadas que empezan en {@code [from, to)}, por día e hora. */
  List<Booking> confirmedStarting(
      UUID municipality, Instant from, Instant to, @Nullable UUID facilityId) {
    return jdbc.sql(
            """
            select id, code, facility_id, use_id, part_id, rental_mode, lower(period) as start_at,
                   upper(period) as end_at, slots, status, payment_status, price_kind,
                   amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                   rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                   holder_subject, holder_name, holder_email, holder_phone, holder_language,
                   booked_by, cancelled_at,
                   cancelled_by, created_at, updated_at, version
            from booking
            where status = 'CONFIRMED' and lower(period) >= :since and lower(period) < :until
            and (:allFacilities or facility_id = :facility)
            and municipality_id = :tenant
            order by lower(period), id
            """)
        .param("since", BookingSql.utc(from))
        .param("until", BookingSql.utc(to))
        .param("allFacilities", facilityId == null)
        .param("facility", facilityId == null ? NONE : facilityId)
        .param("tenant", municipality)
        .query((rs, n) -> BookingSql.booking(rs))
        .list();
  }

  /** Cantas confirmadas empezan cada día en {@code [from, to)} (Europe/Madrid). */
  List<StartCount> perDay(UUID municipality, Instant from, Instant to, @Nullable UUID facilityId) {
    return jdbc.sql(
            """
            select (lower(period) at time zone 'Europe/Madrid')::date as day, count(*) as total
            from booking
            where status = 'CONFIRMED' and lower(period) >= :since and lower(period) < :until
              and (:allFacilities or facility_id = :facility)
              and municipality_id = :tenant
            group by 1
            order by 1
            """)
        .param("since", BookingSql.utc(from))
        .param("until", BookingSql.utc(to))
        .param("allFacilities", facilityId == null)
        .param("facility", facilityId == null ? NONE : facilityId)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new StartCount(rs.getObject("day", LocalDate.class), null, rs.getLong("total")))
        .list();
  }

  /** Cantas confirmadas empezan cada día e hora en {@code [from, to)} (Europe/Madrid). */
  List<StartCount> perHour(UUID municipality, Instant from, Instant to, @Nullable UUID facilityId) {
    return jdbc.sql(
            """
            select (lower(period) at time zone 'Europe/Madrid')::date as day,
                   date_part('hour', lower(period) at time zone 'Europe/Madrid')::int as hour,
                   count(*) as total
            from booking
            where status = 'CONFIRMED' and lower(period) >= :since and lower(period) < :until
              and (:allFacilities or facility_id = :facility)
              and municipality_id = :tenant
            group by 1, 2
            order by 1, 2
            """)
        .param("since", BookingSql.utc(from))
        .param("until", BookingSql.utc(to))
        .param("allFacilities", facilityId == null)
        .param("facility", facilityId == null ? NONE : facilityId)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new StartCount(
                    rs.getObject("day", LocalDate.class), rs.getInt("hour"), rs.getLong("total")))
        .list();
  }

  /** Os parámetros dos filtros: cada un coa súa bandeira e un recheo se non filtra. */
  private static JdbcClient.StatementSpec filtered(
      JdbcClient.StatementSpec spec, UUID municipality, BookingFilter filter) {
    boolean anyText = filter.name() == null && filter.code() == null;
    return spec.param("anySince", filter.since() == null)
        .param("since", BookingSql.utc(filter.since() == null ? ANY : filter.since()))
        .param("anyUntil", filter.until() == null)
        .param("until", BookingSql.utc(filter.until() == null ? ANY : filter.until()))
        .param("allFacilities", filter.facilityIds().isEmpty())
        .param("facilities", orNone(filter.facilityIds(), Function.identity(), NONE))
        .param("allPayments", filter.payments().isEmpty())
        .param("payments", orNone(filter.payments(), Enum::name, NOTHING))
        .param("allStatuses", filter.statuses().isEmpty())
        .param("statuses", orNone(filter.statuses(), Enum::name, NOTHING))
        .param("anyText", anyText)
        .param("pattern", filter.name() == null ? NOTHING : "%" + escaped(filter.name()) + "%")
        .param("code", filter.code() == null ? NOTHING : filter.code(), Types.VARCHAR)
        .param("tenant", municipality);
  }

  /** A lista para {@code in (…)}; nunca baleira, que en SQL non vale. */
  private static <T, R> List<R> orNone(Collection<T> values, Function<T, R> map, R none) {
    return values.isEmpty() ? List.of(none) : values.stream().map(map).toList();
  }

  /** Os comodíns de {@code like} como texto, co escape {@code !}. */
  static String escaped(String text) {
    return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
