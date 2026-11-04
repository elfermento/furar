// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ApplicationEventMulticaster;
import tools.jackson.databind.JsonNode;

/**
 * Preparación común das probas de reservas de punta a punta: un concello cunha instalación aberta
 * de 9:00 a 14:00, usos, reservar e pedir á vez, todo pola API. Pública para os correos ({@code
 * notification}).
 */
public abstract class AbstractBookingIT extends AbstractIT {

  protected static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  protected static final List<String> DAYS =
      List.of(
          "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY", "HOLIDAYS");

  /** Aberta de 9:00 a 14:00 todos os días: ocos de 60 minutos ás 9, 10, 11, 12 e 13. */
  protected static final int OPENS = 540;

  protected static final int CLOSES = 840;

  @Autowired protected ApplicationEventMulticaster multicaster;

  protected record Facility(String slug, UUID municipality, UUID id) {}

  protected static String facilities(String slug) {
    return "/api/v1/municipalities/" + slug + "/facilities";
  }

  protected static String mine(String slug) {
    return "/api/v1/municipalities/" + slug + "/me/bookings";
  }

  protected static LocalDate tomorrow() {
    return LocalDate.now(ZONE).plusDays(1);
  }

  protected static Instant at(LocalDate date, int minute) {
    return date.atStartOfDay(ZONE).plusMinutes(minute).toInstant();
  }

  protected static String person() {
    return person("p-" + UUID.randomUUID());
  }

  protected static String person(String subject) {
    return TestTokens.person(subject, true);
  }

  // --- Preparación, pola API de xestión ---

  protected long version(String slug, UUID facility) {
    return api()
        .get(facilities(slug) + "/" + facility)
        .token(TestTokens.manager(slug))
        .send()
        .etagVersion();
  }

  /** Concello novo cunha instalación publicada, aberta de 9:00 a 14:00 todos os días ata 2030. */
  protected Facility facility(String prefix, boolean online) {
    String slug = uniqueSlug(prefix);
    UUID municipality = id(createMunicipality(slug));
    return facility(slug, municipality, "Pavillón do Alto", online);
  }

  /** Outra instalación no mesmo concello, aberta de 9:00 a 14:00 todos os días ata 2030. */
  protected Facility facility(String slug, UUID municipality, String name, boolean online) {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("street", "Rúa do Alto");
    address.put("streetNumber", "12");
    address.put("postalCode", "15570");
    address.put("locality", "Narón");
    address.put("ineCode", "15054");
    address.put("latitude", 43.5167);
    address.put("longitude", -8.1833);
    Api.Response created =
        api()
            .post(facilities(slug), Map.of("name", name, "address", address))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID facility = id(created.json());
    Map<String, Object> details = new HashMap<>();
    details.put("sports", List.of(Map.of("code", "padel")));
    details.put("type", "FIELD");
    details.put("onlineBooking", online);
    details.put("showFreeSlots", !online);
    details.put("covered", false);
    Api.Response saved =
        api()
            .put(facilities(slug) + "/" + facility + "/details", details)
            .ifMatch(version(slug, facility))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    Map<String, Object> season = new LinkedHashMap<>();
    season.put("name", "Todo o ano");
    season.put("startDate", "2026-01-01");
    season.put("endDate", "2030-12-31");
    Api.Response seasonCreated =
        api()
            .post(facilities(slug) + "/" + facility + "/seasons", season)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(seasonCreated.status()).as(seasonCreated.toString()).isEqualTo(201);
    List<Map<String, Object>> week = new ArrayList<>();
    DAYS.forEach(
        day ->
            week.add(
                Map.of(
                    "day",
                    day,
                    "intervals",
                    List.of(Map.of("startMinute", OPENS, "endMinute", CLOSES)))));
    Map<String, Object> withWeek = new LinkedHashMap<>(season);
    withWeek.put("week", week);
    Api.Response hours =
        api()
            .put(
                facilities(slug)
                    + "/"
                    + facility
                    + "/seasons/"
                    + seasonCreated.json().get("id").asString(),
                withWeek)
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(hours.status()).as(hours.toString()).isEqualTo(200);
    return new Facility(slug, municipality, facility);
  }

  /** Un uso de 60 minutos, sempre, cos campos de {@code extra}. */
  protected JsonNode use(Facility f, String name, String mode, Map<String, Object> extra) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("rentalMode", mode);
    body.put("slotMinutes", 60);
    body.put("availability", "ALWAYS");
    body.putAll(extra);
    Api.Response created =
        api()
            .post(facilities(f.slug()) + "/" + f.id() + "/uses", body)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    publish(f);
    return created.json();
  }

  /** Co primeiro uso xa non lle falta nada: «Publicar» (as seguintes veces non cambia nada). */
  protected void publish(Facility f) {
    String url = facilities(f.slug()) + "/" + f.id();
    long version = api().get(url).token(TestTokens.manager(f.slug())).send().etagVersion();
    Api.Response published =
        api()
            .put(url + "/publication", Map.of())
            .ifMatch(version)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(published.status()).as(published.toString()).isEqualTo(200);
  }

  protected UUID whole(Facility f, Map<String, Object> extra) {
    return whole(f, "Pista enteira", extra);
  }

  protected UUID whole(Facility f, String name, Map<String, Object> extra) {
    return id(use(f, name, "WHOLE", extra));
  }

  // --- Reservar ---

  /** Reservar cunha {@code Idempotency-Key} nova: unha intención de reservar cada vez. */
  protected Api.Response reserve(
      String token, Facility f, UUID use, Instant start, int slots, UUID part) {
    return reserve(token, f, use, start, slots, part, UUID.randomUUID().toString());
  }

  /** Reservar con esta {@code Idempotency-Key} ou, con {@code null}, sen a cabeceira. */
  protected Api.Response reserve(
      String token, Facility f, UUID use, Instant start, int slots, UUID part, String key) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", slots);
    if (part != null) {
      body.put("partId", part);
    }
    Api.Call call = api().post(mine(f.slug()), body);
    if (key != null) {
      call.header("Idempotency-Key", key);
    }
    return token == null ? call.send() : call.token(token).send();
  }

  protected Api.Response reserve(String token, Facility f, UUID use, Instant start) {
    return reserve(token, f, use, start, 1, null);
  }

  /** {@code tokens.size()} peticións á vez, soltas ao mesmo tempo por unha barreira. */
  protected static List<Api.Response> together(
      List<java.util.concurrent.Callable<Api.Response>> calls) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(calls.size());
    try {
      CountDownLatch ready = new CountDownLatch(calls.size());
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Api.Response>> futures = new ArrayList<>();
      for (java.util.concurrent.Callable<Api.Response> call : calls) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return call.call();
                }));
      }
      assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      List<Api.Response> responses = new ArrayList<>();
      for (Future<Api.Response> future : futures) {
        responses.add(future.get(120, TimeUnit.SECONDS));
      }
      return responses;
    } finally {
      pool.shutdownNow();
    }
  }

  protected static Map<Integer, Long> byStatus(List<Api.Response> responses) {
    return responses.stream()
        .collect(Collectors.groupingBy(Api.Response::status, Collectors.counting()));
  }

  protected static String code(Api.Response response) {
    return response.json().get("code").asString();
  }

  protected int count(String sql, UUID key) {
    return jdbc.sql(sql).param("k", key).query(Integer.class).single();
  }

  protected static List<Integer> startMinutes(JsonNode slots) {
    List<Integer> minutes = new ArrayList<>();
    slots.forEach(s -> minutes.add(s.get("startMinute").asInt()));
    return minutes;
  }
}
