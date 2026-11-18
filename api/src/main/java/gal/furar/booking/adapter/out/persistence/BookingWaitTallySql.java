// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O reconto anónimo das esperas en SQL (ADR-0033, punto 8): uso, franxa e número, sen persoa.
 * Súmase ao vencer as esperas ({@link BookingWaitSql#deletePast}), na mesma transacción. Sempre
 * dentro dunha transacción (RLS) e co concello como parámetro (A8b, {@code
 * BookingWaitTallySqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingWaitTallySql {

  private final JdbcClient jdbc;

  BookingWaitTallySql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Cantas persoas esperaban por ese uso e franxa cando empezou; 0 se ninguén. */
  int waits(UUID municipality, UUID useId, Instant start) {
    return jdbc.sql(
            """
            select coalesce(sum(waits), 0) from booking_wait_tally
            where use_id = :use and slot_start = :since
              and municipality_id = :tenant
            """)
        .param("use", useId)
        .param("since", BookingSql.utc(start))
        .param("tenant", municipality)
        .query(Integer.class)
        .single();
  }

  /**
   * Suma {@code waits} ao reconto dese uso e franxa. En dúas sentenzas (A8b le mellor un insert e
   * un update ca un «on conflict do update»): se a fila xa existe, o {@code insert} non entra, sen
   * abortar a transacción, e súmase co {@code update}, que agarda pola fila se outra pasada a ten.
   */
  void add(UUID municipality, UUID facilityId, UUID useId, Instant start, int waits) {
    int inserted =
        jdbc.sql(
                """
                insert into booking_wait_tally
                  (municipality_id, facility_id, use_id, slot_start, waits)
                values (:tenant, :facility, :use, :since, :waits)
                on conflict do nothing
                """)
            .param("tenant", municipality)
            .param("facility", facilityId)
            .param("use", useId)
            .param("since", BookingSql.utc(start))
            .param("waits", waits)
            .update();
    if (inserted == 0) {
      jdbc.sql(
              """
              update booking_wait_tally set waits = waits + :waits
              where use_id = :use and slot_start = :since and municipality_id = :tenant
              """)
          .param("waits", waits)
          .param("use", useId)
          .param("since", BookingSql.utc(start))
          .param("tenant", municipality)
          .update();
    }
  }

  int deleteBefore(UUID municipality, Instant cutoff) {
    return jdbc.sql(
            """
            delete from booking_wait_tally
            where slot_start < :cutoff and municipality_id = :tenant
            """)
        .param("cutoff", BookingSql.utc(cutoff))
        .param("tenant", municipality)
        .update();
  }
}
