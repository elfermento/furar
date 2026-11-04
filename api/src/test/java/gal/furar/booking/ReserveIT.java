// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.adapter.out.qr.ZxingQrEncoderTest;
import gal.furar.booking.domain.QrCode;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.PostgresContainers;
import gal.furar.testsupport.TestTokens;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import tools.jackson.databind.JsonNode;

/**
 * Reservar de punta a punta (plan do dominio 2, D2-05; ADR-0025, puntos 5, 9, 10, 11 e 13): fíos
 * simultáneos ao mesmo oco, partes, prazas, unha praza por persoa, o límite por persoa, cada norma
 * co seu código, un bloqueo que chega mentres se reserva, prezo e prazo conxelados, QR, as miñas
 * reservas, o {@code .ics} e outra persoa.
 */
class ReserveIT extends AbstractBookingIT {

  @Test
  void twentyThreadsOnTheSameWholeSlotMakeOneBookingAndNineteenGetAlternatives() throws Exception {
    Facility f = facility("reservar-20", true);
    UUID use = whole(f, Map.of());
    Instant nine = at(tomorrow(), OPENS);
    List<java.util.concurrent.Callable<Api.Response>> calls = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      String token = person();
      calls.add(() -> reserve(token, f, use, nine));
    }

    List<Api.Response> responses = together(calls);

