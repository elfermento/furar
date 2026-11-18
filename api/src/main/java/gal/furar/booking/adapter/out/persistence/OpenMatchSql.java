// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.OpenMatches.Listed;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.MatchLevel;
import gal.furar.booking.domain.OpenMatch;
import gal.furar.booking.domain.OpenMatchCancelReason;
import gal.furar.booking.domain.OpenMatchStatus;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As partidas abertas en SQL, como o resto das reservas (ADR-0040): cada lectura leva a súa reserva
 * (instalación, uso, parte, franxa e estado) e as contas das persoas que se uniron, nunha consulta.
 * Os filtros opcionais van como {@code (:all or …)}, así o nivel 0 é unha conxunción co concello.
 * Sempre dentro dunha transacción (RLS) e co concello como parámetro (A8b, {@code
 * OpenMatchSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class OpenMatchSql {

  /** Ningún valor real: o recheo dun filtro que non filtra. */
  private static final UUID NONE = new UUID(0, 0);

  private final JdbcClient jdbc;

  OpenMatchSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void insert(UUID municipality, OpenMatch m) {
    jdbc.sql(
            """
            insert into booking_open_match
              (id, municipality_id, booking_id, organizer_subject, organizer_name, known_count,
               minimum, maximum, level, title, description, approval, status, cancel_reason,
               deadline_checked_at, hidden_at, hidden_by, created_at, updated_at, version)
            values (:id, :tenant, :booking, :subject, :name, :known, :minimum, :maximum, :level,
                    :title, :description, :approval, :status, :reason, :checked, :hiddenAt,
                    :hiddenBy, :createdAt, :updatedAt, 0)
            """)
        .param("id", m.id())
        .param("tenant", municipality)
        .param("booking", m.bookingId())
        .param("subject", m.organizerSubject())
        .param("name", m.organizerName())
        .param("known", m.knownCount())
        .param("minimum", m.minimum())
        .param("maximum", m.maximum())
        .param("level", m.level() == null ? null : m.level().name(), Types.VARCHAR)
        .param("title", m.title(), Types.VARCHAR)
        .param("description", m.description(), Types.VARCHAR)
        .param("approval", m.approval())
        .param("status", m.status().name())
        .param("reason", m.cancelReason() == null ? null : m.cancelReason().name(), Types.VARCHAR)
        .param("checked", utc(m.deadlineCheckedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("hiddenAt", utc(m.hiddenAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("hiddenBy", m.hiddenBy(), Types.VARCHAR)
        .param("createdAt", BookingSql.utc(m.createdAt()))
        .param("updatedAt", BookingSql.utc(m.updatedAt()))
        .update();
  }

  /** Garda o cambio se a versión segue sendo a lida; devolve as filas tocadas (0 ou 1). */
  int update(UUID municipality, long version, OpenMatch m) {
    return jdbc.sql(
            """
            update booking_open_match
               set organizer_name = :name, known_count = :known, minimum = :minimum,
                   maximum = :maximum, level = :level, title = :title, description = :description,
                   approval = :approval, status = :status, cancel_reason = :reason,
                   deadline_checked_at = :checked, hidden_at = :hiddenAt, hidden_by = :hiddenBy,
                   updated_at = :updatedAt, version = version + 1
             where id = :id and version = :version
               and municipality_id = :tenant
            """)
        .param("name", m.organizerName())
        .param("known", m.knownCount())
        .param("minimum", m.minimum())
        .param("maximum", m.maximum())
        .param("level", m.level() == null ? null : m.level().name(), Types.VARCHAR)
        .param("title", m.title(), Types.VARCHAR)
        .param("description", m.description(), Types.VARCHAR)
        .param("approval", m.approval())
        .param("status", m.status().name())
        .param("reason", m.cancelReason() == null ? null : m.cancelReason().name(), Types.VARCHAR)
        .param("checked", utc(m.deadlineCheckedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("hiddenAt", utc(m.hiddenAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("hiddenBy", m.hiddenBy(), Types.VARCHAR)
        .param("updatedAt", BookingSql.utc(m.updatedAt()))
        .param("id", m.id())
        .param("version", version)
        .param("tenant", municipality)
        .update();
  }

  Optional<OpenMatch> find(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, booking_id, organizer_subject, organizer_name, known_count, minimum,
                   maximum, level, title, description, approval, status, cancel_reason,
                   deadline_checked_at, hidden_at, hidden_by, created_at, updated_at, version
              from booking_open_match
             where id = :id
               and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> match(rs))
        .optional();
  }

  Optional<OpenMatch> findLocked(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, booking_id, organizer_subject, organizer_name, known_count, minimum,
                   maximum, level, title, description, approval, status, cancel_reason,
                   deadline_checked_at, hidden_at, hidden_by, created_at, updated_at, version
              from booking_open_match
             where id = :id
               and municipality_id = :tenant
             for update
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> match(rs))
        .optional();
  }

  Optional<OpenMatch> findByBookingLocked(UUID municipality, UUID booking) {
    return jdbc.sql(
            """
            select id, booking_id, organizer_subject, organizer_name, known_count, minimum,
                   maximum, level, title, description, approval, status, cancel_reason,
                   deadline_checked_at, hidden_at, hidden_by, created_at, updated_at, version
              from booking_open_match
             where booking_id = :booking
               and municipality_id = :tenant
             for update
            """)
        .param("booking", booking)
        .param("tenant", municipality)
        .query((rs, n) -> match(rs))
        .optional();
  }

  Optional<Listed> listed(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select m.id, m.booking_id, m.organizer_subject, m.organizer_name, m.known_count,
                   m.minimum, m.maximum, m.level, m.title, m.description, m.approval, m.status,
                   m.cancel_reason, m.deadline_checked_at, m.hidden_at, m.hidden_by, m.created_at,
                   m.updated_at, m.version, b.facility_id, b.use_id, b.part_id,
                   lower(b.period) as starts_at, upper(b.period) as ends_at,
                   b.status as booking_status,
                   (select count(*) from booking_open_match_player p
                     where p.municipality_id = m.municipality_id and p.match_id = m.id
                       and p.status = 'CONFIRMED') as confirmed,
                   (select count(*) from booking_open_match_player p
                     where p.municipality_id = m.municipality_id and p.match_id = m.id
                       and p.status = 'PENDING') as pending
              from booking_open_match m
              join booking b on b.municipality_id = m.municipality_id and b.id = m.booking_id
             where m.id = :id
               and m.municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> listed(rs))
        .optional();
  }

  List<Listed> ofBookings(UUID municipality, Collection<UUID> bookings) {
    return jdbc.sql(
            """
            select m.id, m.booking_id, m.organizer_subject, m.organizer_name, m.known_count,
                   m.minimum, m.maximum, m.level, m.title, m.description, m.approval, m.status,
                   m.cancel_reason, m.deadline_checked_at, m.hidden_at, m.hidden_by, m.created_at,
                   m.updated_at, m.version, b.facility_id, b.use_id, b.part_id,
                   lower(b.period) as starts_at, upper(b.period) as ends_at,
                   b.status as booking_status,
                   (select count(*) from booking_open_match_player p
                     where p.municipality_id = m.municipality_id and p.match_id = m.id
                       and p.status = 'CONFIRMED') as confirmed,
                   (select count(*) from booking_open_match_player p
                     where p.municipality_id = m.municipality_id and p.match_id = m.id
                       and p.status = 'PENDING') as pending
              from booking_open_match m
              join booking b on b.municipality_id = m.municipality_id and b.id = m.booking_id
             where m.booking_id in (:bookings)
               and m.municipality_id = :tenant
            """)
        .param("bookings", List.copyOf(bookings))
        .param("tenant", municipality)
        .query((rs, n) -> listed(rs))
        .list();
  }

  /** As que se ven en público, da que empeza antes (ADR-0040, punto 9). */
  List<Listed> visible(
      UUID municipality, Instant now, @Nullable UUID facility, int limit, long offset) {
    return jdbc.sql(
            """
            select m.id, m.booking_id, m.organizer_subject, m.organizer_name, m.known_count,
                   m.minimum, m.maximum, m.level, m.title, m.description, m.approval, m.status,
                   m.cancel_reason, m.deadline_checked_at, m.hidden_at, m.hidden_by, m.created_at,
                   m.updated_at, m.version, b.facility_id, b.use_id, b.part_id,
                   lower(b.period) as starts_at, upper(b.period) as ends_at,
                   b.status as booking_status,
                   (select count(*) from booking_open_match_player p
                     where p.municipality_id = m.municipality_id and p.match_id = m.id
                       and p.status = 'CONFIRMED') as confirmed,
                   (select count(*) from booking_open_match_player p
                     where p.municipality_id = m.municipality_id and p.match_id = m.id
                       and p.status = 'PENDING') as pending
              from booking_open_match m
              join booking b on b.municipality_id = m.municipality_id and b.id = m.booking_id
             where m.status = 'OPEN' and m.hidden_at is null and b.status = 'CONFIRMED'
               and lower(b.period) > :now
               and (:all or b.facility_id = :facility)
               and m.known_count + (select count(*) from booking_open_match_player p
                                      where p.municipality_id = m.municipality_id
                                        and p.match_id = m.id
                                        and p.status = 'CONFIRMED') < m.maximum
               and m.municipality_id = :tenant
             order by lower(b.period), m.id
             limit :limit offset :offset
            """)
        .param("now", BookingSql.utc(now))
        .param("all", facility == null)
        .param("facility", facility == null ? NONE : facility)
        .param("tenant", municipality)
        .param("limit", limit)
        .param("offset", offset)
        .query((rs, n) -> listed(rs))
        .list();
  }

  long countVisible(UUID municipality, Instant now, @Nullable UUID facility) {
    return jdbc.sql(
            """
            select count(*)
              from booking_open_match m
              join booking b on b.municipality_id = m.municipality_id and b.id = m.booking_id
             where m.status = 'OPEN' and m.hidden_at is null and b.status = 'CONFIRMED'
               and lower(b.period) > :now
               and (:all or b.facility_id = :facility)
               and m.known_count + (select count(*) from booking_open_match_player p
                                      where p.municipality_id = m.municipality_id
                                        and p.match_id = m.id
                                        and p.status = 'CONFIRMED') < m.maximum
               and m.municipality_id = :tenant
            """)
        .param("now", BookingSql.utc(now))
        .param("all", facility == null)
        .param("facility", facility == null ? NONE : facility)
        .param("tenant", municipality)
        .query(Long.class)
        .single();
  }

  /** As que organiza a conta ou nas que está ou pediu entrar, aínda sen rematar. */
  List<Listed> ofAccount(UUID municipality, String subject, Instant now) {
    return jdbc.sql(
            """
            select m.id, m.booking_id, m.organizer_subject, m.organizer_name, m.known_count,
                   m.minimum, m.maximum, m.level, m.title, m.description, m.approval, m.status,
                   m.cancel_reason, m.deadline_checked_at, m.hidden_at, m.hidden_by, m.created_at,
                   m.updated_at, m.version, b.facility_id, b.use_id, b.part_id,
                   lower(b.period) as starts_at, upper(b.period) as ends_at,
                   b.status as booking_status,
                   (select count(*) from booking_open_match_player p
                     where p.municipality_id = m.municipality_id and p.match_id = m.id
                       and p.status = 'CONFIRMED') as confirmed,
                   (select count(*) from booking_open_match_player p
                     where p.municipality_id = m.municipality_id and p.match_id = m.id
                       and p.status = 'PENDING') as pending
              from booking_open_match m
              join booking b on b.municipality_id = m.municipality_id and b.id = m.booking_id
             where (m.organizer_subject = :subject
                    or exists (select 1 from booking_open_match_player p
                                where p.municipality_id = m.municipality_id
                                  and p.match_id = m.id and p.account_subject = :subject))
               and upper(b.period) > :now
               and m.municipality_id = :tenant
             order by lower(b.period), m.id
            """)
        .param("subject", subject)
        .param("now", BookingSql.utc(now))
        .param("tenant", municipality)
        .query((rs, n) -> listed(rs))
        .list();
  }

  /** As candidatas da tarefa do prazo, sen bloqueos (ADR-0040, punto 7). */
  List<UUID> deadlineDue(UUID municipality, Instant now, Instant from, int hours, int limit) {
    return jdbc.sql(
            """
            select m.id
              from booking_open_match m
              join booking b on b.municipality_id = m.municipality_id and b.id = m.booking_id
             where m.status = 'OPEN' and m.hidden_at is null and m.deadline_checked_at is null
               and b.status = 'CONFIRMED'
               and lower(b.period) > :now
               and lower(b.period) - :hours * interval '1 hour' <= :now
               and lower(b.period) - :hours * interval '1 hour' >= :since
               and m.municipality_id = :tenant
             order by lower(b.period), m.id
             limit :limit
            """)
        .param("now", BookingSql.utc(now))
        .param("hours", hours)
        .param("since", BookingSql.utc(from))
        .param("tenant", municipality)
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }

  /** Borra as partidas destas reservas (as súas persoas e solicitudes van en cascada). */
  int purgeOfBookings(UUID municipality, Collection<UUID> bookings) {
    if (bookings.isEmpty()) {
      return 0;
    }
    return jdbc.sql(
            """
            delete from booking_open_match
             where booking_id in (:bookings)
               and municipality_id = :tenant
            """)
        .param("bookings", List.copyOf(bookings))
        .param("tenant", municipality)
        .update();
  }

  private static Listed listed(ResultSet rs) throws SQLException {
    return new Listed(
        match(rs),
        rs.getObject("facility_id", UUID.class),
        rs.getObject("use_id", UUID.class),
        rs.getObject("part_id", UUID.class),
        BookingSql.instant(rs, "starts_at"),
        BookingSql.instant(rs, "ends_at"),
        BookingStatus.valueOf(rs.getString("booking_status")),
        rs.getInt("confirmed"),
        rs.getInt("pending"));
  }

  private static OpenMatch match(ResultSet rs) throws SQLException {
    String level = rs.getString("level");
    String reason = rs.getString("cancel_reason");
    return new OpenMatch(
        rs.getObject("id", UUID.class),
        rs.getObject("booking_id", UUID.class),
        rs.getString("organizer_subject"),
        rs.getString("organizer_name"),
        rs.getInt("known_count"),
        rs.getInt("minimum"),
        rs.getInt("maximum"),
        level == null ? null : MatchLevel.valueOf(level),
        rs.getString("title"),
        rs.getString("description"),
        rs.getBoolean("approval"),
        OpenMatchStatus.valueOf(rs.getString("status")),
        reason == null ? null : OpenMatchCancelReason.valueOf(reason),
        nullable(rs, "deadline_checked_at"),
        nullable(rs, "hidden_at"),
        rs.getString("hidden_by"),
        BookingSql.instant(rs, "created_at"),
        BookingSql.instant(rs, "updated_at"),
        rs.getLong("version"));
  }

  private static @Nullable Instant nullable(ResultSet rs, String column) throws SQLException {
    OffsetDateTime at = rs.getObject(column, OffsetDateTime.class);
    return at == null ? null : at.toInstant();
  }

  private static @Nullable OffsetDateTime utc(@Nullable Instant at) {
    return at == null ? null : BookingSql.utc(at);
  }
}
