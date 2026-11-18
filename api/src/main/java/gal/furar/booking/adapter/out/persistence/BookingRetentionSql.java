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

  /** As reservas sen pago e sen anonimizar dunha conta (ADR-0043). */
  List<UUID> unpaidOf(UUID municipality, String holderSubject, int limit) {
    return jdbc.sql(
            """
            select id from booking
            where anonymized_at is null and price_kind <> 'PAID'
              and holder_subject = :subject
              and municipality_id = :tenant
            order by upper(period), id
            limit :limit
            """)
        .param("subject", holderSubject)
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

  /**
   * Nas reservas dunha entidade (ADR-0039, punto 18) non hai conta de titular coa que comparar: o
   * que fixo a persoa responsable (crear unha reserva extra e cancelar) queda sen a súa conta. O
   * persoal (crear a sesión, cancelar polo concello, cambiar, cobrar) queda.
   */
  int forgetOrganizationEventActors(UUID municipality, Collection<UUID> ids) {
    return jdbc.sql(
            """
            update booking_event set actor_subject = :anonymised
            where booking_id in (:ids)
              and actor_subject <> :anonymised
              and (kind = 'CANCELLED_BY_PERSON'
                   or (kind = 'CREATED'
                       and exists (select 1 from booking b
                                   where b.id = booking_event.booking_id
                                     and b.series_id is null
                                     and b.organization_id is not null
                                     and b.municipality_id = :tenant)))
              and exists (select 1 from booking b
                          where b.id = booking_event.booking_id
                            and b.organization_id is not null
                            and b.municipality_id = :tenant)
              and municipality_id = :tenant
            """)
        .param("anonymised", BookingRetention.ANONYMISED_ACTOR)
        .param("ids", ids)
        .param("tenant", municipality)
        .update();
  }

  /**
   * Quen cancelou (se foi a persoa responsable) e quen fixo a reserva extra dunha entidade, sen a
   * súa conta; quen creou a sesión, o persoal, queda.
   */
  int forgetOrganizationPeople(UUID municipality, Collection<UUID> ids) {
    int cancelled =
        jdbc.sql(
                """
                update booking set cancelled_by = :anonymised
                where id in (:ids) and anonymized_at is null and organization_id is not null
                  and status = 'CANCELLED_BY_PERSON'
                  and municipality_id = :tenant
                """)
            .param("anonymised", BookingRetention.ANONYMISED_ACTOR)
            .param("ids", ids)
            .param("tenant", municipality)
            .update();
    return cancelled
        + jdbc.sql(
                """
                update booking set booked_by = :anonymised
                where id in (:ids) and anonymized_at is null and organization_id is not null
                  and series_id is null
                  and municipality_id = :tenant
                """)
            .param("anonymised", BookingRetention.ANONYMISED_ACTOR)
            .param("ids", ids)
            .param("tenant", municipality)
            .update();
  }

  /** A persoa: conta, nome, correo e teléfono a nulo, coa data. O idioma queda (non identifica). */
  int forgetHolder(UUID municipality, Collection<UUID> ids, Instant now) {
    return jdbc.sql(
            """
            update booking
            set holder_subject = null, holder_name = null, holder_email = null,
                holder_email_key = null, holder_phone = null,
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
