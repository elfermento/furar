// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * As esperas ante cada cambio da instalación (ADR-0033, punto 5): {@code BookingImpact.apply}
 * chámase sempre, tamén sen reservas afectadas, e borra as esperas das horas que deixan de existir.
 * Cando borra algunha queda un evento {@code booking.waits-removed} só co número e o tipo de
 * cambio; sen reservas afectadas nin esperas non queda rastro nin sae correo.
 */
class WaitFacilityChangeIT extends AbstractWaitIT {

  @Autowired RecordingMailSender mails;

  String url(Facility f) {
    return facilities(f.slug()) + "/" + f.id();
  }

  /** «TipoDeCambio cantas» de cada evento de esperas borradas do concello, en orde. */
  List<String> removed(Facility f) {
    return jdbc.sql(
            """
            select (details ->> 'facilityChange') || ' ' || (details ->> 'count')
            from audit_event
            where municipality_id = :m and action = 'booking.waits-removed' and outcome = 'SUCCESS'
            order by occurred_at, id
            """)
        .param("m", f.municipality())
        .query(String.class)
        .list();
  }

  /** O que garda o evento: só a instalación, o tipo de cambio e o número. */
  String removedDetails(Facility f) {
    return jdbc.sql(
            "select details::text from audit_event where municipality_id = :m"
                + " and action = 'booking.waits-removed'")
        .param("m", f.municipality())
        .query(String.class)
        .single();
  }

  int bookingAudits(Facility f) {
    return count(
        "select count(*) from audit_event where municipality_id = :k and action like 'booking.%'",
        f.municipality());
  }

  /** Unha espera por unha hora que xa quedou libre: quen a ocupaba cancelou e ninguén a colleu. */
  UUID waitOnAFreedHour(String token, Facility f, UUID use, Instant hour) {
    String xan = person();
    UUID booking = booked(xan, f, use, hour);
    UUID wait = waiting(token, f, use, hour);
    assertThat(cancelMine(xan, f, booking).status()).isEqualTo(200);
    return wait;
  }

