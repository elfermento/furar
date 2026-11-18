// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A reserva manda sobre a súa partida (ADR-0040, punto 6): cancelala (a persoa, o concello ou un
 * cambio da instalación) cancela a partida; movela pola persoa só se a hora nova aínda deixa abrila
 * (se non, 422); movela o persoal péchaa se o prazo novo xa pasou; outro dono péchaa sen correo.
 */
class OpenMatchBookingIT extends AbstractOpenMatchIT {

  record Ready(Facility f, UUID use, String organizer, UUID booking, UUID match) {}

  Ready ready(String prefix, boolean approval) {
    Facility f = facility(prefix, true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID booking = booked(ana, f, use, at(later(), 600));
    return new Ready(f, use, ana, booking, opened(ana, f, booking, 2, 4, 10, approval));
  }

  Api.Response changeByStaff(Facility f, UUID booking, UUID use, Instant start) {
    String manager = TestTokens.manager(f.slug());
    long version =
        api().get(bookings(f.slug()) + "/" + booking).token(manager).send().etagVersion();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    return api()
        .put(bookings(f.slug()) + "/" + booking, body)
        .ifMatch(version)
        .token(manager)
        .send();
  }

  @Test
  void cancellingTheBookingCancelsTheMatchAndDropsTheRequests() {
    Ready r = ready("partida-cancela", true);
    assertThat(enter(person(), r.f(), r.match()).status()).isEqualTo(201);

    assertThat(cancelMine(r.organizer(), r.f(), r.booking()).status()).isEqualTo(200);

    assertThat(matchState(r.match())).isEqualTo("CANCELLED BOOKING_CANCELLED");
    assertThat(players(r.match(), "PENDING")).isZero();
    assertThat(status(r.organizer(), r.f(), r.booking(), "OPEN").status())
        .as("unha cancelada non se reabre")
        .isEqualTo(409);
  }

  @Test
  void theMunicipalityAndAFacilityChangeAlsoCancelIt() {
    Ready byStaff = ready("partida-cancela-concello", false);
    assertThat(cancelByStaff(byStaff.f(), byStaff.booking()).status()).isEqualTo(200);
    assertThat(matchState(byStaff.match())).isEqualTo("CANCELLED BOOKING_CANCELLED");

    Ready byBlock = ready("partida-bloqueo", false);
    Api.Response blocked =
        blockHours(byBlock.f(), later(), 600, 660, List.of(cancelDecision(byBlock.booking())));
    assertThat(blocked.status()).as(blocked.toString()).isEqualTo(201);
    assertThat(matchState(byBlock.match())).isEqualTo("CANCELLED BOOKING_CANCELLED");
  }

  @Test
  void thePersonMovesItOnlyWhereItCanStillBeOpened() {
    Ready r = ready("partida-move", false);

    Api.Response moved =
        changeMine(r.organizer(), r.f(), r.booking(), r.use(), at(later(), 720), 1);
    assertThat(moved.status()).as(moved.toString()).isEqualTo(200);
    assertThat(matchState(r.match())).isEqualTo("OPEN -");
    assertThat(publicPage(r.f(), r.match()).json().get("start").asString())
        .isEqualTo(at(later(), 720).toString());

    // Mañá ás 9:00 con 48 horas de prazo: xa pasou. Non se move.
    jdbc.sql("update municipality set open_match_cancel_hours = 48 where id = :id")
        .param("id", r.f().municipality())
        .update();
    LocalDate tomorrow = tomorrow();
    Api.Response refused =
        changeMine(r.organizer(), r.f(), r.booking(), r.use(), at(tomorrow, OPENS), 1);
    assertThat(problem(refused)).isEqualTo("422 booking.match-too-late");
    assertThat(publicPage(r.f(), r.match()).json().get("start").asString())
        .isEqualTo(at(later(), 720).toString());

    // A un uso por prazas, tampouco.
    UUID seats = id(use(r.f(), "Nado libre", "SEATS", Map.of("capacity", 10)));
    assertThat(problem(changeMine(r.organizer(), r.f(), r.booking(), seats, at(later(), 780), 1)))
        .isEqualTo("422 booking.match-seats-use");
  }

  @Test
  void theStaffMovingItToAPassedDeadlineClosesItInsteadOfCancelling() {
    Ready r = ready("partida-move-persoal", true);
    assertThat(enter(person(), r.f(), r.match()).status()).isEqualTo(201);
    jdbc.sql("update municipality set open_match_cancel_hours = 48 where id = :id")
        .param("id", r.f().municipality())
        .update();

    Api.Response moved = changeByStaff(r.f(), r.booking(), r.use(), at(tomorrow(), OPENS));

    assertThat(moved.status()).as(moved.toString()).isEqualTo(200);
    assertThat(matchState(r.match())).isEqualTo("CLOSED -");
    assertThat(players(r.match(), "PENDING")).isZero();
    assertThat(bookingStatus(r.booking())).isEqualTo("CONFIRMED");
  }

  @Test
  void anotherHolderClosesItWithoutMail() {
    Facility f = facility("partida-titular", true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String xan = "xan-" + UUID.randomUUID();
    Api.Response created = bookFor(f, use, at(later(), 600), "Xan", email(xan));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID booking = id(created.json());
    jdbc.sql("update booking set holder_subject = :s where id = :id")
        .param("s", xan)
        .param("id", booking)
        .update();
    UUID match = opened(person(xan), f, booking, 2, 4, 10, false);

    String manager = TestTokens.manager(f.slug());
    long version =
        api().get(bookings(f.slug()) + "/" + booking).token(manager).send().etagVersion();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", use);
    body.put("start", at(later(), 600).toString());
    body.put("slots", 1);
    body.put(
        "holder", Map.of("name", "Xan", "email", "outro-" + UUID.randomUUID() + "@example.org"));
    Api.Response corrected =
        api().put(bookings(f.slug()) + "/" + booking, body).ifMatch(version).token(manager).send();

    assertThat(corrected.status()).as(corrected.toString()).isEqualTo(200);
    assertThat(matchState(match)).isEqualTo("CLOSED -");
    assertThat(
            count(
                "select count(*) from notification_delivery where kind like 'MATCH_%'"
                    + " and municipality_id = :k",
                f.municipality()))
        .isZero();
  }
}
