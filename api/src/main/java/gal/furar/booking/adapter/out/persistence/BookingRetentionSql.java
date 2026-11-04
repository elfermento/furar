// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.BookingRetention;
import gal.furar.shared.persistence.NativeSql;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A conservación das reservas en SQL, como o resto das reservas (ADR-0025, punto 18). Sempre dentro
 * dunha transacción (RLS) e co concello como parámetro (A8b, {@code BookingRetentionSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingRetentionSql {

  private final JdbcClient jdbc;

  BookingRetentionSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  List<UUID> expiredPaid(UUID municipality, Instant cutoff, int limit) {
    return jdbc.sql(
            """
            select id from booking
            where anonymized_at is null and price_kind = 'PAID'
              and upper(period) < :cutoff and updated_at < :cutoff
              and municipality_id = :tenant
            order by upper(period), id
            limit :limit
            """)
        .param("cutoff", BookingSql.utc(cutoff))
        .param("tenant", municipality)
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }

  List<UUID> expiredUnpaid(UUID municipality, Instant cutoff, int limit) {
    return jdbc.sql(
            """
            select id from booking
            where anonymized_at is null and price_kind <> 'PAID'
              and upper(period) < :cutoff and updated_at < :cutoff
              and municipality_id = :tenant
            order by upper(period), id
            limit :limit
            """)
        .param("cutoff", BookingSql.utc(cutoff))
        .param("tenant", municipality)
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }

  /** Na historia, as filas que fixo a propia persoa (o seu {@code sub}) quedan sen ela. */
  int forgetEventActors(UUID municipality, Collection<UUID> ids) {
    return jdbc.sql(
            """
            update booking_event set actor_subject = :anonymised
            where booking_id in (:ids)
              and actor_subject = (select b.holder_subject from booking b
                                   where b.id = booking_event.booking_id
                                     and b.municipality_id = :tenant)
              and municipality_id = :tenant
            """)
        .param("anonymised", BookingRetention.ANONYMISED_ACTOR)
        .param("ids", ids)
        .param("tenant", municipality)
        .update();
  }

  /** Quen cancelou, se foi a propia persoa. */
  int forgetCanceller(UUID municipality, Collection<UUID> ids) {
    return jdbc.sql(
            """
            update booking set cancelled_by = :anonymised
            where id in (:ids) and anonymized_at is null and cancelled_by = holder_subject
              and municipality_id = :tenant
            """)
        .param("anonymised", BookingRetention.ANONYMISED_ACTOR)
        .param("ids", ids)
        .param("tenant", municipality)
        .update();
  }

  /** A persoa: conta, nome e correo a nulo, coa data. O idioma queda (non identifica). */
  int forgetHolder(UUID municipality, Collection<UUID> ids, Instant now) {
    return jdbc.sql(
            """
            update booking
            set holder_subject = null, holder_name = null, holder_email = null,
                anonymized_at = :now, updated_at = :now, version = version + 1
            where id in (:ids) and anonymized_at is null
              and municipality_id = :tenant
            """)
        .param("now", BookingSql.utc(now))
        .param("ids", ids)
        .param("tenant", municipality)
        .update();
  }
}
