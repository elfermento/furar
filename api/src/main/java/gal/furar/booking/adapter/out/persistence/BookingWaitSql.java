// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.domain.Wait;
import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As esperas en SQL, como o resto das reservas (ADR-0033, puntos 3 a 8): {@code on conflict do
 * nothing} para o límite e {@code delete … returning} para vencer e contar unha soa vez. Sempre
 * dentro dunha transacción (RLS) e co concello como parámetro (A8b, {@code
 * BookingWaitSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingWaitSql {

  private final JdbcClient jdbc;

  BookingWaitSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Garda a espera con ese ordinal. Se o ordinal ou a franxa xa son da conta, non entra e a
   * transacción segue: {@code on conflict do nothing} agarda pola transacción que os ten e non
   * aborta (ADR-0033, punto 4).
   */
  boolean insert(UUID municipality, Wait wait, int ordinal) {
    return jdbc.sql(
            """
            insert into booking_wait
              (id, municipality_id, facility_id, use_id, slot_start, slot_end, account_subject,
               email_key, language, ordinal, created_at)
            values (:id, :tenant, :facility, :use, :since, :until, :subject, :emailKey, :language,
                    :ordinal, :at)
            on conflict do nothing
            returning id
            """)
        .param("id", wait.id())
        .param("tenant", municipality)
        .param("facility", wait.facilityId())
        .param("use", wait.useId())
        .param("since", BookingSql.utc(wait.start()))
        .param("until", BookingSql.utc(wait.end()))
        .param("subject", wait.subject())
        .param("emailKey", wait.emailKey())
        .param("language", wait.language().code())
        .param("ordinal", ordinal)
        .param("at", BookingSql.utc(wait.createdAt()))
        .query(UUID.class)
        .optional()
        .isPresent();
  }

  Optional<Wait> findOfAccount(
      UUID municipality, String subject, UUID useId, Instant start, Instant now) {
    return jdbc.sql(
            """
            select id, facility_id, use_id, slot_start, slot_end, account_subject, email_key,
                   language, created_at
            from booking_wait
            where account_subject = :subject and use_id = :use and slot_start = :since
              and slot_start > :now
              and municipality_id = :tenant
            """)
        .param("subject", subject)
        .param("use", useId)
        .param("since", BookingSql.utc(start))
        .param("now", BookingSql.utc(now))
        .param("tenant", municipality)
        .query(BookingWaitSql::row)
        .optional();
  }

  /** As activas da conta, por inicio. Usa {@code booking_wait_limit_uk} (como moito 3 filas). */
  List<Wait> activeOfAccount(UUID municipality, String subject, Instant now) {
    return jdbc.sql(
            """
            select id, facility_id, use_id, slot_start, slot_end, account_subject, email_key,
                   language, created_at
            from booking_wait
            where account_subject = :subject and slot_start > :now
              and municipality_id = :tenant
            order by slot_start, id
            """)
        .param("subject", subject)
        .param("now", BookingSql.utc(now))
        .param("tenant", municipality)
        .query(BookingWaitSql::row)
        .list();
  }

  int delete(UUID municipality, UUID id, String subject) {
    return jdbc.sql(
            """
            delete from booking_wait
            where id = :id and account_subject = :subject
              and municipality_id = :tenant
            """)
        .param("id", id)
        .param("subject", subject)
        .param("tenant", municipality)
        .update();
  }

  int refreshEmail(UUID municipality, String subject, String emailKey) {
    return jdbc.sql(
            """
            update booking_wait
               set email_key = :emailKey
            where account_subject = :subject and email_key <> :emailKey
              and municipality_id = :tenant
            """)
        .param("emailKey", emailKey)
        .param("subject", subject)
        .param("tenant", municipality)
        .update();
  }

  /** Usa o índice {@code booking_wait_email_idx}. */
  int deleteServed(UUID municipality, String emailKey, UUID useId, Instant start, Instant end) {
    return jdbc.sql(
            """
            delete from booking_wait
            where email_key = :emailKey and use_id = :use
              and slot_start < :until and slot_end > :since
              and municipality_id = :tenant
            """)
        .param("emailKey", emailKey)
        .param("use", useId)
        .param("since", BookingSql.utc(start))
        .param("until", BookingSql.utc(end))
        .param("tenant", municipality)
        .update();
  }

  /** Usa o índice {@code booking_wait_facility_idx}: sen esperas, unha lectura curta. */
  List<Wait> activeOfFacility(
      UUID municipality, UUID facilityId, Instant from, Instant to, Instant now) {
    return jdbc.sql(
            """
            select id, facility_id, use_id, slot_start, slot_end, account_subject, email_key,
                   language, created_at
            from booking_wait
            where facility_id = :facility and slot_start > :now
              and slot_start < :until and slot_end > :since
              and municipality_id = :tenant
            order by slot_start, id
            """)
        .param("facility", facilityId)
        .param("now", BookingSql.utc(now))
        .param("since", BookingSql.utc(from))
        .param("until", BookingSql.utc(to))
        .param("tenant", municipality)
        .query(BookingWaitSql::row)
        .list();
  }

  /**
   * Os borrados de varias filas bloquéanas sempre na mesma orde (por id): dúas transaccións que
   * borren as mesmas esperas (un cambio da instalación e a tarefa que vence as pasadas) agardan
   * unha pola outra en vez de interbloquearse.
   */
  int deleteAll(UUID municipality, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return 0;
    }
    return jdbc.sql(
            """
            delete from booking_wait
            where id in (
                select id from booking_wait
                where id in (:ids) and municipality_id = :tenant
                order by id
                for update)
              and municipality_id = :tenant
            """)
        .param("ids", List.copyOf(ids))
        .param("tenant", municipality)
        .update();
  }

  /**
   * Como {@link #deleteAll}, pero devolve as esperas que borrou de verdade: as que outra
   * transacción xa borrou (a persoa deixou de esperar á vez) non saen.
   */
  List<Wait> deleteAllReturning(UUID municipality, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            delete from booking_wait
            where id in (
                select id from booking_wait
                where id in (:ids) and municipality_id = :tenant
                order by id
                for update)
              and municipality_id = :tenant
            returning id, facility_id, use_id, slot_start, slot_end, account_subject, email_key,
                      language, created_at
            """)
        .param("ids", List.copyOf(ids))
        .param("tenant", municipality)
        .query(BookingWaitSql::row)
        .list();
  }

  List<Wait> ofSlot(
      UUID municipality, UUID useId, Instant start, Instant createdUntil, Instant now) {
    return jdbc.sql(
            """
            select id, facility_id, use_id, slot_start, slot_end, account_subject, email_key,
                   language, created_at
            from booking_wait
            where use_id = :use and slot_start = :since and slot_start > :now
              and created_at <= :created
              and municipality_id = :tenant
            order by id
            """)
        .param("use", useId)
        .param("since", BookingSql.utc(start))
        .param("now", BookingSql.utc(now))
        .param("created", BookingSql.utc(createdUntil))
        .param("tenant", municipality)
        .query(BookingWaitSql::row)
        .list();
  }

  /**
   * Borra as esperas do concello cuxa hora xa empezou e devolve de que uso e franxa era cada unha,
   * para o reconto anónimo (ADR-0033, punto 8). {@code delete … returning}: cada espera sae nunha
   * soa transacción, aínda que dúas pasadas se crucen.
   */
  List<Gone> deletePast(UUID municipality, Instant now) {
    return jdbc.sql(
            """
            delete from booking_wait
            where id in (
                select id from booking_wait
                where slot_start <= :now and municipality_id = :tenant
                order by id
                for update)
              and municipality_id = :tenant
            returning facility_id, use_id, slot_start
            """)
        .param("now", BookingSql.utc(now))
        .param("tenant", municipality)
        .query(BookingWaitSql::gone)
        .list();
  }

  /** Como {@link #deletePast}, só as dunha conta. */
  List<Gone> deletePastOfAccount(UUID municipality, String subject, Instant now) {
    return jdbc.sql(
            """
            delete from booking_wait
            where id in (
                select id from booking_wait
                where slot_start <= :now and account_subject = :subject
                  and municipality_id = :tenant
                order by id
                for update)
              and municipality_id = :tenant
            returning facility_id, use_id, slot_start
            """)
        .param("now", BookingSql.utc(now))
        .param("subject", subject)
        .param("tenant", municipality)
        .query(BookingWaitSql::gone)
        .list();
  }

  /** O uso e a franxa dunha espera vencida: sen persoa. */
  record Gone(UUID facilityId, UUID useId, Instant start) {}

  private static Gone gone(ResultSet rs, int n) throws SQLException {
    return new Gone(
        rs.getObject("facility_id", UUID.class),
        rs.getObject("use_id", UUID.class),
        BookingSql.instant(rs, "slot_start"));
  }

  private static Wait row(ResultSet rs, int n) throws SQLException {
    return new Wait(
        rs.getObject("id", UUID.class),
        rs.getObject("facility_id", UUID.class),
        rs.getObject("use_id", UUID.class),
        BookingSql.instant(rs, "slot_start"),
        BookingSql.instant(rs, "slot_end"),
        rs.getString("account_subject"),
        rs.getString("email_key"),
        Language.fromCode(rs.getString("language")).orElseThrow(),
        BookingSql.instant(rs, "created_at"));
  }
}
