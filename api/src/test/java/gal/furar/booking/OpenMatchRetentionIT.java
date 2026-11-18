// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.service.BookingRetentionService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Conservación das partidas abertas (ADR-0040, punto 13, revisado o 2026-11-16): sen prazo propio,
 * van coa súa reserva. A tarefa diaria das reservas borra a partida (con quen organiza, quen se
 * uniu e as solicitudes) no mesmo lote en que anonimiza a reserva; mentres a reserva se garda, a
 * partida tamén.
 */
class OpenMatchRetentionIT extends AbstractOpenMatchIT {

  @Autowired BookingRetentionService retention;

  void endedDaysAgo(UUID booking, int days) {
    OffsetDateTime start =
        Instant.now().minus(days, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC).minusHours(1);
    jdbc.sql(
            "update booking set period = tstzrange(:from, :to, '[)'), cancel_until = :from,"
                + " updated_at = :to where id = :id")
        .param("from", start)
        .param("to", start.plusHours(1))
        .param("id", booking)
        .update();
  }

  @Test
  void matchesGoWithTheirBooking() {
    Facility f = facility("partida-conserva", true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID old = booked(ana, f, use, at(later(), 600));
    UUID recent = booked(ana, f, use, at(later(), 720));
    UUID oldMatch = opened(ana, f, old, 2, 4, 10, false);
    UUID recentMatch = opened(ana, f, recent, 2, 4, 10, false);
    assertThat(enter(person(), f, oldMatch).status()).isEqualTo(201);
    assertThat(enter(person(), f, recentMatch).status()).isEqualTo(201);
    // Unha, de hai máis do prazo das reservas (P2Y); a outra, de hai un mes (antes, xa se borraba).
    endedDaysAgo(old, 3 * 366);
    endedDaysAgo(recent, 31);

    retention.apply();

    assertThat(count("select count(*) from booking_open_match where id = :k", oldMatch)).isZero();
    assertThat(
            count("select count(*) from booking_open_match_player where match_id = :k", oldMatch))
        .isZero();
    assertThat(
            count("select count(*) from booking where id = :k and anonymized_at is not null", old))
        .as("a reserva anonimízase no mesmo paso")
        .isEqualTo(1);
    assertThat(count("select count(*) from booking_open_match where id = :k", recentMatch))
        .as("a partida segue mentres se garda a súa reserva")
        .isEqualTo(1);
    assertThat(players(recentMatch, "CONFIRMED")).isEqualTo(1);
  }
}
