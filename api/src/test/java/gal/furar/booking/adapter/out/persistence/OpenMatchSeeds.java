// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Filas de proba das partidas abertas (ADR-0040): unha reserva confirmada dentro de dous días
 * ({@link AttendanceSeeds}), a súa partida e as persoas que se unen, por JDBC e sen filtro de
 * concello.
 */
final class OpenMatchSeeds {

  private OpenMatchSeeds() {}

  record Seeded(UUID facility, UUID booking, UUID match) {}

  static Seeded match(JdbcClient jdbc, UUID municipality, String prefix, int n) {
    AttendanceSeeds.Seeded booking =
        AttendanceSeeds.booking(
            jdbc, municipality, Instant.now().plusSeconds(2 * 86400), "NONE", prefix, n);
    UUID match = UUID.randomUUID();
    jdbc.sql(
            """
            insert into booking_open_match
              (id, municipality_id, booking_id, organizer_subject, organizer_name, known_count,
               minimum, maximum, approval, status, created_at, updated_at, version)
            values (:id, :m, :b, 'sub-ana', 'Ana Rey', 2, 4, 6, true, 'OPEN', now(), now(), 0)
            """)
        .param("id", match)
        .param("m", municipality)
        .param("b", booking.booking())
        .update();
    return new Seeded(booking.facility(), booking.booking(), match);
  }

  static UUID player(
      JdbcClient jdbc, UUID municipality, UUID match, String subject, String status) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into booking_open_match_player
              (id, municipality_id, match_id, account_subject, email, name, language, status,
               created_at, decided_at)
            values (:id, :m, :match, :s, :e, 'Xan Pérez', 'gl', :status, now(),
                    case when :status = 'PENDING' then null else now() end)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("match", match)
        .param("s", subject)
        .param("e", subject + "@example.org")
        .param("status", status)
        .update();
    return id;
  }
}
