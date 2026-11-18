// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.BookingReminders.Marked;
import gal.furar.shared.persistence.NativeSql;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Os recordatorios en SQL (ADR-0035): a marca {@code booking.reminded_at}, que non sobe a versión
 * da reserva (non é un cambio dela), e as baixas por correo ({@code booking_reminder_opt_out}).
 * Sempre dentro dunha transacción (RLS) e co concello como parámetro (A8b, {@code
 * BookingReminderSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingReminderSql {

  private final JdbcClient jdbc;

  BookingReminderSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * As que tocan, coas súas filas bloqueadas: a que ten outra transacción (outra instancia, unha
   * cancelación) sáltase. Usa o índice parcial {@code booking_reminder_due_idx}.
   */
  List<UUID> due(UUID municipality, Instant now, Duration lead, int limit) {
    return jdbc.sql(
            """
            select id from booking
            where status = 'CONFIRMED' and reminded_at is null and anonymized_at is null
              and holder_email is not null
              and cancel_until > :now and cancel_until <= :until
              and created_at <= cancel_until - make_interval(secs => :lead)
              and municipality_id = :tenant
            order by cancel_until, id
            limit :limit
            for update skip locked
            """)
        .param("now", BookingSql.utc(now))
        .param("until", BookingSql.utc(now.plus(lead)))
        .param("lead", (double) lead.toSeconds())
        .param("tenant", municipality)
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }

  /** Marca as que seguen confirmadas e sen recordatorio; devolve cales, coa clave do correo. */
  List<Marked> mark(UUID municipality, Collection<UUID> ids, Instant now) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            update booking
            set reminded_at = :now
            where id in (:ids) and status = 'CONFIRMED' and reminded_at is null
              and municipality_id = :tenant
            returning id, holder_email_key
            """)
        .param("now", BookingSql.utc(now))
        .param("ids", List.copyOf(ids))
        .param("tenant", municipality)
        .query(
            (rs, n) -> new Marked(rs.getObject("id", UUID.class), rs.getString("holder_email_key")))
        .list();
  }

  Set<String> optedOutAmong(UUID municipality, Collection<String> emailKeys) {
    if (emailKeys.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql(
                """
                select email_key from booking_reminder_opt_out
                where email_key in (:keys)
                  and municipality_id = :tenant
                """)
            .param("keys", List.copyOf(emailKeys))
            .param("tenant", municipality)
            .query(String.class)
            .list());
  }

  void optOut(UUID municipality, String emailKey, Instant now) {
    jdbc.sql(
            """
            insert into booking_reminder_opt_out (municipality_id, email_key, created_at)
            values (:tenant, :emailKey, :now)
            on conflict do nothing
            """)
        .param("tenant", municipality)
        .param("emailKey", emailKey)
        .param("now", BookingSql.utc(now))
        .update();
  }

  int optIn(UUID municipality, String emailKey) {
    return jdbc.sql(
            """
            delete from booking_reminder_opt_out
            where email_key = :emailKey
              and municipality_id = :tenant
            """)
        .param("emailKey", emailKey)
        .param("tenant", municipality)
        .update();
  }

  Optional<String> holderEmailKey(UUID municipality, UUID bookingId) {
    return jdbc.sql(
            """
            select holder_email_key from booking
            where id = :id and holder_email_key is not null
              and municipality_id = :tenant
            """)
        .param("id", bookingId)
        .param("tenant", municipality)
        .query(String.class)
        .optional();
  }
}
