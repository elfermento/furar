// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

/**
 * Excepcións do horario de punta a punta con PostgreSQL real (D1-15b): {@code If-Match} por
 * excepción, data repetida → 409 (no servizo e na base), {@code today}, {@code now} e {@code
 * gaps[]} en {@code GET …/schedule}, {@code openingIntervals} nos cambios de hora (29 de marzo e 25
 * de outubro de 2026) coa prioridade excepción &gt; festivo &gt; semana, {@code exclusiveUseOn},
 * modal de reservas afectadas cun dobre, outro concello e rexistro de actividade.
 */
class FacilityScheduleExceptionIT extends AbstractIT {

  static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

  @Autowired ProgrammableBookingImpact impact;
  @Autowired FacilityQueries queries;

  record Facility(String slug, UUID municipality, UUID id, String url) {
    String exceptions() {
      return url + "/schedule-exceptions";
    }

    TenantRef tenant() {
      return new TenantRef(municipality, slug);
    }
  }

  Facility facility() {
    String slug = uniqueSlug("exc");
    UUID municipality = id(createMunicipality(slug));
    String base = "/api/v1/municipalities/" + slug + "/facilities";
    Api.Response created =
        api()
            .post(base, Map.of("name", "Pavillón de Piñeiros", "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID id = id(created.json());
    return new Facility(slug, municipality, id, base + "/" + id);
  }

  static Map<String, Object> closed(String date) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", date);
    body.put("open", false);
    body.put("reason", "Fin de ano");
    return body;
  }

  static Map<String, Object> open(String date, int start, int end) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", date);
    body.put("open", true);
    body.put("startMinute", start);
    body.put("endMinute", end);
    body.put("reason", "Noiteboa");
    return body;
  }

  Api.Response post(Facility f, Map<String, Object> body) {
    return api().post(f.exceptions(), body).token(TestTokens.manager(f.slug())).send();
  }

