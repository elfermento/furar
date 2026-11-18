// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.RentalMode;
import gal.furar.booking.domain.season.Series;
import gal.furar.booking.domain.season.SeriesPlanner;
import gal.furar.booking.domain.season.SeriesSchedule;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As tempadas e as súas sesións en SQL (ADR-0039, puntos 4, 8 e 11). As sesións escríbense e
 * cancélanse en lote (unha sentenza por táboa, con {@code unnest}): tres viaxes á base para centos
 * de sesións. Sempre dentro dunha transacción (RLS) e co concello como parámetro (A8b, {@code
 * SeriesSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class SeriesSql {

  private final JdbcClient jdbc;

  SeriesSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Series> find(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, organization_id, facility_id, use_id, part_id, rental_mode,
                   array_to_string(weekdays, ',') as days, start_minute, end_minute, first_date,
                   last_date, ended_from, payment_status, request_id, idempotency_key,
                   idempotency_fingerprint, created_by, created_at, updated_at, version
            from booking_series
            where id = :id and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> series(rs))
        .optional();
  }

  Optional<Series> findForUpdate(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, organization_id, facility_id, use_id, part_id, rental_mode,
                   array_to_string(weekdays, ',') as days, start_minute, end_minute, first_date,
                   last_date, ended_from, payment_status, request_id, idempotency_key,
                   idempotency_fingerprint, created_by, created_at, updated_at, version
            from booking_series
            where id = :id and municipality_id = :tenant
            for update
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> series(rs))
        .optional();
  }

  Optional<Series> findByKey(UUID municipality, String key) {
    return jdbc.sql(
            """
            select id, organization_id, facility_id, use_id, part_id, rental_mode,
                   array_to_string(weekdays, ',') as days, start_minute, end_minute, first_date,
                   last_date, ended_from, payment_status, request_id, idempotency_key,
                   idempotency_fingerprint, created_by, created_at, updated_at, version
            from booking_series
            where idempotency_key = :key and municipality_id = :tenant
            """)
        .param("key", key)
        .param("tenant", municipality)
        .query((rs, n) -> series(rs))
        .optional();
  }

  List<Series> list(UUID municipality, boolean ended, LocalDate today) {
    return jdbc.sql(
            """
            select id, organization_id, facility_id, use_id, part_id, rental_mode,
                   array_to_string(weekdays, ',') as days, start_minute, end_minute, first_date,
                   last_date, ended_from, payment_status, request_id, idempotency_key,
                   idempotency_fingerprint, created_by, created_at, updated_at, version
            from booking_series
            where (ended_from is not null or last_date < :today) = :ended
              and municipality_id = :tenant
            order by first_date, created_at, id
            """)
        .param("today", today)
        .param("ended", ended)
        .param("tenant", municipality)
        .query((rs, n) -> series(rs))
        .list();
  }

  List<Series> ofOrganization(UUID municipality, UUID organization) {
    return jdbc.sql(
            """
            select id, organization_id, facility_id, use_id, part_id, rental_mode,
                   array_to_string(weekdays, ',') as days, start_minute, end_minute, first_date,
                   last_date, ended_from, payment_status, request_id, idempotency_key,
                   idempotency_fingerprint, created_by, created_at, updated_at, version
            from booking_series
            where organization_id = :organization and municipality_id = :tenant
            order by created_at desc, id
            """)
        .param("organization", organization)
        .param("tenant", municipality)
        .query((rs, n) -> series(rs))
        .list();
  }

  /** Por serie: cantas sesións confirmadas e cantas canceladas. */
  List<CountRow> counts(UUID municipality, Collection<UUID> series) {
    if (series.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select series_id,
                   count(*) filter (where status = 'CONFIRMED') as confirmed,
                   count(*) filter (where status <> 'CONFIRMED') as cancelled
            from booking
            where series_id in (:series) and municipality_id = :tenant
            group by series_id
            """)
        .param("series", List.copyOf(series))
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new CountRow(
                    rs.getObject("series_id", UUID.class),
                    rs.getInt("confirmed"),
                    rs.getInt("cancelled")))
        .list();
  }

  record CountRow(UUID seriesId, int confirmed, int cancelled) {}

  List<LocalDate> sessionDates(UUID municipality, UUID series) {
    return jdbc.sql(
            """
            select distinct (lower(period) at time zone 'Europe/Madrid')::date as day
            from booking
            where series_id = :series and municipality_id = :tenant
            order by 1
            """)
        .param("series", series)
        .param("tenant", municipality)
        .query((rs, n) -> rs.getObject("day", LocalDate.class))
        .list();
  }

  List<SeriesPlanner.Occupant> occupants(
      UUID municipality, UUID facility, Instant since, Instant until) {
    return jdbc.sql(
            """
            select o.booking_id, o.use_id, o.part_id, lower(o.period) as start_at,
                   upper(o.period) as end_at, b.holder_name, b.series_id, b.activity_id
            from booking_occupancy o
            join booking b on b.id = o.booking_id and b.municipality_id = o.municipality_id
            where o.facility_id = :facility and o.active
              and o.period && tstzrange(:since, :until, '[)')
              and o.municipality_id = :tenant
            order by lower(o.period), o.booking_id
            """)
        .param("facility", facility)
        .param("since", BookingSql.utc(since))
        .param("until", BookingSql.utc(until))
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new SeriesPlanner.Occupant(
                    rs.getObject("booking_id", UUID.class),
                    rs.getObject("use_id", UUID.class),
                    rs.getObject("part_id", UUID.class),
                    BookingSql.instant(rs, "start_at"),
                    BookingSql.instant(rs, "end_at"),
                    rs.getString("holder_name"),
                    rs.getObject("series_id", UUID.class) != null
                        || rs.getObject("activity_id", UUID.class) != null))
        .list();
  }

  List<String> takenCodes(UUID municipality, Collection<String> codes) {
    if (codes.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select code from booking
            where code in (:codes) and municipality_id = :tenant
            """)
        .param("codes", List.copyOf(codes))
        .param("tenant", municipality)
        .query(String.class)
        .list();
  }

  void insert(UUID municipality, Series s) {
    SeriesSchedule h = s.schedule();
    jdbc.sql(
            """
            insert into booking_series
              (id, municipality_id, organization_id, facility_id, use_id, part_id, rental_mode,
               weekdays, start_minute, end_minute, first_date, last_date, ended_from,
               payment_status, request_id, idempotency_key, idempotency_fingerprint, created_by,
               created_at, updated_at, version)
            values (:id, :tenant, :organization, :facility, :use, :part, :mode,
                    cast(:days as smallint[]), :startMinute, :endMinute, :firstDate, :lastDate,
                    null, :payment, :request, :key, :fingerprint, :createdBy, :createdAt,
                    :updatedAt, 0)
            """)
        .param("id", s.id())
        .param("tenant", municipality)
        .param("organization", s.organizationId())
        .param("facility", h.facilityId())
        .param("use", h.useId())
        .param("part", h.partId(), Types.OTHER)
        .param("mode", s.rentalMode().name())
        .param("days", "{" + days(h) + "}")
        .param("startMinute", h.startMinute())
        .param("endMinute", h.endMinute())
        .param("firstDate", h.firstDate())
        .param("lastDate", h.lastDate())
        .param("payment", s.paymentStatus().name())
        .param("request", s.requestId(), Types.OTHER)
        .param("key", s.idempotencyKey())
        .param("fingerprint", s.fingerprint())
        .param("createdBy", s.createdBy())
        .param("createdAt", BookingSql.utc(s.createdAt()))
        .param("updatedAt", BookingSql.utc(s.updatedAt()))
        .update();
  }

  boolean update(UUID municipality, Series s, long expected) {
    return jdbc.sql(
                """
                update booking_series
                set ended_from = :endedFrom, payment_status = :payment, updated_at = :updatedAt,
                    version = version + 1
                where id = :id and version = :version and municipality_id = :tenant
                """)
            .param("endedFrom", s.endedFrom(), Types.DATE)
            .param("payment", s.paymentStatus().name())
            .param("updatedAt", BookingSql.utc(s.updatedAt()))
            .param("id", s.id())
            .param("version", expected)
            .param("tenant", municipality)
            .update()
        == 1;
  }

  /**
   * As filas {@code booking} das sesións dunha serie nunha sentenza (as listas van como texto e
   * desfanse con {@code unnest}). Todas comparten instalación, uso, parte, entidade, serie, quen as
   * creou e cando.
   *
   * @return os ids das que se escribiron; as que faltan tiñan un código que xa existía
   */
  List<UUID> insertBookings(UUID municipality, List<Booking> sessions) {
    if (sessions.isEmpty()) {
      return List.of();
    }
    Booking first = sessions.getFirst();
    return jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, part_id, rental_mode, period,
               slots, status, payment_status, price_kind, cancel_until, holder_name,
               holder_language, booked_by, created_at, updated_at, version, seats,
               organization_id, series_id, activity_id)
            select id, municipality_id, code, cast(:facility as uuid), cast(:use as uuid),
                   cast(:part as uuid), :mode, tstzrange(since, until, '[)'), slots,
                   'CONFIRMED', 'NONE', 'NONE', since, :name, :language, :bookedBy,
                   cast(:createdAt as timestamptz), cast(:createdAt as timestamptz), 0, 1,
                   cast(:organization as uuid), cast(:series as uuid), cast(:activity as uuid)
            from (select cast(:tenant as uuid) as municipality_id,
                         unnest(cast(string_to_array(:ids, ',') as uuid[])) as id,
                         unnest(string_to_array(:codes, ',')) as code,
                         unnest(cast(string_to_array(:starts, ',') as timestamptz[])) as since,
                         unnest(cast(string_to_array(:ends, ',') as timestamptz[])) as until,
                         unnest(cast(string_to_array(:counts, ',') as int[])) as slots) as planned
            where municipality_id = :tenant
            on conflict (municipality_id, code) do nothing
            returning id
            """)
        .param("tenant", municipality)
        .param("facility", first.slot().facilityId())
        .param("use", first.slot().useId())
        .param("part", first.slot().partId(), Types.OTHER)
        .param("mode", first.slot().rentalMode().name())
        .param("name", first.requireHolder().name())
        .param("language", first.requireHolder().language().code())
        .param("bookedBy", first.bookedBy())
        .param("createdAt", BookingSql.utc(first.createdAt()))
        .param("organization", first.organizationId(), Types.OTHER)
        .param("series", first.seriesId(), Types.OTHER)
        .param("activity", first.activityId(), Types.OTHER)
        .param("ids", joined(sessions, b -> b.id().value().toString()))
        .param("codes", joined(sessions, b -> b.code().value()))
        .param("starts", joined(sessions, b -> b.slot().start().toString()))
        .param("ends", joined(sessions, b -> b.slot().end().toString()))
        .param("counts", joined(sessions, b -> Integer.toString(b.slot().slots())))
        .query(UUID.class)
        .list();
  }

  /** A ocupación activa de cada sesión, nunha sentenza. */
  void insertOccupancies(UUID municipality, List<Booking> sessions) {
    if (sessions.isEmpty()) {
      return;
    }
    Booking first = sessions.getFirst();
    jdbc.sql(
            """
            insert into booking_occupancy
              (id, municipality_id, booking_id, facility_id, use_id, part_id, rental_mode,
               period, seats)
            select id, municipality_id, owner, cast(:facility as uuid), cast(:use as uuid),
                   cast(:part as uuid), :mode, tstzrange(since, until, '[)'), 1
            from (select cast(:tenant as uuid) as municipality_id,
                         unnest(cast(string_to_array(:ids, ',') as uuid[])) as id,
                         unnest(cast(string_to_array(:owners, ',') as uuid[])) as owner,
                         unnest(cast(string_to_array(:starts, ',') as timestamptz[])) as since,
                         unnest(cast(string_to_array(:ends, ',') as timestamptz[])) as until)
                 as planned
            where municipality_id = :tenant
            """)
        .param("tenant", municipality)
        .param("facility", first.slot().facilityId())
        .param("use", first.slot().useId())
        .param("part", first.slot().partId(), Types.OTHER)
        .param("mode", first.slot().rentalMode().name())
        .param("ids", joined(sessions, b -> Ids.newV7().toString()))
        .param("owners", joined(sessions, b -> b.id().value().toString()))
        .param("starts", joined(sessions, b -> b.slot().start().toString()))
        .param("ends", joined(sessions, b -> b.slot().end().toString()))
        .update();
  }

  /**
   * A fila da historia de cada reserva, nunha sentenza; nas cancelacións do concello, co aviso por
   * correo.
   */
  void insertEvents(
      UUID municipality, Collection<UUID> bookings, String kind, Instant at, String actor) {
    if (bookings.isEmpty()) {
      return;
    }
    jdbc.sql(
            """
            insert into booking_event
              (id, municipality_id, booking_id, kind, at, actor_subject, channels)
            select id, municipality_id, owner, :kind, cast(:at as timestamptz), :actor,
                   coalesce(string_to_array(nullif(:channels, ''), ','), cast('{}' as text[]))
            from (select cast(:tenant as uuid) as municipality_id,
                         unnest(cast(string_to_array(:ids, ',') as uuid[])) as id,
                         unnest(cast(string_to_array(:owners, ',') as uuid[])) as owner)
                 as planned
            where municipality_id = :tenant
            """)
        .param("tenant", municipality)
        .param("kind", kind)
        .param("at", BookingSql.utc(at))
        .param("actor", actor)
        .param("channels", "CANCELLED_BY_MUNICIPALITY".equals(kind) ? "EMAIL" : "")
        .param("ids", String.join(",", bookings.stream().map(b -> Ids.newV7().toString()).toList()))
        .param("owners", String.join(",", bookings.stream().map(UUID::toString).toList()))
        .update();
  }

  List<Booking> confirmedSessions(UUID municipality, UUID series, Instant after) {
    return jdbc.sql(
            """
            select id, code, facility_id, use_id, part_id, rental_mode, lower(period) as start_at,
                   upper(period) as end_at, slots, seats, status, payment_status, price_kind,
                   amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                   rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                   holder_subject, holder_name, holder_email, holder_phone, holder_language,
                   booked_by, cancelled_at, cancelled_by, created_at, updated_at, version,
                   attendance, attendance_at, attendance_by, organization_id, series_id,
                   activity_id
            from booking
            where series_id = :series and status = 'CONFIRMED' and attendance = 'NONE'
              and lower(period) > :after
              and municipality_id = :tenant
            order by lower(period), id
            """)
        .param("series", series)
        .param("after", BookingSql.utc(after))
        .param("tenant", municipality)
        .query((rs, n) -> BookingSql.booking(rs))
        .list();
  }

  /**
   * As sesións confirmadas e sen marca dunha actividade (ADR-0041), destas reservas, que empezan
   * despois de {@code after}, en orde.
   */
  List<Booking> activitySessions(
      UUID municipality, UUID activity, Collection<UUID> bookings, Instant after) {
    if (bookings.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select id, code, facility_id, use_id, part_id, rental_mode, lower(period) as start_at,
                   upper(period) as end_at, slots, seats, status, payment_status, price_kind,
                   amount_cents, cancel_until, rule_min_advance_hours, rule_max_advance_days,
                   rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                   holder_subject, holder_name, holder_email, holder_phone, holder_language,
                   booked_by, cancelled_at, cancelled_by, created_at, updated_at, version,
                   attendance, attendance_at, attendance_by, organization_id, series_id,
                   activity_id
            from booking
            where activity_id = :activity and id in (:bookings) and status = 'CONFIRMED'
              and attendance = 'NONE' and lower(period) > :after
              and municipality_id = :tenant
            order by lower(period), id
            """)
        .param("activity", activity)
        .param("bookings", List.copyOf(bookings))
        .param("after", BookingSql.utc(after))
        .param("tenant", municipality)
        .query((rs, n) -> BookingSql.booking(rs))
        .list();
  }

  /** O nome da actividade nas súas sesións sen anonimizar (ADR-0041). */
  int renameActivity(UUID municipality, UUID activity, String name) {
    return jdbc.sql(
            """
            update booking set holder_name = :name
            where activity_id = :activity and anonymized_at is null and holder_name <> :name
              and municipality_id = :tenant
            """)
        .param("name", name)
        .param("activity", activity)
        .param("tenant", municipality)
        .update();
  }

  /**
   * O concello cancela esas sesións confirmadas e sen marca, nunha sentenza (versión + 1).
   *
   * @return cantas cancelou
   */
  int cancelBookings(UUID municipality, Collection<UUID> bookings, Instant at, String actor) {
    if (bookings.isEmpty()) {
      return 0;
    }
    return jdbc.sql(
            """
            update booking set status = 'CANCELLED_BY_MUNICIPALITY', cancelled_at = :at,
                               cancelled_by = :actor, updated_at = :at, version = version + 1
            where id in (:bookings) and status = 'CONFIRMED' and attendance = 'NONE'
              and municipality_id = :tenant
            """)
        .param("at", BookingSql.utc(at))
        .param("actor", actor)
        .param("bookings", List.copyOf(bookings))
        .param("tenant", municipality)
        .update();
  }

  int deactivateOccupancies(UUID municipality, Collection<UUID> bookings) {
    if (bookings.isEmpty()) {
      return 0;
    }
    return jdbc.sql(
            """
            update booking_occupancy set active = false
            where booking_id in (:bookings) and active and municipality_id = :tenant
            """)
        .param("bookings", List.copyOf(bookings))
        .param("tenant", municipality)
        .update();
  }

  private static String joined(List<Booking> sessions, Function<Booking, String> value) {
    return sessions.stream().map(value).collect(Collectors.joining(","));
  }

  private static String days(SeriesSchedule h) {
    return h.isoWeekdays().stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  private static Series series(ResultSet rs) throws SQLException {
    List<Integer> days =
        Arrays.stream(rs.getString("days").split(",")).map(Integer::valueOf).toList();
    SeriesSchedule schedule =
        SeriesSchedule.of(
            rs.getObject("facility_id", UUID.class),
            rs.getObject("use_id", UUID.class),
            rs.getObject("part_id", UUID.class),
            days,
            rs.getInt("start_minute"),
            rs.getInt("end_minute"),
            rs.getObject("first_date", LocalDate.class),
            rs.getObject("last_date", LocalDate.class));
    return new Series(
        rs.getObject("id", UUID.class),
        rs.getObject("organization_id", UUID.class),
        schedule,
        RentalMode.valueOf(rs.getString("rental_mode")),
        rs.getObject("ended_from", LocalDate.class),
        Series.PaymentStatus.valueOf(rs.getString("payment_status")),
        rs.getObject("request_id", UUID.class),
        rs.getString("idempotency_key"),
        rs.getString("idempotency_fingerprint"),
        rs.getString("created_by"),
        rs.getLong("version"),
        BookingSql.instant(rs, "created_at"),
        BookingSql.instant(rs, "updated_at"));
  }
}
