// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.domain.Booking;
import gal.furar.shared.persistence.NativeSql;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A asistencia das reservas en SQL (ADR-0032, puntos 4 e 5): a reserva co bloqueo da súa fila, para
 * validala ou marcala, e os lotes da tarefa das faltas. Sempre dentro dunha transacción (RLS) e co
 * concello como parámetro (A8b, {@code BookingAttendanceSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingAttendanceSql {

  private final JdbcClient jdbc;

  BookingAttendanceSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * A reserva co bloqueo da súa fila ata o fin da transacción ({@code for update}): validar e
   * marcar a asistencia non tocan a ocupación, así que non precisan o bloqueo da instalación.
   */
  Optional<Booking> findLocked(UUID municipality, UUID id) {
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
            where id = :id and municipality_id = :tenant
            for update
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query((rs, n) -> BookingSql.booking(rs))
        .optional();
  }

  /** A reserva dese código no concello, co bloqueo da súa fila ({@code for update}). */
  Optional<Booking> findByCodeLocked(UUID municipality, String code) {
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
            where code = :code and municipality_id = :tenant
            for update
            """)
        .param("code", code)
        .param("tenant", municipality)
        .query((rs, n) -> BookingSql.booking(rs))
        .optional();
  }

  /**
   * As candidatas a falta dunha instalación con control de entrada (ADR-0032, punto 5):
   * confirmadas, sen marca e nunca tocadas, que empezaron despois de {@code since} e remataron como
   * moito en {@code endedBy}. Colle as filas con {@code for update skip locked}: a que ten outra
   * transacción (unha validación, unha cancelación) sáltase. Usa o índice parcial {@code
   * booking_no_show_candidates_idx}.
   */
  List<UUID> noShowCandidates(
      UUID municipality, UUID facilityId, Instant since, Instant endedBy, int limit) {
    return jdbc.sql(
            """
            select id from booking
            where facility_id = :facility and status = 'CONFIRMED' and attendance = 'NONE'
              and attendance_by is null and organization_id is null and activity_id is null
              and lower(period) > :since and upper(period) <= :endedBy
              and municipality_id = :tenant
            order by upper(period), id
            limit :limit
            for update skip locked
            """)
        .param("facility", facilityId)
        .param("since", BookingSql.utc(since))
        .param("endedBy", BookingSql.utc(endedBy))
        .param("tenant", municipality)
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }

  /**
   * Marca como falta esas reservas se seguen confirmadas, sen marca e sen tocar: a que cambiou
   * mentres tanto non se pisa. Devolve as que marcou, co correo da reserva (nulo se non o ten).
   */
  List<Marked> markNoShow(UUID municipality, Collection<UUID> ids, Instant now, String actor) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            update booking
            set attendance = 'NO_SHOW', attendance_at = :now, attendance_by = :actor,
                updated_at = :now, version = version + 1
            where id in (:ids) and status = 'CONFIRMED' and attendance = 'NONE'
              and attendance_by is null
              and municipality_id = :tenant
            returning id, holder_email_key
            """)
        .param("now", BookingSql.utc(now))
        .param("actor", actor)
        .param("ids", List.copyOf(ids))
        .param("tenant", municipality)
        .query(
            (rs, n) -> new Marked(rs.getObject("id", UUID.class), rs.getString("holder_email_key")))
        .list();
  }

  /** Unha reserva que a tarefa marcou como falta. */
  record Marked(UUID bookingId, @Nullable String emailKey) {}
}
