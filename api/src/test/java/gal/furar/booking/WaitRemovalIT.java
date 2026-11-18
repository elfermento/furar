// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cando se borra unha espera sen que a persoa o pida (ADR-0033, punto 5): o seu correo consegue
 * reserva nesa franxa por calquera vía, ou a súa hora deixa de existir. Nunca conta no reconto.
 */
class WaitRemovalIT extends AbstractWaitIT {

  @Test
  void bookingTheHourByAnyRouteRemovesTheWait() {
    Facility f = facility("espera-atendida", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    String xan = person();
    String sub = "ana-" + UUID.randomUUID();
    String ana = person(sub);

    // Por internet.
    UUID first = booked(xan, f, use, at(day, 540));
    waiting(ana, f, use, at(day, 540));
    assertThat(cancelMine(xan, f, first).status()).isEqualTo(200);
    booked(ana, f, use, at(day, 540));
    assertThat(myWaits(ana, f)).isEmpty();

    // No mostrador, a nome dese correo escrito doutro xeito.
    UUID second = booked(xan, f, use, at(day, 600));
    waiting(ana, f, use, at(day, 600));
    assertThat(cancelMine(xan, f, second).status()).isEqualTo(200);
    Api.Response counter = bookFor(f, use, at(day, 600), "Ana Rey", email(sub).toUpperCase());
    assertThat(counter.status()).as(counter.toString()).isEqualTo(201);
    assertThat(myWaits(ana, f)).isEmpty();

    // Cun cambio a esa hora.
    UUID third = booked(xan, f, use, at(day, 660));
    UUID mine = booked(ana, f, use, at(day, 720));
    waiting(ana, f, use, at(day, 660));
    assertThat(cancelMine(xan, f, third).status()).isEqualTo(200);
    assertThat(changeMine(ana, f, mine, use, at(day, 660), 1).status()).isEqualTo(200);
    assertThat(myWaits(ana, f)).isEmpty();

    assertThat(tallyIn(f)).as("atendida: non conta").isZero();
  }

  /** A reserva doutra persoa nesa hora non toca a espera. */
  @Test
  void anotherPersonBookingTheHourKeepsTheWait() {
    Facility f = facility("espera-doutra", true);
    UUID use = whole(f, Map.of());
    Instant ten = at(tomorrow(), 600);
    String xan = person();
    UUID booking = booked(xan, f, use, ten);
    String ana = person();
    UUID wait = waiting(ana, f, use, ten);

    assertThat(cancelMine(xan, f, booking).status()).isEqualTo(200);
    booked(person(), f, use, ten);

    assertThat(myWaits(ana, f)).containsExactly(wait);
  }

  @Test
  void aBlockRemovesOnlyTheWaitsItCovers() {
    Facility f = facility("espera-bloqueo", true);
    UUID padel = whole(f, "Pádel", Map.of());
    UUID tennis = whole(f, "Tenis", Map.of());
    LocalDate day = tomorrow();
    String xan = person();
    // As horas están ocupadas polo outro uso: bloquear non afecta a ningunha reserva de pádel.
    UUID kept = booked(xan, f, tennis, at(day, 540));
    UUID hit = booked(xan, f, tennis, at(day, 660));
    String ana = person();
    UUID outside = waiting(ana, f, padel, at(day, 540));
    waiting(ana, f, padel, at(day, 660));

    Api.Response blocked =
        blockHours(f, day, 630, 720, List.of(Map.of("bookingId", hit, "action", "KEEP")));

    assertThat(blocked.status()).as(blocked.toString()).isEqualTo(201);
    assertThat(myWaits(ana, f)).containsExactly(outside);
    assertThat(tallyIn(f)).isZero();
    // A reserva mantívose dentro do bloqueo; a espera por esa hora bórrase e queda rexistrado.
    assertThat(count("select count(*) from booking where id = :k and status = 'CONFIRMED'", hit))
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select (details ->> 'facilityChange') || ' ' || (details ->> 'count')"
                        + " from audit_event where municipality_id = :m"
                        + " and action = 'booking.waits-removed'")
                .param("m", f.municipality())
                .query(String.class)
                .list())
        .containsExactly("Block 1");
    assertThat(kept).isNotNull();
  }

  /** Sen reservas afectadas tamén: {@code BookingImpact.apply} chámase con todo cambio. */
  @Test
  void archivingTheUseOrClosingTheFacilityRemovesItsWaits() {
    Facility f = facility("espera-arquivo", true);
    UUID padel = whole(f, "Pádel", Map.of());
    UUID tennis = whole(f, "Tenis", Map.of());
    LocalDate day = tomorrow();
    booked(person(), f, tennis, at(day, 600));
    String ana = person();
    waiting(ana, f, padel, at(day, 600));
    String bea = person();
    String manager = TestTokens.manager(f.slug());
    String url = facilities(f.slug()) + "/" + f.id();

    // Arquivar o uso polo que se espera (non ten reservas: non pregunta nada).
    long useVersion = api().get(url + "/uses/" + padel).token(manager).send().etagVersion();
    Api.Response archived =
        api()
            .post(url + "/uses/" + padel + "/archive", Map.of())
            .ifMatch(useVersion)
            .token(manager)
            .send();
    assertThat(archived.status()).as(archived.toString()).isEqualTo(200);
    assertThat(myWaits(ana, f)).isEmpty();

    // Pechar a instalación mantendo a reserva: as esperas que queden, fóra.
    UUID golf = whole(f, "Golf", Map.of());
    UUID wait = waiting(bea, f, golf, at(day, 600));
    assertThat(myWaits(bea, f)).containsExactly(wait);
    UUID booking =
        jdbc.sql("select id from booking where use_id = :u")
            .param("u", tennis)
            .query(UUID.class)
            .single();
    Api.Response closed =
        api()
            .put(
                url + "/status",
                Map.of(
                    "status",
                    "TEMPORARILY_CLOSED",
                    "decisions",
                    List.of(Map.of("bookingId", booking, "action", "KEEP"))))
            .ifMatch(version(f.slug(), f.id()))
            .token(manager)
            .send();
    assertThat(closed.status()).as(closed.toString()).isEqualTo(200);
    assertThat(waitsIn(f)).isZero();
    assertThat(tallyIn(f)).isZero();
  }
}