    assertThat(byStatus(responses)).as(responses.toString()).containsOnlyKeys(201, 409);
    assertThat(byStatus(responses)).containsEntry(201, 1L).containsEntry(409, 19L);
    for (Api.Response r : responses) {
      if (r.status() == 409) {
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertThat(code(r)).isEqualTo("booking.slot-taken");
        assertThat(startMinutes(r.json().get("alternatives")))
            .as("as tres horas libres máis próximas")
            .containsExactly(600, 660, 720);
        assertThat(Instant.parse(r.json().get("alternatives").get(0).get("start").asString()))
            .isEqualTo(at(tomorrow(), 600));
      }
    }
    assertThat(count("select count(*) from booking where facility_id = :k", f.id())).isEqualTo(1);
    assertThat(
            count(
                "select count(*) from booking_occupancy where facility_id = :k and active", f.id()))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from booking_event where booking_id in (select id from"
                    + " booking where facility_id = :k) and kind = 'CREATED'",
                f.id()))
        .isEqualTo(1);
  }

  @Test
  void twoPartsTakeTwoBookingsWhateverPartTheyAskFor() throws Exception {
    Facility f = facility("reservar-partes", true);
    JsonNode created =
        use(
            f,
            "Medio campo",
            "PARTS",
            Map.of("parts", List.of(Map.of("name", "Metade norte"), Map.of("name", "Metade sur"))));
    UUID use = id(created);
    Instant nine = at(tomorrow(), OPENS);
    List<java.util.concurrent.Callable<Api.Response>> calls = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      String token = person();
      calls.add(() -> reserve(token, f, use, nine));
    }

    List<Api.Response> responses = together(calls);

    assertThat(byStatus(responses)).containsEntry(201, 2L).containsEntry(409, 18L);
    List<String> parts =
        responses.stream()
            .filter(r -> r.status() == 201)
            .map(r -> r.json().get("partName").asString())
            .sorted()
            .toList();
    assertThat(parts).containsExactly("Metade norte", "Metade sur");

    // Pedindo unha parte concreta xa collida noutra hora: part-taken; a outra, libre.
    UUID north = UUID.fromString(created.get("parts").get(0).get("id").asString());
    Instant ten = at(tomorrow(), 600);
    assertThat(reserve(person(), f, use, ten, 1, north).status()).isEqualTo(201);
    Api.Response again = reserve(person(), f, use, ten, 1, north);
    assertThat(again.status()).as(again.toString()).isEqualTo(409);
    assertThat(code(again)).isEqualTo("booking.part-taken");
    Api.Response any = reserve(person(), f, use, ten);
    assertThat(any.status()).isEqualTo(201);
    assertThat(any.json().get("partName").asString()).isEqualTo("Metade sur");
  }

  @Test
  void thirtySeatsWithFiftyThreadsMakeThirtyBookings() throws Exception {
    Facility f = facility("reservar-prazas", true);
    UUID use = id(use(f, "Spinning", "SEATS", Map.of("capacity", 30)));
    Instant nine = at(tomorrow(), OPENS);
    List<java.util.concurrent.Callable<Api.Response>> calls = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      String token = person();
      calls.add(() -> reserve(token, f, use, nine));
    }

    List<Api.Response> responses = together(calls);

    assertThat(byStatus(responses)).containsEntry(201, 30L).containsEntry(409, 20L);
    assertThat(
            responses.stream()
                .filter(r -> r.status() == 409)
                .map(ReserveIT::code)
                .distinct()
                .toList())
        .containsExactly("booking.slot-taken");
    assertThat(count("select taken from use_slot where use_id = :k", use)).isEqualTo(30);
    assertThat(count("select count(*) from booking where use_id = :k", use)).isEqualTo(30);
  }

  @Test
  void thePersonCannotHoldTwoSeatsInTheSameSlot() {
    Facility f = facility("reservar-unha-praza", true);
    UUID use = id(use(f, "Spinning", "SEATS", Map.of("capacity", 30)));
    String ana = person();
    Instant nine = at(tomorrow(), OPENS);

    Api.Response first = reserve(ana, f, use, nine);
    Api.Response second = reserve(ana, f, use, nine);
    Api.Response overlapping = reserve(ana, f, use, at(tomorrow(), 600));

    assertThat(first.status()).as(first.toString()).isEqualTo(201);
    assertThat(second.status()).as(second.toString()).isEqualTo(409);
    assertThat(code(second)).isEqualTo("booking.seat-taken-by-you");
    assertThat(overlapping.status()).as("outra hora si").isEqualTo(201);
    assertThat(
            count("select taken from use_slot where use_id = :k order by slot_start limit 1", use))
        .isEqualTo(1);
  }

  @Test
  void twoSimultaneousRequestsOfThePersonDoNotPassTheCap() throws Exception {
    Facility f = facility("reservar-limite", true);
    UUID use = whole(f, Map.of("rules", Map.of("capCount", 1, "capPeriod", "DAY")));
    String ana = person();

    List<Api.Response> responses =
        together(
            List.of(
                () -> reserve(ana, f, use, at(tomorrow(), OPENS)),
                () -> reserve(ana, f, use, at(tomorrow(), 660))));

    assertThat(byStatus(responses)).as(responses.toString()).containsEntry(201, 1L);
    Api.Response refused = responses.stream().filter(r -> r.status() != 201).findFirst().get();
    assertThat(refused.status()).isEqualTo(422);
    assertThat(code(refused)).isEqualTo("booking.cap-reached");
    assertThat(reserve(ana, f, use, at(tomorrow().plusDays(1), OPENS)).status())
        .as("outro día, outro período")
        .isEqualTo(201);
  }

  @Test
  void eachRuleRejectsWithItsCode() {
    Facility f = facility("reservar-normas", true);
    Facility offline = facility("reservar-sen-internet", false);
    UUID soon = whole(f, "Con antelación", Map.of("rules", Map.of("minAdvanceHours", 720)));
    UUID far = whole(f, "Pouco adiante", Map.of("rules", Map.of("maxAdvanceDays", 1)));
    UUID longer = whole(f, "Curta", Map.of("rules", Map.of("maxSlots", 1)));
    int notTomorrow = 127 & ~(1 << (tomorrow().getDayOfWeek().getValue() - 1));
    UUID otherDays = whole(f, "Outros días", Map.of("weekdays", notTomorrow));
    UUID free = whole(f, Map.of());
    UUID offlineUse = whole(offline, Map.of());
    String ana = person();
    Instant nine = at(tomorrow(), OPENS);

    Map<String, Api.Response> results = new LinkedHashMap<>();
    results.put("booking.too-soon", reserve(ana, f, soon, nine));
    results.put("booking.too-far", reserve(ana, f, far, at(tomorrow().plusDays(2), OPENS)));
    results.put("booking.too-long", reserve(ana, f, longer, nine, 2, null));
    results.put("booking.use-not-available", reserve(ana, f, otherDays, nine));
    results.put("booking.slot-invalid", reserve(ana, f, free, at(tomorrow(), OPENS + 30)));
    results.put("booking.facility-not-bookable", reserve(ana, offline, offlineUse, nine));
    results.put(
        "booking.too-soon ", reserve(ana, f, free, at(LocalDate.now(ZONE).minusDays(1), OPENS)));

    results.forEach(
        (expected, response) -> {
          assertThat(response.status()).as(expected + ": " + response).isEqualTo(422);
          assertThat(code(response)).isEqualTo(expected.strip());
        });
    Api.Response tooMany = reserve(ana, f, free, at(tomorrow(), 780), 2, null);
    assertThat(tooMany.status()).as("13:00 + dúas franxas pasa das 14:00").isEqualTo(422);
    assertThat(code(tooMany)).isEqualTo("booking.slot-invalid");
    Api.Response foreign = reserve(ana, f, offlineUse, nine);
    assertThat(foreign.status()).as("un uso doutra instalación").isEqualTo(404);
    assertThat(count("select count(*) from booking where facility_id = :k", f.id())).isZero();
  }

  @Test
  void aBlockCommittedWhileTheBookingWaitsForTheFacilityLockRejectsIt() throws Exception {
    Facility f = facility("reservar-bloqueo", true);
    UUID use = whole(f, Map.of());
    LocalDate day = tomorrow();
    String ana = person();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try (Connection owner =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            PostgresContainers.OWNER_USER,
            PostgresContainers.OWNER_PASSWORD)) {
      owner.setAutoCommit(false);
      // Como fai facility ao crear un bloqueo: primeiro o bloqueo da instalación.
      try (PreparedStatement lock =
          owner.prepareStatement("select id from facility where id = ? for no key update")) {
        lock.setObject(1, f.id());
        lock.executeQuery().close();
      }
      try (PreparedStatement block =
          owner.prepareStatement(
              """
              insert into block
                (id, municipality_id, facility_id, date, start_minute, end_minute, starts_at,
                 ends_at, created_by, created_at, updated_at, version)
              values (?, ?, ?, ?, 540, 600, ?, ?, 'xestion', now(), now(), 0)
              """)) {
        block.setObject(1, UUID.randomUUID());
        block.setObject(2, f.municipality());
        block.setObject(3, f.id());
        block.setObject(4, day);
        block.setTimestamp(5, Timestamp.from(at(day, OPENS)));
        block.setTimestamp(6, Timestamp.from(at(day, 600)));
        block.executeUpdate();
      }

      Future<Api.Response> booking = pool.submit(() -> reserve(ana, f, use, at(day, OPENS)));
      awaitWaitingForALock(booking);
      assertThat(booking.isDone()).as("agarda polo bloqueo da instalación").isFalse();
      owner.commit();

      Api.Response response = booking.get(30, TimeUnit.SECONDS);
      assertThat(response.status()).as(response.toString()).isEqualTo(422);
      assertThat(code(response)).isEqualTo("booking.slot-invalid");
    } finally {
      pool.shutdownNow();
    }
    assertThat(reserve(ana, f, use, at(day, 600)).status()).as("despois do bloqueo").isEqualTo(201);
  }

  /**
   * Ata que unha sesión agarda por un bloqueo (a reserva, detrás do da instalación). Antes de 5 s:
   * despois, {@code lock_timeout} respondería 503 {@code common.busy}.
   */
  void awaitWaitingForALock(Future<Api.Response> booking) throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < until) {
      int waiting =
          jdbc.sql("select count(*) from pg_locks where not granted").query(Integer.class).single();
      if (waiting > 0) {
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError(
        "The booking never waited for the facility lock: "
            + (booking.isDone() ? booking.get() : "still running")
            + " "
            + jdbc.sql(
                    "select state, wait_event_type, wait_event, left(query, 80) from pg_stat_activity")
                .query()
                .listOfRows());
  }

  @Test
  void aBookingFreezesThePriceAndTheDeadlineAndListsItsQrInMyBookings() {
    Facility f = facility("reservar-ver", true);
    JsonNode created =
        use(
            f,
            "Pádel",
            "WHOLE",
            Map.of(
                "price",
                Map.of("kind", "PAID", "amountCents", 800),
                "rules",
                Map.of("cancelHours", 24)));
    UUID use = id(created);
    String subject = "p-" + UUID.randomUUID();
    String ana = person(subject);
    Instant ten = at(tomorrow().plusDays(1), 600);
    List<BookingConfirmed> events = new CopyOnWriteArrayList<>();
    ApplicationListener<ApplicationEvent> listener =
        e -> {
          if (e instanceof PayloadApplicationEvent<?> p
              && p.getPayload() instanceof BookingConfirmed confirmed) {
            events.add(confirmed);
          }
        };
    multicaster.addApplicationListener(listener);
    Api.Response response;
    try {
      response = reserve(ana, f, use, ten, 2, null);
    } finally {
      multicaster.removeApplicationListener(listener);
    }

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    JsonNode b = response.json();
    UUID id = UUID.fromString(b.get("id").asString());
    assertThat(response.header("Location")).endsWith("/me/bookings/" + id);
    assertThat(response.etagVersion()).isZero();
    assertThat(b.get("code").asString()).matches("FUR-[A-HJ-NP-Z2-9]{4}");
    assertThat(b.get("facilityName").asString()).isEqualTo("Pavillón do Alto");
    assertThat(b.get("useName").asString()).isEqualTo("Pádel");
    assertThat(Instant.parse(b.get("start").asString())).isEqualTo(ten);
    assertThat(Instant.parse(b.get("end").asString())).isEqualTo(ten.plusSeconds(7200));
    assertThat(b.get("status").asString()).isEqualTo("CONFIRMED");
    assertThat(b.get("paymentStatus").asString()).isEqualTo("PENDING");
    assertThat(b.get("price").get("kind").asString()).isEqualTo("PAID");
    assertThat(b.get("price").get("amountCents").asInt()).as("800 × 2 franxas").isEqualTo(1600);
    assertThat(Instant.parse(b.get("cancelUntil").asString()))
        .isEqualTo(ten.minusSeconds(24 * 3600));
    assertThat(b.get("canCancel").asBoolean()).isTrue();
    assertThat(b.get("done").asBoolean()).isFalse();
    QrCode qr = new QrCode(b.get("qr").get("size").asInt(), b.get("qr").get("path").asString());
    assertThat(decode(qr)).isEqualTo(b.get("code").asString());
    assertThat(events).containsExactly(new BookingConfirmed(f.municipality(), id));
    Map<String, Object> holder =
        jdbc.sql(
                "select holder_subject, holder_name, holder_email, holder_language from booking"
                    + " where id = :id")
            .param("id", id)
            .query()
            .singleRow();
    assertThat(holder)
        .containsEntry("holder_subject", subject)
        .containsEntry("holder_name", "Persoa " + subject)
        .containsEntry("holder_email", subject + "@example.org")
        .containsEntry("holder_language", "gl");

    // Cambiar a tarifa e as normas do uso non toca a reserva xa feita.
    Map<String, Object> changed = new LinkedHashMap<>();
    changed.put("name", "Pádel");
    changed.put("rentalMode", "WHOLE");
    changed.put("slotMinutes", 60);
    changed.put("availability", "ALWAYS");
    changed.put("price", Map.of("kind", "PAID", "amountCents", 1200));
    changed.put("rules", Map.of("cancelHours", 2));
    Api.Response put =
        api()
            .put(facilities(f.slug()) + "/" + f.id() + "/uses/" + use, changed)
            .ifMatch(created.get("version").asLong())
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(put.status()).as(put.toString()).isEqualTo(200);

    Api.Response detail = api().get(mine(f.slug()) + "/" + id).token(ana).send();
    assertThat(detail.status()).as(detail.toString()).isEqualTo(200);
    assertThat(detail.etagVersion()).isZero();
    assertThat(detail.json().get("price").get("amountCents").asInt()).isEqualTo(1600);
    assertThat(Instant.parse(detail.json().get("cancelUntil").asString()))
        .isEqualTo(ten.minusSeconds(24 * 3600));

    JsonNode upcoming = api().get(mine(f.slug()) + "?scope=UPCOMING").token(ana).send().json();
    assertThat(upcoming.get("totalItems").asInt()).isEqualTo(1);
    assertThat(upcoming.get("items").get(0).get("id").asString()).isEqualTo(id.toString());
    JsonNode history = api().get(mine(f.slug()) + "?scope=HISTORY").token(ana).send().json();
    assertThat(history.get("totalItems").asInt()).isZero();
  }

  @Test
  void myBookingsSplitUpcomingAndHistoryAndTheCalendarIsAnEvent() {
    Facility f = facility("reservar-lista", true);
    UUID use = whole(f, Map.of());
    String subject = "p-" + UUID.randomUUID();
    String ana = person(subject);
    UUID later = id(reserve(ana, f, use, at(tomorrow().plusDays(1), OPENS)).json());
    UUID sooner = id(reserve(ana, f, use, at(tomorrow(), 660)).json());
    UUID past = insertPast(f, use, subject);
    reserve(person(), f, use, at(tomorrow(), OPENS));

    JsonNode upcoming = api().get(mine(f.slug())).token(ana).send().json();
    JsonNode history = api().get(mine(f.slug()) + "?scope=HISTORY").token(ana).send().json();

    assertThat(ids(upcoming)).as("só as súas, da máis próxima").containsExactly(sooner, later);
    assertThat(ids(history)).containsExactly(past);
    assertThat(history.get("items").get(0).get("done").asBoolean()).isTrue();
    assertThat(history.get("items").get(0).get("canCancel").asBoolean()).isFalse();

    Api.Response ics = api().get(mine(f.slug()) + "/" + sooner + "/calendar.ics").token(ana).send();
    assertThat(ics.status()).as(ics.toString()).isEqualTo(200);
    assertThat(ics.contentType()).startsWith("text/calendar");
    assertThat(ics.body())
        .startsWith("BEGIN:VCALENDAR\r\n")
        .contains("UID:" + sooner + "\r\n")
        .contains("SEQUENCE:0\r\n")
        .contains("METHOD:PUBLISH\r\n")
        .contains("SUMMARY:Pista enteira · Pavillón do Alto\r\n");
  }

  @Test
  void anotherPersonSeesNothingAndWithoutAVerifiedEmailNobodyBooks() {
    Facility f = facility("reservar-outra", true);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID id = id(reserve(ana, f, use, at(tomorrow(), OPENS)).json());
    String brais = person();

    Api.Response detail = api().get(mine(f.slug()) + "/" + id).token(brais).send();
    Api.Response ics = api().get(mine(f.slug()) + "/" + id + "/calendar.ics").token(brais).send();
    JsonNode list = api().get(mine(f.slug())).token(brais).send().json();
    Api.Response manager =
        api().get(mine(f.slug()) + "/" + id).token(TestTokens.manager(f.slug())).send();
    Api.Response anonymous = reserve(null, f, use, at(tomorrow(), 600));
    Api.Response unverified =
        reserve(TestTokens.person("p-" + UUID.randomUUID(), false), f, use, at(tomorrow(), 600));

    assertThat(detail.status()).as(detail.toString()).isEqualTo(404);
    assertThat(code(detail)).isEqualTo("booking.not-found");
    assertThat(ics.status()).isEqualTo(404);
    assertThat(list.get("totalItems").asInt()).isZero();
    assertThat(manager.status()).as("o xestor tampouco: non é súa").isEqualTo(403);
    assertThat(anonymous.status()).isEqualTo(401);
    assertThat(unverified.status()).isEqualTo(403);
    assertThat(code(unverified)).isEqualTo("auth.email-not-verified");
  }

  /** Unha reserva da persoa xa rematada, directamente na base (non se pode reservar o pasado). */
  UUID insertPast(Facility f, UUID use, String subject) {
    UUID id = UUID.randomUUID();
    Instant start = at(LocalDate.now(ZONE).minusDays(3), OPENS);
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, rental_mode, period, slots, status,
               payment_status, price_kind, cancel_until, holder_subject, holder_name,
               holder_email, holder_language, created_at, updated_at, version)
            values (:id, :m, 'FUR-PAST', :f, :u, 'WHOLE',
                    tstzrange(:start, :start + interval '1 hour', '[)'), 1, 'CONFIRMED', 'NONE',
                    'NONE', :start, :subject, 'Ana Rey', 'ana@example.org', 'gl', now(), now(), 0)
            """)
        .param("id", id)
        .param("m", f.municipality())
        .param("f", f.id())
        .param("u", use)
        .param("start", start.atOffset(java.time.ZoneOffset.UTC))
        .param("subject", subject)
        .update();
    return id;
  }

  static List<UUID> ids(JsonNode page) {
    List<UUID> ids = new ArrayList<>();
    page.get("items").forEach(i -> ids.add(UUID.fromString(i.get("id").asString())));
    return ids;
  }

  static String decode(QrCode qr) {
    try {
      return ZxingQrEncoderTest.decode(qr);
    } catch (Exception e) {
      throw new AssertionError("The QR code does not decode", e);
    }
  }
}
