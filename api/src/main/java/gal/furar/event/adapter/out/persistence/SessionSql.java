// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSession;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As sesións das actividades con horario e a súa asistencia en SQL (ADR-0041). Sempre dentro dunha
 * transacción (RLS) e co concello como parámetro (A8b, {@code SessionSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class SessionSql {

  private static final String COLUMNS =
      """
      id, event_id, session_date, starts_at, ends_at, booking_id, cancelled_at,
      attendance_taken_at
      """;

  private final JdbcClient jdbc;

  SessionSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** As sesións novas nunha sentenza: as listas van como texto e desfanse con {@code unnest}. */
  void insertAll(UUID municipality, List<EventSession> sessions, Instant now) {
    if (sessions.isEmpty()) {
      return;
    }
    jdbc.sql(
            """
            insert into event_session
              (id, municipality_id, event_id, session_date, starts_at, ends_at, booking_id,
               created_at, updated_at, version)
            select id, municipality_id, cast(:event as uuid), day, since, until,
                   cast(nullif(owner, '') as uuid), cast(:now as timestamptz),
                   cast(:now as timestamptz), 0
            from (select cast(:tenant as uuid) as municipality_id,
                         unnest(cast(string_to_array(:ids, ',') as uuid[])) as id,
                         unnest(cast(string_to_array(:days, ',') as date[])) as day,
                         unnest(cast(string_to_array(:starts, ',') as timestamptz[])) as since,
                         unnest(cast(string_to_array(:ends, ',') as timestamptz[])) as until,
                         unnest(string_to_array(:owners, ',')) as owner) as planned
            where municipality_id = :tenant
            """)
        .param("tenant", municipality)
        .param("event", sessions.getFirst().eventId().value())
        .param("now", utc(now))
        .param("ids", joined(sessions, s -> s.id().toString()))
        .param("days", joined(sessions, s -> s.date().toString()))
        .param("starts", joined(sessions, s -> s.startsAt().toString()))
        .param("ends", joined(sessions, s -> s.endsAt().toString()))
        .param(
            "owners", joined(sessions, s -> s.bookingId() == null ? "" : s.bookingId().toString()))
        .update();
  }

  List<EventSession> listForEvent(UUID municipality, UUID event) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_session
                where event_id = :event and municipality_id = :tenant
                order by session_date
                """)
        .param("event", event)
        .param("tenant", municipality)
        .query(SessionSql::session)
        .list();
  }

  Optional<EventSession> find(UUID municipality, UUID event, UUID id) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_session
                where id = :id and event_id = :event and municipality_id = :tenant
                """)
        .param("id", id)
        .param("event", event)
        .param("tenant", municipality)
        .query(SessionSql::session)
        .optional();
  }

  /** As sesións sen cancelar destas actividades que aínda non remataron, por inicio. */
  List<EventSession> upcoming(UUID municipality, Collection<UUID> events, Instant now) {
    if (events.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_session
                where event_id in (:events) and cancelled_at is null and ends_at > :now
                  and municipality_id = :tenant
                order by starts_at, id
                """)
        .param("events", List.copyOf(events))
        .param("now", utc(now))
        .param("tenant", municipality)
        .query(SessionSql::session)
        .list();
  }

  List<Link> byBookings(UUID municipality, Collection<UUID> bookings) {
    if (bookings.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            select booking_id, id
              from event_session
             where booking_id in (:bookings) and municipality_id = :tenant
            """)
        .param("bookings", List.copyOf(bookings))
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new Link(rs.getObject("booking_id", UUID.class), rs.getObject("id", UUID.class)))
        .list();
  }

  Optional<EventSession> findByBooking(UUID municipality, UUID booking) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from event_session
                where booking_id = :booking and municipality_id = :tenant
                """)
        .param("booking", booking)
        .param("tenant", municipality)
        .query(SessionSql::session)
        .optional();
  }

  int cancel(UUID municipality, Collection<UUID> ids, Instant now) {
    if (ids.isEmpty()) {
      return 0;
    }
    return jdbc.sql(
            """
            update event_session
               set cancelled_at = :now, updated_at = :now, version = version + 1
             where id in (:ids) and cancelled_at is null and municipality_id = :tenant
            """)
        .param("now", utc(now))
        .param("ids", List.copyOf(ids))
        .param("tenant", municipality)
        .update();
  }

  List<Mark> marks(UUID municipality, UUID session) {
    return jdbc.sql(
            """
            select registration_id, present
              from event_attendance
             where session_id = :session and municipality_id = :tenant
            """)
        .param("session", session)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new Mark(rs.getObject("registration_id", UUID.class), rs.getBoolean("present")))
        .list();
  }

  /** Cambia unha marca que xa había; devolve as filas tocadas. */
  int updateMark(UUID municipality, UUID session, UUID registration, boolean present, Instant now) {
    return jdbc.sql(
            """
            update event_attendance
               set present = :present, marked_at = :now
             where session_id = :session and registration_id = :registration
               and municipality_id = :tenant
            """)
        .param("present", present)
        .param("now", utc(now))
        .param("session", session)
        .param("registration", registration)
        .param("tenant", municipality)
        .update();
  }

  /** Unha marca nova. */
  void insertMark(
      UUID municipality, UUID session, UUID registration, boolean present, Instant now) {
    jdbc.sql(
            """
            insert into event_attendance
              (municipality_id, session_id, registration_id, present, marked_at)
            values (:tenant, :session, :registration, :present, :now)
            """)
        .param("tenant", municipality)
        .param("session", session)
        .param("registration", registration)
        .param("present", present)
        .param("now", utc(now))
        .update();
  }

  void deleteMark(UUID municipality, UUID session, UUID registration) {
    jdbc.sql(
            """
            delete from event_attendance
             where session_id = :session and registration_id = :registration
               and municipality_id = :tenant
            """)
        .param("session", session)
        .param("registration", registration)
        .param("tenant", municipality)
        .update();
  }

  /**
   * Gardouse a lista: cando e quen (a primeira vez, tamén cando se pasou). Devolve as filas
   * tocadas.
   */
  int taken(UUID municipality, UUID session, Instant now, @Nullable String byName) {
    return jdbc.sql(
            """
            update event_session
               set attendance_taken_at = coalesce(attendance_taken_at, :now),
                   attendance_saved_at = :now, attendance_saved_by_name = :byName,
                   updated_at = :now, version = version + 1
             where id = :session and municipality_id = :tenant
            """)
        .param("now", utc(now))
        .param("byName", byName, Types.VARCHAR)
        .param("session", session)
        .param("tenant", municipality)
        .update();
  }

  /** Cando e quen gardou a lista por última vez. */
  Optional<SessionRepository.Saved> saved(UUID municipality, UUID session) {
    return jdbc.sql(
            """
            select attendance_saved_at, attendance_saved_by_name
              from event_session
             where id = :session and attendance_saved_at is not null
               and municipality_id = :tenant
            """)
        .param("session", session)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new SessionRepository.Saved(
                    instant(rs, "attendance_saved_at"), rs.getString("attendance_saved_by_name")))
        .optional();
  }

  List<TallyRow> tallies(UUID municipality, UUID event) {
    return jdbc.sql(
            """
            select a.session_id,
                   count(*) filter (where a.present) as present,
                   count(*) filter (where not a.present) as absent
              from event_attendance a
              join event_session s on s.id = a.session_id and s.municipality_id = a.municipality_id
             where s.event_id = :event and a.municipality_id = :tenant
             group by a.session_id
            """)
        .param("event", event)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new TallyRow(
                    rs.getObject("session_id", UUID.class),
                    rs.getInt("present"),
                    rs.getInt("absent")))
        .list();
  }

  /** Por inscrición: presenzas e faltas nas sesións sen cancelar. */
  List<TallyRow> byRegistration(UUID municipality, UUID event) {
    return jdbc.sql(
            """
            select a.registration_id as session_id,
                   count(*) filter (where a.present) as present,
                   count(*) filter (where not a.present) as absent
              from event_attendance a
              join event_session s on s.id = a.session_id and s.municipality_id = a.municipality_id
             where s.event_id = :event and s.cancelled_at is null
               and a.municipality_id = :tenant
             group by a.registration_id
            """)
        .param("event", event)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new TallyRow(
                    rs.getObject("session_id", UUID.class),
                    rs.getInt("present"),
                    rs.getInt("absent")))
        .list();
  }

  /** Sesións da actividade sen cancelar con lista pasada. */
  int takenCount(UUID municipality, UUID event) {
    return jdbc.sql(
            """
            select count(*)
              from event_session
             where event_id = :event and cancelled_at is null
               and attendance_taken_at is not null and municipality_id = :tenant
            """)
        .param("event", event)
        .param("tenant", municipality)
        .query(Integer.class)
        .single();
  }

  /** As sesións dun día en actividades sen cancelar (o número de persoas cóntao quen chama). */
  List<SessionRepository.Today> on(UUID municipality, LocalDate date) {
    return jdbc.sql(
            """
            select s.id, s.event_id, s.session_date, s.starts_at, s.ends_at, s.booking_id,
                   s.cancelled_at, s.attendance_taken_at, e.name_gl, e.name_es, e.facility_id,
                   e.place
              from event_session s
              join event e on e.id = s.event_id and e.municipality_id = s.municipality_id
             where s.session_date = :date and s.cancelled_at is null and e.cancelled_at is null
               and s.municipality_id = :tenant
             order by s.starts_at, e.name_gl, s.id
            """)
        .param("date", date)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new SessionRepository.Today(
                    new EventId(rs.getObject("event_id", UUID.class)),
                    rs.getString("name_gl"),
                    rs.getString("name_es"),
                    rs.getObject("facility_id", UUID.class),
                    rs.getString("place"),
                    session(rs, n),
                    0))
        .list();
  }

  private static EventSession session(ResultSet rs, int row) throws SQLException {
    return new EventSession(
        rs.getObject("id", UUID.class),
        new EventId(rs.getObject("event_id", UUID.class)),
        rs.getObject("session_date", LocalDate.class),
        instant(rs, "starts_at"),
        instant(rs, "ends_at"),
        rs.getObject("booking_id", UUID.class),
        nullableInstant(rs, "cancelled_at"),
        nullableInstant(rs, "attendance_taken_at"));
  }

  private static String joined(List<EventSession> sessions, Function<EventSession, String> value) {
    return sessions.stream().map(value).collect(Collectors.joining(","));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }

  private static @Nullable Instant nullableInstant(ResultSet rs, String column)
      throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  /** A reserva e a súa sesión. */
  record Link(UUID bookingId, UUID sessionId) {}

  /** Unha marca. */
  record Mark(UUID registrationId, boolean present) {}

  /** Presentes e ausentes por sesión ou por inscrición. */
  record TallyRow(UUID key, int present, int absent) {}
}
