// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractBookingIT;
import gal.furar.event.application.service.WaitlistTask;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.RecordingMailSender;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Actividades con horario de punta a punta (ADR-0041): a vista previa cos choques, crear sen pisar
 * unha reserva da veciñanza (as sesións son reservas da actividade), cancelar unha sesión e rematar
 * antes (soltan as horas), cancelar a reserva dunha sesión desde as reservas, a lista de espera en
 * fervenza, as persoas a cargo e a idade, pasar lista e as sesións de hoxe, os permisos e a baixa
 * da conta.
 */
class ActivityIT extends AbstractBookingIT {

  @Autowired RecordingMailSender mails;
  @Autowired WaitlistTask waitlistTask;

  static String base(String slug) {
    return "/api/v1/municipalities/" + slug + "/events";
  }

  static void await(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
    while (!condition.getAsBoolean()) {
      assertThat(Instant.now()).as("agardando").isBefore(deadline);
      Thread.sleep(50);
    }
  }

  /** Cada semana o día de mañá, 10:00–11:00, tres semanas. */
  static Map<String, Object> schedule(@Nullable UUID use, List<LocalDate> skip) {
    LocalDate first = tomorrow();
    Map<String, Object> s = new HashMap<>();
    s.put("useId", use == null ? null : use.toString());
    s.put("weekdays", List.of(first.getDayOfWeek().getValue()));
    s.put("startMinute", 600);
    s.put("endMinute", 660);
    s.put("firstDate", first.toString());
    s.put("lastDate", first.plusDays(14).toString());
    s.put("skip", skip.stream().map(LocalDate::toString).toList());
    return s;
  }

  static Map<String, Object> body(
      Facility f, @Nullable UUID use, @Nullable Integer capacity, List<LocalDate> skip) {
    Map<String, Object> body = new HashMap<>();
    body.put("nameGl", "Escola de baloncesto");
    body.put("nameEs", "Escuela de baloncesto");
    body.put("kind", "SCHEDULED");
    if (use == null) {
      body.put("place", "Parque");
    } else {
      body.put("facilityId", f.id().toString());
    }
    body.put("schedule", schedule(use, skip));
    body.put("capacity", capacity);
    body.put("priceCents", 1500);
    body.put("pricePeriod", "MONTH");
    body.put("published", true);
    body.put("approval", false);
    return body;
  }

  UUID created(Facility f, Map<String, Object> body) {
    Api.Response created =
        api().post(base(f.slug()) + "/manage", body).token(TestTokens.manager(f.slug())).send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return UUID.fromString(created.json().get("id").asString());
  }

  JsonNode sessions(Facility f, UUID event) {
    Api.Response list =
        api()
            .get(base(f.slug()) + "/manage/" + event + "/sessions")
            .token(TestTokens.staff(f.slug()))
            .send();
    assertThat(list.status()).as(list.toString()).isEqualTo(200);
    return list.json();
  }

  int bookingsOf(UUID event, String status) {
    return jdbc.sql("select count(*) from booking where activity_id = :e and status = :s")
        .param("e", event)
        .param("s", status)
        .query(Integer.class)
        .single();
  }

  @Test
  void theConflictsAreToldFirstAndAResidentBookingIsNeverTrodOn() throws Exception {
    Facility f = facility("act-crea", true);
    UUID use = whole(f, Map.of());
    assertThat(reserve(person(), f, use, at(tomorrow(), 600)).status()).isEqualTo(201);

    Api.Response preview =
        api()
            .post(
                base(f.slug()) + "/manage/sessions-preview",
                Map.of("facilityId", f.id().toString(), "schedule", schedule(use, List.of())))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(preview.status()).as(preview.toString()).isEqualTo(200);
    assertThat(preview.json().get("sessions").asInt()).isEqualTo(2);
    assertThat(preview.json().get("conflicts").get(0).get("kind").asString()).isEqualTo("BOOKING");

    Api.Response stale =
        api()
            .post(base(f.slug()) + "/manage", body(f, use, 10, List.of()))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(code(stale)).isEqualTo("event.sessions-changed");

    UUID event = created(f, body(f, use, 10, List.of(tomorrow())));
    assertThat(sessions(f, event)).hasSize(2);
    assertThat(bookingsOf(event, "CONFIRMED")).isEqualTo(2);
    // A hora da sesión xa non se pode reservar.
    assertThat(reserve(person(), f, use, at(tomorrow().plusDays(7), 600)).status()).isEqualTo(409);

    // Cancelar unha sesión solta a súa hora.
    String sessionId = sessions(f, event).get(0).get("id").asString();
    Api.Response cancelled =
        api()
            .post(base(f.slug()) + "/manage/" + event + "/sessions/" + sessionId + "/cancel", null)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    assertThat(bookingsOf(event, "CONFIRMED")).isEqualTo(1);

    // Rematar desde a última data: a outra tamén.
    Api.Response ended =
        api()
            .post(
                base(f.slug()) + "/manage/" + event + "/end",
                Map.of("from", tomorrow().plusDays(14).toString()))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(ended.status()).as(ended.toString()).isEqualTo(200);
    assertThat(bookingsOf(event, "CONFIRMED")).isZero();
    assertThat(ended.json().get("schedule").get("endedFrom").asString())
        .isEqualTo(tomorrow().plusDays(14).toString());
  }

