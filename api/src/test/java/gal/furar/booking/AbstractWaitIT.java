// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import tools.jackson.databind.JsonNode;

/** Axudas das probas da lista de espera (ADR-0033): esperar, deixar de esperar e soltar horas. */
public abstract class AbstractWaitIT extends AbstractAttendanceIT {

  protected static String waits(String slug) {
    return "/api/v1/municipalities/" + slug + "/me/booking-waits";
  }

  protected Api.Response join(String token, Facility f, UUID use, Instant start) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    Api.Call call = api().post(waits(f.slug()), body);
    return token == null ? call.send() : call.token(token).send();
  }

  /** Espera e devolve o id da espera. */
  protected UUID waiting(String token, Facility f, UUID use, Instant start) {
    Api.Response joined = join(token, f, use, start);
    assertThat(joined.status()).as(joined.toString()).isEqualTo(201);
    return id(joined.json());
  }

  protected Api.Response leave(String token, Facility f, UUID wait) {
    return api().delete(waits(f.slug()) + "/" + wait).token(token).send();
  }

  protected List<UUID> myWaits(String token, Facility f) {
    Api.Response found = api().get(waits(f.slug())).token(token).send();
    assertThat(found.status()).as(found.toString()).isEqualTo(200);
    List<UUID> ids = new ArrayList<>();
    found.json().forEach(w -> ids.add(id(w)));
    return ids;
  }

  /** Reserva e devolve o id. */
  protected UUID booked(String token, Facility f, UUID use, Instant start) {
    Api.Response booked = reserve(token, f, use, start);
    assertThat(booked.status()).as(booked.toString()).isEqualTo(201);
    return id(booked.json());
  }

  protected Api.Response cancelMine(String token, Facility f, UUID booking) {
    long version = api().get(mine(f.slug()) + "/" + booking).token(token).send().etagVersion();
    return api()
        .post(mine(f.slug()) + "/" + booking + "/cancel", null)
        .ifMatch(version)
        .token(token)
        .send();
  }

  protected Api.Response changeMine(
      String token, Facility f, UUID booking, UUID use, Instant start, int slots) {
    long version = api().get(mine(f.slug()) + "/" + booking).token(token).send().etagVersion();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", slots);
    return api()
        .put(mine(f.slug()) + "/" + booking + "/slot", body)
        .ifMatch(version)
        .token(token)
        .send();
  }

  protected Api.Response cancelByStaff(Facility f, UUID booking) {
    String manager = TestTokens.manager(f.slug());
    long version =
        api().get(bookings(f.slug()) + "/" + booking).token(manager).send().etagVersion();
    return api()
        .post(bookings(f.slug()) + "/" + booking + "/cancel", Map.of("channels", List.of()))
        .ifMatch(version)
        .token(manager)
        .send();
  }

  /** Un bloqueo da instalación ese día, coas decisións sobre as reservas afectadas. */
  protected Api.Response blockHours(
      Facility f, LocalDate date, int start, int end, List<Object> decisions) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", date.toString());
    body.put("startMinute", start);
    body.put("endMinute", end);
    body.put("reason", "Mantemento");
    if (decisions != null) {
      body.put("decisions", decisions);
    }
    return api()
        .post(facilities(f.slug()) + "/" + f.id() + "/blocks", body)
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  protected static Map<String, Object> cancelDecision(UUID booking) {
    return Map.of("bookingId", booking, "action", "CANCEL", "channels", List.of("WEB"));
  }

  /** Bloquea ese correo por faltas neste concello, coa penalización acesa. */
  protected void blockByNoShows(Facility f, String email) {
    penalty(f, 2, 30, 15, Instant.now().minus(Duration.ofDays(60)));
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql(
            """
            insert into booking_no_show_block
              (id, municipality_id, holder_email_key, starts_at, ends_at, created_at)
            values (:id, :m, :email, :from, :to, :from)
            """)
        .param("id", UUID.randomUUID())
        .param("m", f.municipality())
        .param("email", email)
        .param("from", now.minusHours(1))
        .param("to", now.plusDays(10))
        .update();
  }

  protected int waitsIn(Facility f) {
    return count("select count(*) from booking_wait where municipality_id = :k", f.municipality());
  }

  protected int tallyIn(Facility f) {
    return count(
        "select coalesce(sum(waits), 0) from booking_wait_tally where municipality_id = :k",
        f.municipality());
  }

  /** As franxas que se anuncian como liberadas mentres corre {@code action}. */
  protected List<BookingSlotReleased> released(Runnable action) {
    List<BookingSlotReleased> seen = new CopyOnWriteArrayList<>();
    ApplicationListener<ApplicationEvent> listener =
        e -> {
          if (e instanceof PayloadApplicationEvent<?> p
              && p.getPayload() instanceof BookingSlotReleased r) {
            seen.add(r);
          }
        };
    multicaster.addApplicationListener(listener);
    try {
      action.run();
    } finally {
      multicaster.removeApplicationListener(listener);
    }
    return seen;
  }

  protected static void await(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
    while (!condition.getAsBoolean()) {
      assertThat(Instant.now()).as("agardando").isBefore(deadline);
      Thread.sleep(50);
    }
  }

  /** Dálles tempo ás escoitas asíncronas que puidesen quedar. */
  protected static void settle() throws InterruptedException {
    Thread.sleep(1500);
  }

  protected static String problem(Api.Response response) {
    return response.status() + " " + response.json().get("code").asString();
  }

  protected static String email(String subject) {
    return subject + "@example.org";
  }

  protected JsonNode availability(Facility f, UUID use, LocalDate date) {
    Api.Response found =
        api()
            .get(
                "/api/v1/municipalities/"
                    + f.slug()
                    + "/public/facilities/"
                    + f.id()
                    + "/availability?useId="
                    + use
                    + "&date="
                    + date)
            .send();
    assertThat(found.status()).as(found.toString()).isEqualTo(200);
    return found.json();
  }
}
