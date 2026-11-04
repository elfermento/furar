// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Tempadas de punta a punta con PostgreSQL real (D1-15a): {@code If-Match} por tempada, solape con
 * 409 que di con cal choca, semana substituída enteira nun gardado (tramos novos que pisan os
 * vellos), tramos non válidos, borrador {@code SCHEDULE}, outro concello, bloqueo da instalación,
 * modal de reservas afectadas cun dobre de {@link BookingImpact} e rexistro de actividade.
 */
class FacilityScheduleIT extends AbstractIT {

  @Autowired ProgrammableBookingImpact impact;
  @Autowired FacilityQueries queries;
  @Autowired TransactionTemplate tx;

  /**
   * Fío propio para cada tarefa: no pool común (un só fío nun runner de 2 CPU) quen ten o bloqueo
   * ocupaba o único fío e a escritura nunca saía.
   */
  private final ExecutorService async = Executors.newVirtualThreadPerTaskExecutor();

  record Facility(String slug, UUID municipality, UUID id, String url) {
    String seasons() {
      return url + "/seasons";
    }
  }

  Facility facility() {
    String slug = uniqueSlug("hor");
    UUID municipality = id(createMunicipality(slug));
    String base = "/api/v1/municipalities/" + slug + "/facilities";
    Api.Response created =
        api()
            .post(base, Map.of("name", "Pavillón de O Alto", "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID id = id(created.json());
    return new Facility(slug, municipality, id, base + "/" + id);
  }

  static Map<String, Object> season(String name, String from, String to) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("startDate", from);
    body.put("endDate", to);
    return body;
  }

  static Map<String, Object> day(String day, int... minutes) {
    List<Map<String, Object>> intervals = new ArrayList<>();
    for (int i = 0; i < minutes.length; i += 2) {
      intervals.add(Map.of("startMinute", minutes[i], "endMinute", minutes[i + 1]));
    }
    return Map.of("day", day, "intervals", intervals);
  }

  static Map<String, Object> withWeek(Map<String, Object> season, List<Map<String, Object>> week) {
    Map<String, Object> body = new LinkedHashMap<>(season);
    body.put("week", week);
    return body;
  }

  Api.Response post(Facility f, Map<String, Object> body) {
    return api().post(f.seasons(), body).token(TestTokens.manager(f.slug())).send();
  }

  Api.Response put(Facility f, UUID season, long version, Map<String, Object> body) {
    return api()
        .put(f.seasons() + "/" + season, body)
        .ifMatch(version)
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  JsonNode created(Facility f, Map<String, Object> body) {
    Api.Response response = post(f, body);
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return response.json();
  }

  JsonNode schedule(Facility f) {
    Api.Response response =
        api().get(f.url() + "/schedule").token(TestTokens.staff(f.slug())).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json();
  }

  static void assertProblem(Api.Response response, int status, String code) {
    assertThat(response.status()).as(response.toString()).isEqualTo(status);
    assertThat(response.json().get("code").asString()).isEqualTo(code);
  }

  static List<String> intervals(JsonNode season, String day) {
    List<String> result = new ArrayList<>();
    for (JsonNode row : season.get("week")) {
      if (row.get("day").asString().equals(day)) {
        row.get("intervals")
            .forEach(
                i -> result.add(i.get("startMinute").asInt() + "-" + i.get("endMinute").asInt()));
      }
    }
    return result;
  }

  String missing(Facility f) {
    return api()
        .get(f.url())
        .token(TestTokens.staff(f.slug()))
        .send()
        .json()
        .get("missing")
        .toString();
  }

  static LocalDate today() {
    return LocalDate.now(ZoneId.of("Europe/Madrid"));
  }

  @Test
  void createReadAndUpdateWithIfMatchOfTheSeason() {
    Facility f = facility();
    Api.Response created = post(f, season("Inverno", "2026-09-01", "2027-06-30"));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID seasonId = id(created.json());
    assertThat(created.header("Location"))
        .endsWith("/facilities/" + f.id() + "/seasons/" + seasonId);
    assertThat(created.etagVersion()).isZero();
    assertThat(created.json().get("week").size()).isEqualTo(8);
    assertThat(intervals(created.json(), "MONDAY")).isEmpty();

    Api.Response one =
        api().get(f.seasons() + "/" + seasonId).token(TestTokens.staff(f.slug())).send();
    assertThat(one.status()).isEqualTo(200);
    assertThat(one.etagVersion()).isZero();

    Map<String, Object> body =
        withWeek(
            season("Inverno 26-27", "2026-09-01", "2027-06-30"),
            List.of(day("MONDAY", 960, 1380, 480, 840), day("HOLIDAYS", 600, 840)));
    Api.Response updated = put(f, seasonId, 0, body);
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    assertThat(updated.etagVersion()).isEqualTo(1);
    assertThat(updated.json().get("name").asString()).isEqualTo("Inverno 26-27");
    assertThat(intervals(updated.json(), "MONDAY")).containsExactly("480-840", "960-1380");
    assertThat(intervals(updated.json(), "TUESDAY")).isEmpty();
    assertThat(intervals(updated.json(), "HOLIDAYS")).containsExactly("600-840");

    assertProblem(put(f, seasonId, 0, body), 412, "common.stale-version");
    Api.Response without =
        api().put(f.seasons() + "/" + seasonId, body).token(TestTokens.manager(f.slug())).send();
    assertProblem(without, 428, "common.precondition-required");
    assertThat(
            api()
                .post(f.seasons(), season("X", "2028-01-01", "2028-01-31"))
                .token(TestTokens.staff(f.slug()))
                .send()
                .status())
        .isEqualTo(403);

    JsonNode schedule = schedule(f);
    assertThat(schedule.get("seasons").size()).isEqualTo(1);
    assertThat(schedule.get("seasons").get(0).get("version").asLong()).isEqualTo(1);
    assertThat(schedule.get("year").asInt()).isEqualTo(today().getYear());
  }

  @Test
  void overlappingSeasonIsRefusedWithTheSeasonItClashesWith() {
    Facility f = facility();
    JsonNode winter = created(f, season("Inverno", "2026-09-01", "2027-06-30"));
    Api.Response clash = post(f, season("Verán", "2027-06-30", "2027-08-31"));
    assertProblem(clash, 409, "facility.season-overlap");
    JsonNode params = clash.json().get("params");
    assertThat(params.get("seasonId").asString()).isEqualTo(id(winter).toString());
    assertThat(params.get("name").asString()).isEqualTo("Inverno");
    assertThat(params.get("startDate").asString()).isEqualTo("2026-09-01");
    assertThat(params.get("endDate").asString()).isEqualTo("2027-06-30");
    assertThat(clash.json().get("detail").asString()).contains("Inverno");

    JsonNode summer = created(f, season("Verán", "2027-07-01", "2027-08-31"));
    Api.Response moved =
        put(f, id(summer), 0, withWeek(season("Verán", "2027-06-15", "2027-08-31"), List.of()));
    assertProblem(moved, 409, "facility.season-overlap");

    // Noutra instalación as mesmas datas non chocan.
    Facility other = facility();
    created(other, season("Inverno", "2026-09-01", "2027-06-30"));
  }

  /** Maqueta 54: «Ata» é opcional; sen fin, a tempada cobre todo desde o inicio. */
  @Test
  void aSeasonWithoutEndCoversEveryDayFromItsStart() {
    Facility f = facility();
    JsonNode always = created(f, season("Todo o ano", "2026-01-01", null));
    assertThat(always.path("endDate").isNull() || always.path("endDate").isMissingNode())
        .as(always.toString())
        .isTrue();
    Api.Response week =
        put(
            f,
            id(always),
            0,
            withWeek(season("Todo o ano", "2026-01-01", null), List.of(day("MONDAY", 480, 1320))));
    assertThat(week.status()).as(week.toString()).isEqualTo(200);

    JsonNode schedule = schedule(f);
    assertThat(schedule.get("gaps").size()).as("sen días sen tempada").isZero();
    assertThat(schedule.get("currentSeasonId").asString()).isEqualTo(id(always).toString());
    Api.Response facility = api().get(f.url()).token(TestTokens.manager(f.slug())).send();
    assertThat(facility.json().get("missing").toString()).doesNotContain("SCHEDULE");

    Api.Response clash = post(f, season("Verán", "2035-07-01", "2035-08-31"));
    assertProblem(clash, 409, "facility.season-overlap");
    assertThat(clash.json().get("params").has("endDate")).isFalse();
    created(f, season("Antes", "2025-01-01", "2025-12-31"));
  }

  /** §1.2.15: a semana nova pisa a vella; sen DELETE + flush antes, a exclusión saltaría. */
  @Test
  void theWholeWeekIsReplacedInOneSave() {
    Facility f = facility();
    UUID seasonId = id(created(f, season("Inverno", "2026-09-01", "2027-06-30")));
    Api.Response first =
        put(
            f,
            seasonId,
            0,
            withWeek(
                season("Inverno", "2026-09-01", "2027-06-30"),
                List.of(
                    day("MONDAY", 480, 840, 960, 1380),
                    day("TUESDAY", 480, 1380),
                    day("SUNDAY", 600, 840))));
    assertThat(first.status()).as(first.toString()).isEqualTo(200);

    Api.Response second =
        put(
            f,
            seasonId,
            1,
            withWeek(
                season("Inverno", "2026-09-01", "2027-06-30"),
                List.of(day("MONDAY", 540, 1440), day("WEDNESDAY", 0, 60))));
    assertThat(second.status()).as(second.toString()).isEqualTo(200);
    assertThat(intervals(second.json(), "MONDAY")).containsExactly("540-1440");
    assertThat(intervals(second.json(), "TUESDAY")).isEmpty();
    assertThat(intervals(second.json(), "WEDNESDAY")).containsExactly("0-60");
    assertThat(intervals(second.json(), "SUNDAY")).isEmpty();
    assertThat(
            jdbc.sql("select count(*) from season_hours where season_id = :s")
                .param("s", seasonId)
                .query(Long.class)
                .single())
        .isEqualTo(2);
  }

  @Test
  void intervalsAreCheckedWithTheirField() {
    Facility f = facility();
    UUID seasonId = id(created(f, season("Inverno", "2026-09-01", "2027-06-30")));
    Api.Response invalid =
        put(
            f,
            seasonId,
            0,
            withWeek(
                season("Inverno", "2026-09-01", "2027-06-30"),
                List.of(day("MONDAY", 480, 840), day("FRIDAY", 1380, 1455))));
    assertProblem(invalid, 422, "facility.interval-invalid");
    assertThat(invalid.json().get("errors").get(0).get("field").asString())
        .isEqualTo("week[1].intervals[0]");
    Api.Response quarters =
        put(
            f,
            seasonId,
            0,
            withWeek(
                season("Inverno", "2026-09-01", "2027-06-30"), List.of(day("MONDAY", 485, 600))));
    assertProblem(quarters, 422, "facility.interval-invalid");
    Api.Response overlap =
        put(
            f,
            seasonId,
            0,
            withWeek(
                season("Inverno", "2026-09-01", "2027-06-30"),
                List.of(day("MONDAY", 480, 840, 600, 900))));
    assertProblem(overlap, 422, "facility.interval-overlap");
    assertThat(overlap.json().get("errors").get(0).get("field").asString())
        .isEqualTo("week[0].intervals[1]");
    Api.Response dates = post(f, season("Mal", "2027-09-30", "2027-09-01"));
    assertProblem(dates, 422, "facility.season-invalid");
    assertThat(dates.json().get("errors").get(0).get("field").asString()).isEqualTo("endDate");
  }

  @Test
  void copyFromStartsWithTheOtherWeekAndHolidaysLikeSunday() {
    Facility f = facility();
    UUID winter = id(created(f, season("Inverno", "2026-09-01", "2027-06-30")));
    put(
        f,
        winter,
        0,
        withWeek(
            season("Inverno", "2026-09-01", "2027-06-30"),
            List.of(day("MONDAY", 480, 1380), day("SUNDAY", 600, 840), day("HOLIDAYS", 0, 60))));
    Map<String, Object> body = season("Verán", "2027-07-01", "2027-08-31");
    body.put("copyFrom", winter.toString());
    JsonNode summer = created(f, body);
    assertThat(intervals(summer, "MONDAY")).containsExactly("480-1380");
    assertThat(intervals(summer, "HOLIDAYS")).containsExactly("600-840");

    Facility other = facility();
    Map<String, Object> foreign = season("Verán", "2027-07-01", "2027-08-31");
    foreign.put("copyFrom", winter.toString());
    assertProblem(post(other, foreign), 422, "facility.season-invalid");
  }

  /** P8: unha tempada con algún tramo que remata hoxe ou despois quita SCHEDULE do borrador. */
  @Test
  void aSeasonWithHoursEndingTodayOrLaterIsASchedule() {
    Facility f = facility();
    assertThat(missing(f)).contains("SCHEDULE");
    LocalDate today = today();
    UUID past =
        id(
            created(
                f,
                season("Pasada", today.minusDays(40).toString(), today.minusDays(1).toString())));
    put(
        f,
        past,
        0,
        withWeek(
            season("Pasada", today.minusDays(40).toString(), today.minusDays(1).toString()),
            List.of(day("MONDAY", 480, 840))));
    assertThat(missing(f)).contains("SCHEDULE");

    UUID current = id(created(f, season("Agora", today.toString(), today.toString())));
    assertThat(missing(f)).contains("SCHEDULE");
    put(
        f,
        current,
        0,
        withWeek(
            season("Agora", today.toString(), today.toString()), List.of(day("FRIDAY", 0, 15))));
    assertThat(missing(f)).doesNotContain("SCHEDULE");
    assertThat(schedule(f).get("currentSeasonId").asString()).isEqualTo(current.toString());

    Api.Response list =
        api()
            .get("/api/v1/municipalities/" + f.slug() + "/facilities")
            .token(TestTokens.staff(f.slug()))
            .send();
    assertThat(list.body()).doesNotContain("SCHEDULE");

    Api.Response deleted =
        api()
            .method("DELETE", f.seasons() + "/" + current, null)
            .ifMatch(1)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(deleted.status()).as(deleted.toString()).isEqualTo(204);
    assertThat(missing(f)).contains("SCHEDULE");
  }

  @Test
  void deleteNeedsTheVersionOfTheSeason() {
    Facility f = facility();
    UUID seasonId = id(created(f, season("Inverno", "2026-09-01", "2027-06-30")));
    Api.Response stale =
        api()
            .method("DELETE", f.seasons() + "/" + seasonId, null)
            .ifMatch(3)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertProblem(stale, 412, "common.stale-version");
    Api.Response ok =
        api()
            .method("DELETE", f.seasons() + "/" + seasonId, null)
            .ifMatch(0)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(ok.status()).as(ok.toString()).isEqualTo(204);
    assertProblem(
        api().get(f.seasons() + "/" + seasonId).token(TestTokens.staff(f.slug())).send(),
        404,
        "facility.season-not-found");
  }

  @Test
  void anotherMunicipalityCannotSeeOrTouchTheSeasons() {
    Facility a = facility();
    Facility b = facility();
    UUID seasonId = id(created(a, season("Inverno", "2026-09-01", "2027-06-30")));
    String foreign = "/api/v1/municipalities/" + b.slug() + "/facilities/" + a.id();
    assertProblem(
        api().get(foreign + "/schedule").token(TestTokens.staff(b.slug())).send(),
        404,
        "facility.not-found");
    assertProblem(
        api().get(foreign + "/seasons/" + seasonId).token(TestTokens.staff(b.slug())).send(),
        404,
        "facility.season-not-found");
    assertProblem(
        api()
            .post(foreign + "/seasons", season("Verán", "2027-07-01", "2027-08-31"))
            .token(TestTokens.manager(b.slug()))
            .send(),
        404,
        "facility.not-found");
    assertProblem(
        api()
            .put(
                foreign + "/seasons/" + seasonId,
                withWeek(season("Roubada", "2026-09-01", "2027-06-30"), List.of()))
            .ifMatch(0)
            .token(TestTokens.manager(b.slug()))
            .send(),
        404,
        "facility.not-found");
    assertThat(schedule(a).get("seasons").get(0).get("name").asString()).isEqualTo("Inverno");
  }

  @Test
  void changingTheWeekWithBookingsOpensTheDialog() {
    Facility f = facility();
    UUID seasonId = id(created(f, season("Inverno", "2026-09-01", "2027-06-30")));
    Map<String, Object> open =
        withWeek(season("Inverno", "2026-09-01", "2027-06-30"), List.of(day("MONDAY", 480, 1380)));
    assertThat(put(f, seasonId, 0, open).status()).isEqualTo(200);

    UUID booking = UUID.randomUUID();
    impact.affect(
        f.id(),
        List.of(
            new AffectedBooking(
                booking, "Uxía Pérez", true, LocalDate.of(2026, 11, 2), 480, 570, "Fútbol sala")));
    Map<String, Object> shorter =
        withWeek(season("Inverno", "2026-09-01", "2027-06-30"), List.of(day("MONDAY", 600, 1380)));
    Api.Response first = put(f, seasonId, 1, shorter);
    assertProblem(first, 409, "facility.affected-bookings");
    assertThat(first.json().get("affected").get(0).get("bookingId").asString())
        .isEqualTo(booking.toString());

    shorter.put("decisions", List.of(Map.of("bookingId", booking.toString(), "action", "KEEP")));
    Api.Response second = put(f, seasonId, 1, shorter);
    assertThat(second.status()).as(second.toString()).isEqualTo(200);
    assertThat(impact.appliedTo(f.id())).hasSize(1);

    Api.Response delete =
        api()
            .method("DELETE", f.seasons() + "/" + seasonId, Map.of())
            .ifMatch(2)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertProblem(delete, 409, "facility.affected-bookings");
    Api.Response confirmed =
        api()
            .method(
                "DELETE",
                f.seasons() + "/" + seasonId,
                Map.of(
                    "decisions",
                    List.of(
                        Map.of(
                            "bookingId",
                            booking.toString(),
                            "action",
                            "CANCEL",
                            "channels",
                            List.of("EMAIL")))))
            .ifMatch(2)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(confirmed.status()).as(confirmed.toString()).isEqualTo(204);
    assertThat(impact.appliedTo(f.id())).hasSize(2);
  }

  /** ADR-0022, punto 4: a escritura do horario espera a quen ten o bloqueo da instalación. */
  @Test
  void seasonWritesWaitForTheFacilityLock() throws Exception {
    Facility f = facility();
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Boolean> holder =
        CompletableFuture.supplyAsync(
            () ->
                TenantContext.runAs(
                    new TenantRef(f.municipality(), f.slug()),
                    () ->
                        tx.execute(
                            status -> {
                              boolean ok = queries.lockFacility(f.id());
                              locked.countDown();
                              await(release);
                              return ok;
                            })),
            async);
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Api.Response> write =
        CompletableFuture.supplyAsync(
            () -> post(f, season("Inverno", "2026-09-01", "2027-06-30")), async);
    assertThat(waitingOnALock(Duration.ofSeconds(10))).isTrue();
    assertThat(write).isNotDone();
    release.countDown();
    assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
    Api.Response response = write.get(10, TimeUnit.SECONDS);
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
  }

  @Test
  void seasonWritesAreAudited() {
    Facility f = facility();
    created(f, season("Inverno", "2026-09-01", "2027-06-30"));
    post(f, season("Choca", "2027-01-01", "2027-01-31"));
    List<String> rows =
        jdbc.sql(
                """
                select action || ' ' || outcome from audit_event
                where municipality_id = (select id from municipality where slug = :s)
                order by occurred_at
                """)
            .param("s", f.slug())
            .query(String.class)
            .list();
    assertThat(rows).contains("facility.season-create SUCCESS", "facility.season-create FAILURE");
  }

  private boolean waitingOnALock(Duration timeout) throws InterruptedException {
    long end = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < end) {
      Integer waiting =
          appJdbc
              .sql(
                  """
                  select count(*) from pg_stat_activity
                  where wait_event_type = 'Lock' and query like '%for no key update%'
                  """)
              .query(Integer.class)
              .single();
      if (waiting > 0) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("not released");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