  /**
   * A administración da plataforma configura instalacións sen poder decidir sobre reservas: un
   * bloqueo seu sen reservas afectadas pero con esperas faise (201, sen 403), borra as esperas e
   * queda rexistrado, sen ningún dato de quen esperaba.
   */
  @Test
  void aPlatformBlockWithoutBookingsRemovesTheWaitsAndLeavesATrace() {
    Facility f = facility("cambio-plataforma", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    String sub = "ana-" + UUID.randomUUID();
    waitOnAFreedHour(person(sub), f, use, at(day, 600));
    waitOnAFreedHour(person(), f, use, at(day, 660));
    UUID outside = waitOnAFreedHour(person(sub), f, use, at(day, 780));
    int before = bookingAudits(f);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", day.toString());
    body.put("startMinute", 570);
    body.put("endMinute", 720);
    body.put("reason", "Mantemento");

    List<Api.Response> created = new ArrayList<>();
    List<BookingSlotReleased> events =
        released(
            () ->
                created.add(
                    api().post(url(f) + "/blocks", body).token(TestTokens.platformAdmin()).send()));

    assertThat(created.getFirst().status()).as(created.getFirst().toString()).isEqualTo(201);
    assertThat(myWaits(person(sub), f)).containsExactly(outside);
    assertThat(waitsIn(f)).isEqualTo(1);
    assertThat(tallyIn(f)).isZero();
    assertThat(events).isEmpty();
    assertThat(removed(f)).containsExactly("Block 2");
    assertThat(bookingAudits(f)).isEqualTo(before + 1);
    assertThat(removedDetails(f))
        .contains(f.id().toString())
        .doesNotContain(sub, "example.org", "ana");
  }

  /** Sen reservas afectadas nin esperas: nin evento de reservas, nin aviso, nin correo. */
  @Test
  void aChangeWithoutBookingsOrWaitsLeavesNoTraceAndSendsNothing() throws Exception {
    Facility f = facility("cambio-nada", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    // Unha espera fóra do bloqueo non conta.
    waitOnAFreedHour(person(), f, use, at(day, 780));
    settle();
    int audits = bookingAudits(f);
    int sent = mails.sent().size();

    List<BookingSlotReleased> events =
        released(() -> assertThat(blockHours(f, day, 570, 720, null).status()).isEqualTo(201));

    settle();
    assertThat(events).isEmpty();
    assertThat(removed(f)).isEmpty();
    assertThat(bookingAudits(f)).isEqualTo(audits);
    assertThat(mails.sent()).hasSize(sent);
    assertThat(waitsIn(f)).isEqualTo(1);
  }

  /** Un cambio do uso que toca as súas reservas (outra duración), sen reservas que o impidan. */
  @Test
  void changingTheUseRemovesItsWaits() {
    Facility f = facility("cambio-uso", true);
    UUID padel = whole(f, "Pádel", Map.of());
    UUID tennis = whole(f, "Tenis", Map.of());
    Instant ten = at(tomorrow(), 600);
    // A hora de tenis está ocupada por unha reserva de pádel: o tenis non ten reservas.
    booked(person(), f, padel, ten);
    waiting(person(), f, tennis, ten);
    String ana = person();
    UUID kept = waitOnAFreedHour(ana, f, padel, at(tomorrow(), 720));
    String manager = TestTokens.manager(f.slug());
    long version = api().get(url(f) + "/uses/" + tennis).token(manager).send().etagVersion();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Tenis");
    body.put("rentalMode", "WHOLE");
    body.put("slotMinutes", 30);
    body.put("availability", "ALWAYS");

    Api.Response changed =
        api().put(url(f) + "/uses/" + tennis, body).ifMatch(version).token(manager).send();

    assertThat(changed.status()).as(changed.toString()).isEqualTo(200);
    assertThat(myWaits(ana, f)).containsExactly(kept);
    assertThat(waitsIn(f)).isEqualTo(1);
    assertThat(removed(f)).containsExactly("UseChanged 1");
    assertThat(tallyIn(f)).isZero();
  }

  /**
   * Acurtar as datas dun uso (que se pode facer sempre) borra as esperas dos días que quedan fóra,
   * coma un bloqueo, e conserva as dos días que seguen valendo.
   */
  @Test
  void shorteningTheDatesOfAUseRemovesTheWaitsOfTheDaysLeftOut() {
    Facility f = facility("cambio-datas", true);
    UUID use = whole(f, Map.of());
    LocalDate inside = tomorrow();
    LocalDate outside = tomorrow().plusDays(3);
    String ana = person();
    UUID kept = waitOnAFreedHour(ana, f, use, at(inside, 600));
    waitOnAFreedHour(ana, f, use, at(outside, 600));
    // E unha espera cunha reserva que segue: a reserva queda, a espera non.
    booked(person(), f, use, at(outside, 660));
    waiting(person(), f, use, at(outside, 660));
    String manager = TestTokens.manager(f.slug());
    long version = api().get(url(f) + "/uses/" + use).token(manager).send().etagVersion();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Pista enteira");
    body.put("rentalMode", "WHOLE");
    body.put("slotMinutes", 60);
    body.put("availability", "DATES");
    body.put(
        "periods", List.of(Map.of("from", inside.toString(), "to", inside.plusDays(1).toString())));
    body.put("weekdays", 127);

    List<Api.Response> saved = new ArrayList<>();
    List<BookingSlotReleased> events =
        released(
            () ->
                saved.add(
                    api()
                        .put(url(f) + "/uses/" + use, body)
                        .ifMatch(version)
                        .token(manager)
                        .send()));

    assertThat(saved.getFirst().status()).as(saved.getFirst().toString()).isEqualTo(200);
    assertThat(myWaits(ana, f)).containsExactly(kept);
    assertThat(waitsIn(f)).isEqualTo(1);
    assertThat(removed(f)).containsExactly("UseDatesChanged 2");
    assertThat(events).isEmpty();
    assertThat(tallyIn(f)).isZero();
    assertThat(
            count(
                "select count(*) from booking where facility_id = :k and status = 'CONFIRMED'",
                f.id()))
        .as("as reservas quedan: as datas dun uso cámbianse sempre")
        .isEqualTo(1);
    // Cambiar outra cousa (o nome) non borra nada.
    body.put("name", "Pista");
    assertThat(
            api()
                .put(url(f) + "/uses/" + use, body)
                .ifMatch(saved.getFirst().etagVersion())
                .token(manager)
                .send()
                .status())
        .isEqualTo(200);
    assertThat(waitsIn(f)).isEqualTo(1);
    assertThat(removed(f)).hasSize(1);
  }

  /** Unha tempada que perde horas e unha excepción que pecha o día, sen reservas afectadas. */
  @Test
  void aShorterSeasonAndAClosedDayRemoveTheWaitsOfTheLostHours() {
    Facility f = facility("cambio-horario", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    LocalDate later = tomorrow().plusDays(2);
    String ana = person();
    UUID morning = waitOnAFreedHour(ana, f, use, at(day, 540));
    waitOnAFreedHour(ana, f, use, at(day, 780));
    UUID otherDay = waitOnAFreedHour(ana, f, use, at(later, 600));
    String manager = TestTokens.manager(f.slug());

    // A tempada pasa a pechar ás 12:00: pérdense as 12:00 e as 13:00 de todos os días.
    var season = api().get(url(f) + "/schedule").token(manager).send().json().get("seasons").get(0);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Todo o ano");
    body.put("startDate", "2026-01-01");
    body.put("endDate", "2030-12-31");
    List<Map<String, Object>> week = new ArrayList<>();
    DAYS.forEach(
        d ->
            week.add(
                Map.of(
                    "day",
                    d,
                    "intervals",
                    List.of(Map.of("startMinute", OPENS, "endMinute", 720)))));
    body.put("week", week);
    Api.Response shorter =
        api()
            .put(url(f) + "/seasons/" + season.get("id").asString(), body)
            .ifMatch(season.get("version").asLong())
            .token(manager)
            .send();
    assertThat(shorter.status()).as(shorter.toString()).isEqualTo(200);
    assertThat(myWaits(ana, f)).containsExactly(morning, otherDay);
    assertThat(removed(f)).containsExactly("ScheduleChanged 1");

    // Unha excepción pecha o outro día enteiro.
    Map<String, Object> closed = new LinkedHashMap<>();
    closed.put("date", later.toString());
    closed.put("open", false);
    closed.put("reason", "Obras");
    Api.Response exception =
        api().post(url(f) + "/schedule-exceptions", closed).token(manager).send();
    assertThat(exception.status()).as(exception.toString()).isEqualTo(201);
    assertThat(myWaits(ana, f)).containsExactly(morning);
    assertThat(removed(f)).containsExactly("ScheduleChanged 1", "ScheduleChanged 1");
    assertThat(tallyIn(f)).isZero();
  }

  /** Dar de baixa a instalación, sen reservas que decidir: todas as súas esperas, fóra. */
  @Test
  void withdrawingTheFacilityRemovesAllItsWaits() {
    Facility f = facility("cambio-baixa", true);
    Facility other = facility(f.slug(), f.municipality(), "Pista da Gándara", true);
    UUID use = whole(f, Map.of());
    UUID otherUse = whole(other, Map.of());
    String ana = person();
    waitOnAFreedHour(ana, f, use, at(tomorrow(), 600));
    waitOnAFreedHour(ana, f, use, at(tomorrow().plusDays(5), 660));
    UUID elsewhere = waitOnAFreedHour(ana, other, otherUse, at(tomorrow(), 600));

    Api.Response withdrawn =
        api()
            .put(url(f) + "/status", Map.of("status", "WITHDRAWN"))
            .ifMatch(version(f.slug(), f.id()))
            .token(TestTokens.manager(f.slug()))
            .send();

    assertThat(withdrawn.status()).as(withdrawn.toString()).isEqualTo(200);
    assertThat(myWaits(ana, f)).containsExactly(elsewhere);
    assertThat(removed(f)).containsExactly("Withdrawal 2");
    assertThat(tallyIn(f)).isZero();
  }

  /**
   * Un bloqueo parcial sobre unha reserva de dúas franxas: cancélase enteira; a espera da franxa
   * bloqueada bórrase e a da franxa que queda fóra do bloqueo recibe o aviso.
   */
  @Test
  void aPartialBlockOverATwoSlotBookingNotifiesTheWaitOfTheSlotLeftOutside() {
    Facility f = facility("cambio-parcial", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    Api.Response booked = reserve(person(), f, use, at(day, 600), 2, null);
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    UUID booking = id(booked.json());
    String ana = person();
    String bea = person();
    UUID outside = waiting(ana, f, use, at(day, 600));
    waiting(bea, f, use, at(day, 660));

    List<Api.Response> blocked = new ArrayList<>();
    List<BookingSlotReleased> events =
        released(() -> blocked.add(blockHours(f, day, 660, 720, List.of(cancelDecision(booking)))));

    assertThat(blocked.getFirst().status()).as(blocked.getFirst().toString()).isEqualTo(201);
    assertThat(events).extracting(BookingSlotReleased::start).containsExactly(at(day, 600));
    assertThat(myWaits(ana, f)).containsExactly(outside);
    assertThat(myWaits(bea, f)).isEmpty();
    assertThat(removed(f)).containsExactly("Block 1");
  }
}