  Api.Response put(Facility f, UUID exception, long version, Map<String, Object> body) {
    return api()
        .put(f.exceptions() + "/" + exception, body)
        .ifMatch(version)
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  Api.Response delete(Facility f, UUID exception, long version, Object body) {
    return api()
        .method("DELETE", f.exceptions() + "/" + exception, body)
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

  /** Tempada coa mesma franxa todos os días (tamén festivos). */
  UUID season(Facility f, String from, String to, int start, int end) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Tempada " + from);
    body.put("startDate", from);
    body.put("endDate", to);
    Api.Response created =
        api().post(f.url() + "/seasons", body).token(TestTokens.manager(f.slug())).send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID id = id(created.json());
    List<Map<String, Object>> week = new ArrayList<>();
    for (String day :
        List.of(
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY",
            "HOLIDAYS")) {
      week.add(
          Map.of(
              "day",
              day,
              "intervals",
              List.of(
                  Map.of(
                      "startMinute",
                      day.equals("HOLIDAYS") ? 600 : start,
                      "endMinute",
                      day.equals("HOLIDAYS") ? 780 : end))));
    }
    body.put("week", week);
    Api.Response updated =
        api()
            .put(f.url() + "/seasons/" + id, body)
            .ifMatch(0)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    return id;
  }

  static void assertProblem(Api.Response response, int status, String code) {
    assertThat(response.status()).as(response.toString()).isEqualTo(status);
    assertThat(response.json().get("code").asString()).isEqualTo(code);
  }

  static LocalDate today() {
    return LocalDate.now(MADRID);
  }

  List<OpeningInterval> openingIntervals(Facility f, String from, String to) {
    return TenantContext.runAs(
        f.tenant(),
        () -> queries.openingIntervals(f.id(), LocalDate.parse(from), LocalDate.parse(to)));
  }

  @Test
  void createReadUpdateAndDeleteWithIfMatchOfTheException() {
    Facility f = facility();
    Api.Response created = post(f, open("2026-12-24", 540, 840));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID exceptionId = id(created.json());
    assertThat(created.header("Location"))
        .endsWith("/facilities/" + f.id() + "/schedule-exceptions/" + exceptionId);
    assertThat(created.etagVersion()).isZero();
    JsonNode body = created.json();
    assertThat(body.get("date").asString()).isEqualTo("2026-12-24");
    assertThat(body.get("open").asBoolean()).isTrue();
    assertThat(body.get("startMinute").asInt()).isEqualTo(540);
    assertThat(body.get("endMinute").asInt()).isEqualTo(840);
    assertThat(body.get("reason").asString()).isEqualTo("Noiteboa");

    Api.Response one =
        api().get(f.exceptions() + "/" + exceptionId).token(TestTokens.staff(f.slug())).send();
    assertThat(one.status()).isEqualTo(200);
    assertThat(one.etagVersion()).isZero();

    Api.Response updated = put(f, exceptionId, 0, closed("2026-12-24"));
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    assertThat(updated.etagVersion()).isEqualTo(1);
    assertThat(updated.json().get("open").asBoolean()).isFalse();
    assertThat(updated.json().get("startMinute").isNull()).isTrue();

    assertProblem(put(f, exceptionId, 0, closed("2026-12-24")), 412, "common.stale-version");
    Api.Response without =
        api()
            .put(f.exceptions() + "/" + exceptionId, closed("2026-12-24"))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertProblem(without, 428, "common.precondition-required");
    assertThat(
            api()
                .post(f.exceptions(), closed("2026-12-31"))
                .token(TestTokens.staff(f.slug()))
                .send()
                .status())
        .isEqualTo(403);

    JsonNode schedule = schedule(f);
    assertThat(schedule.get("exceptions").size()).isEqualTo(1);
    assertThat(schedule.get("exceptions").get(0).get("version").asLong()).isEqualTo(1);

    assertProblem(delete(f, exceptionId, 0, null), 412, "common.stale-version");
    assertThat(delete(f, exceptionId, 1, null).status()).isEqualTo(204);
    assertProblem(
        api().get(f.exceptions() + "/" + exceptionId).token(TestTokens.staff(f.slug())).send(),
        404,
        "facility.exception-not-found");
    assertThat(schedule(f).get("exceptions").size()).isZero();
  }

  @Test
  void aRepeatedDateIsRefused() {
    Facility f = facility();
    UUID first = id(created(f, closed("2026-12-31")));
    Api.Response clash = post(f, open("2026-12-31", 600, 720));
    assertProblem(clash, 409, "facility.exception-date-taken");
    assertThat(clash.json().get("params").get("exceptionId").asString())
        .isEqualTo(first.toString());
    UUID other = id(created(f, closed("2026-12-30")));
    assertProblem(put(f, other, 0, closed("2026-12-31")), 409, "facility.exception-date-taken");
    // Noutra instalación a mesma data non choca.
    created(facility(), closed("2026-12-31"));

    // A base tamén o garante, sen pasar polo servizo.
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        insert into schedule_exception (id, municipality_id, facility_id, date,
                            open, created_at, updated_at, version)
                        values (:id, :m, :f, date '2026-12-31', false, now(), now(), 0)
                        """)
                    .param("id", UUID.randomUUID())
                    .param("m", f.municipality())
                    .param("f", f.id())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("schedule_exception_date_uk");
  }

  @Test
  void invalidFieldsAreReportedWithTheirField() {
    Facility f = facility();
    Api.Response interval = post(f, open("2026-12-24", 840, 540));
    assertProblem(interval, 422, "facility.interval-invalid");
    assertThat(interval.json().get("errors").get(0).get("field").asString()).isEqualTo("endMinute");
    assertProblem(post(f, open("2026-12-24", 545, 840)), 422, "facility.interval-invalid");
    Map<String, Object> closedWithHours = open("2026-12-24", 540, 840);
    closedWithHours.put("open", false);
    Api.Response invalid = post(f, closedWithHours);
    assertProblem(invalid, 422, "facility.exception-invalid");
    assertThat(invalid.json().get("errors").get(0).get("field").asString())
        .isEqualTo("startMinute");
    Map<String, Object> openWithout = closed("2026-12-24");
    openWithout.put("open", true);
    assertProblem(post(f, openWithout), 422, "facility.exception-invalid");
  }

  @Test
  void scheduleGivesTodayNowAndTheDaysWithoutSeason() {
    Facility f = facility();
    LocalDate today = today();
    JsonNode empty = schedule(f);
    assertThat(empty.get("today").asString()).isEqualTo(today.toString());
    int now = empty.get("now").asInt();
    assertThat(now).isBetween(0, 1439);
    int clock = LocalTime.now(MADRID).toSecondOfDay() / 60;
    int drift = Math.abs(clock - now);
    assertThat(Math.min(drift, 1440 - drift)).isLessThanOrEqualTo(1);
    assertThat(empty.get("gaps").size()).isEqualTo(1);
    assertThat(empty.get("gaps").get(0).get("from").asString()).isEqualTo(today.toString());
    assertThat(empty.get("gaps").get(0).get("to").asString())
        .isEqualTo(today.plusDays(364).toString());

    // Unha tempada que remata antes de 365 días deixa o resto sen tempada.
    season(f, today.minusDays(10).toString(), today.plusDays(99).toString(), 480, 1320);
    JsonNode gaps = schedule(f).get("gaps");
    assertThat(gaps.size()).isEqualTo(1);
    assertThat(gaps.get(0).get("from").asString()).isEqualTo(today.plusDays(100).toString());
    assertThat(gaps.get(0).get("to").asString()).isEqualTo(today.plusDays(364).toString());

    season(f, today.plusDays(150).toString(), today.plusDays(500).toString(), 480, 1320);
    gaps = schedule(f).get("gaps");
    assertThat(gaps.size()).isEqualTo(1);
    assertThat(gaps.get(0).get("from").asString()).isEqualTo(today.plusDays(100).toString());
    assertThat(gaps.get(0).get("to").asString()).isEqualTo(today.plusDays(149).toString());
  }

  /** 29 de marzo de 2026: o día dura 23 horas; o 19 de marzo é festivo en Galicia. */
  @Test
  void openingIntervalsAcrossTheSpringChange() {
    Facility f = facility();
    season(f, "2026-03-01", "2026-04-30", 0, 1440);
    created(f, open("2026-03-30", 540, 840));
    List<OpeningInterval> intervals = openingIntervals(f, "2026-03-28", "2026-03-30");
    assertThat(intervals).hasSize(3);
    OpeningInterval sunday = intervals.get(1);
    assertThat(sunday.date()).isEqualTo(LocalDate.parse("2026-03-29"));
    assertThat(sunday.start()).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
    assertThat(sunday.end()).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
    assertThat(Duration.between(sunday.start(), sunday.end())).isEqualTo(Duration.ofHours(23));
    assertThat(intervals.get(0).end()).isEqualTo(sunday.start());
    // Excepción > semana.
    OpeningInterval monday = intervals.get(2);
    assertThat(monday.startMinute()).isEqualTo(540);
    assertThat(monday.endMinute()).isEqualTo(840);
    assertThat(monday.start()).isEqualTo(Instant.parse("2026-03-30T07:00:00Z"));

    // Festivo > semana: o 19 de marzo usa a fila de festivos (10:00–13:00).
    assertThat(openingIntervals(f, "2026-03-19", "2026-03-19"))
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.startMinute()).isEqualTo(600);
              assertThat(i.endMinute()).isEqualTo(780);
              assertThat(i.start()).isEqualTo(Instant.parse("2026-03-19T09:00:00Z"));
            });
    // Excepción > festivo.
    created(f, closed("2026-03-19"));
    assertThat(openingIntervals(f, "2026-03-19", "2026-03-19")).isEmpty();
  }

  /** 25 de outubro de 2026: o día dura 25 horas. */
  @Test
  void openingIntervalsAcrossTheAutumnChange() {
    Facility f = facility();
    season(f, "2026-10-01", "2026-10-31", 0, 1440);
    List<OpeningInterval> intervals = openingIntervals(f, "2026-10-24", "2026-10-26");
    assertThat(intervals).hasSize(3);
    OpeningInterval sunday = intervals.get(1);
    assertThat(sunday.start()).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
    assertThat(sunday.end()).isEqualTo(Instant.parse("2026-10-25T23:00:00Z"));
    assertThat(Duration.between(sunday.start(), sunday.end())).isEqualTo(Duration.ofHours(25));
    assertThat(sunday.end()).isEqualTo(intervals.get(2).start());

    // Sen tempada, pechado; e non se ven as instalacións doutro concello.
    assertThat(openingIntervals(f, "2026-11-01", "2026-11-03")).isEmpty();
    Facility other = facility();
    assertThat(
            TenantContext.runAs(
                other.tenant(),
                () ->
                    queries.openingIntervals(
                        f.id(), LocalDate.parse("2026-10-24"), LocalDate.parse("2026-10-26"))))
        .isEmpty();
  }

  /**
   * Os topes das horas abertas: {@value FacilityQueries#MAX_OPENING_DAYS} días nas lecturas de
   * sempre e {@value FacilityQueries#MAX_LONG_OPENING_DAYS} na longa dos indicadores, que dá o
   * mesmo ca as curtas encadeadas.
   */
  @Test
  void openingReadsHaveTheirCapsAndTheLongOneMatchesTheShortOnes() {
    Facility f = facility();
    season(f, "2026-01-01", "2027-12-31", 600, 720);
    LocalDate from = LocalDate.parse("2026-01-01");
    LocalDate lastShort = from.plusDays(FacilityQueries.MAX_OPENING_DAYS - 1L);
    LocalDate to = LocalDate.parse("2027-12-31");
    Map<UUID, List<OpeningInterval>> longRead =
        TenantContext.runAs(
            f.tenant(), () -> queries.longOpeningIntervalsByFacility(f.id(), from, to));
    List<OpeningInterval> chained =
        new ArrayList<>(openingIntervals(f, "2026-01-01", "" + lastShort));
    chained.addAll(openingIntervals(f, "" + lastShort.plusDays(1), "2027-12-31"));
    assertThat(longRead.get(f.id())).isEqualTo(chained);

    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    f.tenant(),
                    () -> queries.openingIntervals(f.id(), from, lastShort.plusDays(1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    f.tenant(),
                    () -> queries.openingIntervalsByFacility(null, from, lastShort.plusDays(1))))
        .isInstanceOf(IllegalArgumentException.class);
    LocalDate lastLong = from.plusDays(FacilityQueries.MAX_LONG_OPENING_DAYS - 1L);
    assertThat(
            TenantContext.runAs(
                f.tenant(), () -> queries.longOpeningIntervalsByFacility(null, from, lastLong)))
        .containsKey(f.id());
    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    f.tenant(),
                    () -> queries.longOpeningIntervalsByFacility(null, from, lastLong.plusDays(1))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exclusiveUseOnGivesTheUseThatBlocksThatDay() {
    Facility f = facility();
    Map<String, Object> blocking = new LinkedHashMap<>();
    blocking.put("name", "Torneo de Samaín");
    blocking.put("rentalMode", "WHOLE");
    blocking.put("slotMinutes", 60);
    blocking.put("availability", "DATES");
    blocking.put("blocksOthers", true);
    blocking.put("periods", List.of(Map.of("from", "2026-10-31", "to", "2026-11-01")));
    Api.Response use =
        api().post(f.url() + "/uses", blocking).token(TestTokens.manager(f.slug())).send();
    assertThat(use.status()).as(use.toString()).isEqualTo(201);
    UUID useId = id(use.json());

    assertThat(exclusiveUseOn(f, "2026-10-31")).contains(useId);
    assertThat(exclusiveUseOn(f, "2026-11-01")).contains(useId);
    assertThat(exclusiveUseOn(f, "2026-11-02")).isEmpty();
    assertThat(exclusiveUseOn(f, "2026-10-30")).isEmpty();
  }

  @Test
  void aWeekendOnlyBlockingUseDoesNotBlockATuesday() {
    Facility f = facility();
    Map<String, Object> blocking = new LinkedHashMap<>();
    blocking.put("name", "Liga de fin de semana");
    blocking.put("rentalMode", "WHOLE");
    blocking.put("slotMinutes", 60);
    blocking.put("availability", "DATES");
    blocking.put("blocksOthers", true);
    blocking.put("periods", List.of(Map.of("from", "2026-11-01", "to", "2026-11-30")));
    blocking.put("weekdays", 32 | 64);
    Api.Response use =
        api().post(f.url() + "/uses", blocking).token(TestTokens.manager(f.slug())).send();
    assertThat(use.status()).as(use.toString()).isEqualTo(201);
    UUID useId = id(use.json());

    assertThat(exclusiveUseOn(f, "2026-11-07")).as("sábado").contains(useId);
    assertThat(exclusiveUseOn(f, "2026-11-08")).as("domingo").contains(useId);
    assertThat(exclusiveUseOn(f, "2026-11-10")).as("martes").isEmpty();
    UseSummary summary = TenantContext.runAs(f.tenant(), () -> queries.use(useId)).orElseThrow();
    assertThat(summary.availableOn(LocalDate.parse("2026-11-10"))).isFalse();
    assertThat(summary.availableOn(LocalDate.parse("2026-11-07"))).isTrue();
  }

  Optional<UUID> exclusiveUseOn(Facility f, String date) {
    return TenantContext.runAs(
        f.tenant(), () -> queries.exclusiveUseOn(f.id(), LocalDate.parse(date)));
  }

  @Test
  void closingADayWithBookingsOpensTheDialog() {
    Facility f = facility();
    season(f, "2026-12-01", "2026-12-31", 480, 1320);
    UUID booking = UUID.randomUUID();
    impact.affect(
        f.id(),
        List.of(
            new AffectedBooking(
                booking, "Uxía Pérez", true, LocalDate.of(2026, 12, 31), 480, 570, "Fútbol sala")));
    Api.Response first = post(f, closed("2026-12-31"));
    assertProblem(first, 409, "facility.affected-bookings");
    assertThat(first.json().get("affected").get(0).get("bookingId").asString())
        .isEqualTo(booking.toString());

    Map<String, Object> confirmed = closed("2026-12-31");
    confirmed.put(
        "decisions",
        List.of(
            Map.of(
                "bookingId",
                booking.toString(),
                "action",
                "CANCEL",
                "channels",
                List.of("EMAIL"))));
    Api.Response second = post(f, confirmed);
    assertThat(second.status()).as(second.toString()).isEqualTo(201);
    assertThat(impact.appliedTo(f.id()))
        .singleElement()
        .satisfies(
            a ->
                assertThat(((FacilityChange.ScheduleChanged) a.change()).lost())
                    .isNotEmpty()
                    .allMatch(i -> i.date().equals(LocalDate.of(2026, 12, 31))));

    // Borrar o peche só devolve horas: sen modal.
    assertThat(delete(f, id(second.json()), 0, Map.of()).status()).isEqualTo(204);
    assertThat(impact.appliedTo(f.id())).hasSize(1);
  }

  @Test
  void anotherMunicipalityCannotSeeOrTouchTheExceptions() {
    Facility a = facility();
    Facility b = facility();
    UUID exceptionId = id(created(a, closed("2026-12-31")));
    String foreign =
        "/api/v1/municipalities/" + b.slug() + "/facilities/" + a.id() + "/schedule-exceptions";
    assertProblem(
        api().get(foreign + "/" + exceptionId).token(TestTokens.staff(b.slug())).send(),
        404,
        "facility.exception-not-found");
    assertProblem(
        api().post(foreign, closed("2026-12-30")).token(TestTokens.manager(b.slug())).send(),
        404,
        "facility.not-found");
    assertProblem(
        api()
            .put(foreign + "/" + exceptionId, closed("2026-12-29"))
            .ifMatch(0)
            .token(TestTokens.manager(b.slug()))
            .send(),
        404,
        "facility.not-found");
    assertProblem(
        api()
            .method("DELETE", foreign + "/" + exceptionId, null)
            .ifMatch(0)
            .token(TestTokens.manager(b.slug()))
            .send(),
        404,
        "facility.not-found");
    assertThat(schedule(a).get("exceptions").get(0).get("date").asString()).isEqualTo("2026-12-31");
  }

  @Test
  void exceptionWritesAreAudited() {
    Facility f = facility();
    created(f, closed("2026-12-31"));
    post(f, closed("2026-12-31"));
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
    assertThat(rows)
        .contains("facility.exception-create SUCCESS", "facility.exception-create FAILURE");
  }
}