  @Test
  void cancellingTheBookingOfASessionCancelsTheSession() throws Exception {
    Facility f = facility("act-sync", true);
    UUID use = whole(f, Map.of());
    UUID event = created(f, body(f, use, 10, List.of()));
    UUID booking =
        jdbc.sql("select id from booking where activity_id = :e order by lower(period) limit 1")
            .param("e", event)
            .query(UUID.class)
            .single();
    String manager = TestTokens.manager(f.slug());
    String bookings = "/api/v1/municipalities/" + f.slug() + "/bookings/" + booking;
    long version = api().get(bookings).token(manager).send().etagVersion();
    Api.Response change =
        api()
            .post(bookings + "/cancel", Map.of("channels", List.of()))
            .ifMatch(version)
            .token(manager)
            .send();
    assertThat(change.status()).as(change.toString()).isEqualTo(200);
    await(() -> !sessions(f, event).get(0).get("cancelledAt").isNull());
  }

  @Test
  void theWaitlistTellsTheFirstAndTheFirstToConfirmTakesTheSeat() throws Exception {
    Facility f = facility("act-espera", true);
    UUID event = created(f, body(f, null, 1, List.of()));
    String holder = "act-" + UUID.randomUUID();
    String first = "act-" + UUID.randomUUID();
    String second = "act-" + UUID.randomUUID();
    String url = base(f.slug()) + "/" + event + "/registration";
    assertThat(api().post(url, Map.of()).token(person(holder)).send().status()).isEqualTo(201);
    JsonNode waiting = api().post(url, Map.of()).token(person(first)).send().json();
    assertThat(waiting.get("status").asString()).isEqualTo("WAITING");
    assertThat(waiting.get("position").asInt()).isEqualTo(1);
    api().post(url, Map.of()).token(person(second)).send();

    assertThat(api().delete(url).token(person(holder)).send().status()).isEqualTo(204);
    JsonNode told = api().get(url).token(person(first)).send().json();
    assertThat(told.get("status").asString()).isEqualTo("NOTIFIED");
    assertThat(told.get("exclusiveUntil").isNull()).isFalse();
    assertThat(api().get(url).token(person(second)).send().json().get("status").asString())
        .isEqualTo("WAITING");
    // Quen non está na lista non colle a praza libre.
    JsonNode card = api().get(base(f.slug()) + "/" + event).send().json();
    assertThat(card.get("open").asBoolean()).isFalse();
    assertThat(card.get("waitlist").asBoolean()).isTrue();

    Api.Response confirmed = api().post(url + "/confirm", null).token(person(first)).send();
    assertThat(confirmed.status()).as(confirmed.toString()).isEqualTo(200);
    assertThat(confirmed.json().get("status").asString()).isEqualTo("CONFIRMED");
    waitlistTask.apply();
    await(() -> !mails.sentTo(first + "@example.org").isEmpty());
  }

  @Test
  void dependentsAgeAndAttendance() throws Exception {
    Facility f = facility("act-lista", true);
    Map<String, Object> limited = body(f, null, 10, List.of());
    limited.put("birthYearFrom", 1950);
    limited.put("birthYearTo", 2010);
    UUID event = created(f, limited);
    String url = base(f.slug()) + "/" + event + "/registration";
    String ana = "act-" + UUID.randomUUID();
    assertThat(code(api().post(url, Map.of()).token(person(ana)).send()))
        .isEqualTo("event.birth-year-required");
    assertThat(code(api().post(url, Map.of("birthDate", "2015-03-01")).token(person(ana)).send()))
        .isEqualTo("event.age-not-allowed");
    assertThat(
            api().post(url, Map.of("birthDate", "1980-03-01")).token(person(ana)).send().status())
        .isEqualTo(201);
    JsonNode people =
        api()
            .get(base(f.slug()) + "/manage/" + event + "/registrations")
            .token(TestTokens.staff(f.slug()))
            .send()
            .json();
    assertThat(people.get(0).get("birthDate").asString()).isEqualTo("1980-03-01");
    assertThat(people.get(0).get("birthYear").asInt()).isEqualTo(1980);

    JsonNode card = api().get(base(f.slug()) + "/" + event).send().json();
    assertThat(card.get("nextSession").get("date").asString()).isEqualTo(tomorrow().toString());
    assertThat(api().get(url).token(person(ana)).send().json().get("nextSessions")).hasSize(3);
    String sessionId = sessions(f, event).get(0).get("id").asString();
    String list = base(f.slug()) + "/manage/" + event + "/sessions/" + sessionId + "/attendance";
    JsonNode open = api().get(list).token(TestTokens.staff(f.slug())).send().json();
    assertThat(open.get("taken").asBoolean()).isFalse();
    assertThat(open.get("attendees").get(0).get("present").asBoolean()).isTrue();
    // A sesión é mañá: aínda é cedo.
    assertThat(
            code(
                api()
                    .put(list, Map.of("marks", List.of()))
                    .token(TestTokens.staff(f.slug()))
                    .send()))
        .isEqualTo("event.attendance-too-early");
    assertThat(api().get(list).token(TestTokens.platformAdmin()).send().status()).isEqualTo(403);
    Api.Response today =
        api()
            .get(base(f.slug()) + "/manage/sessions/today")
            .token(TestTokens.staff(f.slug()))
            .send();
    assertThat(today.status()).isEqualTo(200);
  }

  @Test
  void closingTheAccountRemovesItsWaitsAndOngoingRegistrations() {
    Facility f = facility("act-baixa", true);
    UUID event = created(f, body(f, null, 1, List.of()));
    String url = base(f.slug()) + "/" + event + "/registration";
    String holder = "act-" + UUID.randomUUID();
    String gone = "act-" + UUID.randomUUID();
    api().post(url, Map.of()).token(person(holder)).send();
    api().post(url, Map.of()).token(person(gone)).send();
    jdbc.sql("select close_account(:s)").param("s", gone).query(Object.class).list();
    assertThat(
            jdbc.sql("select count(*) from event_registration where event_id = :e")
                .param("e", event)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }
}
