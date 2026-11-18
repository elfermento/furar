// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.domain.CancelledWait;
import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As esperas canceladas pendentes de avisar (ADR-0033, revisión do 2026-11-11): só o correo, o
 * idioma e a franxa, ata 7 días. Sempre dentro dunha transacción (RLS) e co concello como parámetro
 * (A8b, {@code BookingWaitCancelledSqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingWaitCancelledSql {

  private final JdbcClient jdbc;

  BookingWaitCancelledSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void insert(UUID municipality, UUID cancellation, CancelledWait wait, Instant now) {
    jdbc.sql(
            """
            insert into booking_wait_cancelled
              (id, municipality_id, cancellation_id, facility_id, email_key, language,
               slot_start, slot_end, cancelled_at)
            values (:id, :tenant, :cancellation, :facility, :emailKey, :language, :since, :until,
                    :now)
            on conflict do nothing
            """)
        .param("id", wait.id())
        .param("tenant", municipality)
        .param("cancellation", cancellation)
        .param("facility", wait.facilityId())
        .param("emailKey", wait.emailKey())
        .param("language", wait.language().code())
        .param("since", BookingSql.utc(wait.start()))
        .param("until", BookingSql.utc(wait.end()))
        .param("now", BookingSql.utc(now))
        .update();
  }

  /** As desa cancelación, por correo e inicio. */
  List<CancelledWait> ofCancellation(UUID municipality, UUID cancellation) {
    return jdbc.sql(
            """
            select id, facility_id, email_key, language, slot_start, slot_end
            from booking_wait_cancelled
            where cancellation_id = :cancellation
              and municipality_id = :tenant
            order by email_key, slot_start, id
            """)
        .param("cancellation", cancellation)
        .param("tenant", municipality)
        .query(
            (rs, n) ->
                new CancelledWait(
                    rs.getObject("id", UUID.class),
                    rs.getObject("facility_id", UUID.class),
                    rs.getString("email_key"),
                    Language.fromCode(rs.getString("language")).orElseThrow(),
                    BookingSql.instant(rs, "slot_start"),
                    BookingSql.instant(rs, "slot_end")))
        .list();
  }

  /** Conservación: as canceladas antes de {@code cutoff}. */
  int deleteBefore(UUID municipality, Instant cutoff) {
    return jdbc.sql(
            """
            delete from booking_wait_cancelled
            where cancelled_at < :cutoff
              and municipality_id = :tenant
            """)
        .param("cutoff", BookingSql.utc(cutoff))
        .param("tenant", municipality)
        .update();
  }
}
