// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.domain.RentalMode;
import gal.furar.booking.domain.season.SeriesRequest;
import gal.furar.booking.domain.season.SeriesSchedule;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As solicitudes de tempada en SQL (ADR-0039, puntos 10 e 11). Sempre dentro dunha transacción
 * (RLS) e co concello como parámetro (A8b, {@code SeriesRequestSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class SeriesRequestSql {

  private final JdbcClient jdbc;

  SeriesRequestSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<SeriesRequest> find(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, organization_id, facility_id, use_id, part_id, rental_mode,
                   array_to_string(weekdays, ',') as days, start_minute, end_minute, first_date,
                   last_date, note, requested_by_subject, requested_by_email, status,
                   rejection_message, series_id, decided_at, decided_by, created_at, updated_at,
                   version
            from booking_series_request
            where id = :id and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> request(rs))
        .optional();
  }

  Optional<SeriesRequest> findForUpdate(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, organization_id, facility_id, use_id, part_id, rental_mode,
                   array_to_string(weekdays, ',') as days, start_minute, end_minute, first_date,
                   last_date, note, requested_by_subject, requested_by_email, status,
                   rejection_message, series_id, decided_at, decided_by, created_at, updated_at,
                   version
            from booking_series_request
            where id = :id and municipality_id = :tenant
            for update
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> request(rs))
        .optional();
  }

  List<SeriesRequest> list(UUID municipality, String status) {
    return jdbc.sql(
            """
            select id, organization_id, facility_id, use_id, part_id, rental_mode,
                   array_to_string(weekdays, ',') as days, start_minute, end_minute, first_date,
                   last_date, note, requested_by_subject, requested_by_email, status,
                   rejection_message, series_id, decided_at, decided_by, created_at, updated_at,
                   version
            from booking_series_request
            where status = :status and municipality_id = :tenant
            order by created_at, id
            """)
        .param("status", status)
        .param("tenant", municipality)
        .query((rs, n) -> request(rs))
        .list();
  }

  List<SeriesRequest> ofOrganization(UUID municipality, UUID organization) {
    return jdbc.sql(
            """
            select id, organization_id, facility_id, use_id, part_id, rental_mode,
                   array_to_string(weekdays, ',') as days, start_minute, end_minute, first_date,
                   last_date, note, requested_by_subject, requested_by_email, status,
                   rejection_message, series_id, decided_at, decided_by, created_at, updated_at,
                   version
            from booking_series_request
            where organization_id = :organization and municipality_id = :tenant
            order by created_at desc, id
            """)
        .param("organization", organization)
        .param("tenant", municipality)
        .query((rs, n) -> request(rs))
        .list();
  }

  int countPending(UUID municipality) {
    return jdbc.sql(
            """
            select count(*) from booking_series_request
            where status = 'PENDING' and municipality_id = :tenant
            """)
        .param("tenant", municipality)
        .query(Integer.class)
        .single();
  }

  int countPending(UUID municipality, UUID organization) {
    return jdbc.sql(
            """
            select count(*) from booking_series_request
            where organization_id = :organization and status = 'PENDING'
              and municipality_id = :tenant
            """)
        .param("organization", organization)
        .param("tenant", municipality)
        .query(Integer.class)
        .single();
  }

  void insert(UUID municipality, SeriesRequest r) {
    SeriesSchedule h = r.schedule();
    jdbc.sql(
            """
            insert into booking_series_request
              (id, municipality_id, organization_id, facility_id, use_id, part_id, rental_mode,
               weekdays, start_minute, end_minute, first_date, last_date, note,
               requested_by_subject, requested_by_email, status, created_at, updated_at, version)
            values (:id, :tenant, :organization, :facility, :use, :part, :mode,
                    cast(:days as smallint[]), :startMinute, :endMinute, :firstDate, :lastDate,
                    :note, :subject, :email, 'PENDING', :createdAt, :updatedAt, 0)
            """)
        .param("id", r.id())
        .param("tenant", municipality)
        .param("organization", r.organizationId())
        .param("facility", h.facilityId())
        .param("use", h.useId())
        .param("part", h.partId(), Types.OTHER)
        .param("mode", r.rentalMode().name())
        .param(
            "days",
            "{"
                + h.isoWeekdays().stream().map(String::valueOf).collect(Collectors.joining(","))
                + "}")
        .param("startMinute", h.startMinute())
        .param("endMinute", h.endMinute())
        .param("firstDate", h.firstDate())
        .param("lastDate", h.lastDate())
        .param("note", r.note(), Types.VARCHAR)
        .param("subject", r.requestedBySubject(), Types.VARCHAR)
        .param("email", r.requestedByEmail(), Types.VARCHAR)
        .param("createdAt", BookingSql.utc(r.createdAt()))
        .param("updatedAt", BookingSql.utc(r.updatedAt()))
        .update();
  }

  boolean update(UUID municipality, SeriesRequest r, long expected) {
    return jdbc.sql(
                """
                update booking_series_request
                set status = :status, rejection_message = :message, series_id = :series,
                    decided_at = :decidedAt, decided_by = :decidedBy, updated_at = :updatedAt,
                    version = version + 1
                where id = :id and version = :version and municipality_id = :tenant
                """)
            .param("status", r.status().name())
            .param("message", r.rejectionMessage(), Types.VARCHAR)
            .param("series", r.seriesId(), Types.OTHER)
            .param(
                "decidedAt",
                r.decidedAt() == null ? null : BookingSql.utc(r.decidedAt()),
                Types.TIMESTAMP_WITH_TIMEZONE)
            .param("decidedBy", r.decidedBy(), Types.VARCHAR)
            .param("updatedAt", BookingSql.utc(r.updatedAt()))
            .param("id", r.id())
            .param("version", expected)
            .param("tenant", municipality)
            .update()
        == 1;
  }

  int delete(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            delete from booking_series_request
            where id = :id and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .update();
  }

  int deletePending(UUID municipality, UUID organization) {
    return jdbc.sql(
            """
            delete from booking_series_request
            where organization_id = :organization and status = 'PENDING'
              and municipality_id = :tenant
            """)
        .param("organization", organization)
        .param("tenant", municipality)
        .update();
  }

  int purgeDecidedBefore(UUID municipality, Instant cutoff, int limit) {
    return jdbc.sql(
            """
            delete from booking_series_request
            where id in (select id from booking_series_request
                         where status <> 'PENDING' and decided_at < :cutoff
                           and municipality_id = :tenant
                         order by decided_at
                         limit :limit)
              and municipality_id = :tenant
            """)
        .param("cutoff", BookingSql.utc(cutoff))
        .param("limit", limit)
        .param("tenant", municipality)
        .update();
  }

  private static SeriesRequest request(ResultSet rs) throws SQLException {
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
    OffsetDateTime decided = rs.getObject("decided_at", OffsetDateTime.class);
    return new SeriesRequest(
        rs.getObject("id", UUID.class),
        rs.getObject("organization_id", UUID.class),
        schedule,
        RentalMode.valueOf(rs.getString("rental_mode")),
        rs.getString("note"),
        rs.getString("requested_by_subject"),
        rs.getString("requested_by_email"),
        SeriesRequest.Status.valueOf(rs.getString("status")),
        rs.getString("rejection_message"),
        rs.getObject("series_id", UUID.class),
        decided == null ? null : decided.toInstant(),
        rs.getString("decided_by"),
        rs.getLong("version"),
        BookingSql.instant(rs, "created_at"),
        BookingSql.instant(rs, "updated_at"));
  }
}
