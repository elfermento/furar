// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.IdempotencyKeys;
import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.persistence.NativeSql;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As claves de reintento de «reservar» en SQL, como o resto das reservas (ADR-0025, puntos 18 e
 * 19). Sempre dentro dunha transacción (RLS) e co concello como parámetro (A8b, {@code
 * IdempotencyKeySqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class IdempotencyKeySql {

  private final JdbcClient jdbc;

  IdempotencyKeySql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<IdempotencyKeys.Remembered> find(UUID municipality, String subject, String key) {
    return jdbc.sql(
            """
            select fingerprint, booking_id from booking_idempotency_key
            where holder_subject = :subject and idempotency_key = :key
              and municipality_id = :tenant
            """)
        .param("subject", subject)
        .param("key", key)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new IdempotencyKeys.Remembered(
                    rs.getString("fingerprint"),
                    new BookingId(rs.getObject("booking_id", UUID.class))))
        .optional();
  }

  void insert(
      UUID municipality,
      String subject,
      String key,
      String fingerprint,
      UUID bookingId,
      Instant at) {
    jdbc.sql(
            """
            insert into booking_idempotency_key
              (id, municipality_id, holder_subject, idempotency_key, fingerprint, booking_id,
               created_at)
            values (:id, :tenant, :subject, :key, :fingerprint, :booking, :at)
            """)
        .param("id", Ids.newV7())
        .param("tenant", municipality)
        .param("subject", subject)
        .param("key", key)
        .param("fingerprint", fingerprint)
        .param("booking", bookingId)
        .param("at", BookingSql.utc(at))
        .update();
  }

  int deleteCreatedBefore(UUID municipality, Instant cutoff) {
    return jdbc.sql(
            """
            delete from booking_idempotency_key
            where created_at < :cutoff and municipality_id = :tenant
            """)
        .param("cutoff", BookingSql.utc(cutoff))
        .param("tenant", municipality)
        .update();
  }
}
