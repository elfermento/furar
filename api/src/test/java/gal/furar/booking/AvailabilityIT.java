// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.port.in.UseAvailability;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.service.AvailabilityService;
import gal.furar.booking.domain.FreeSlot;
import gal.furar.facility.FacilityQueries;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.StatementCounter;
import gal.furar.testsupport.TestTokens;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Ocos libres de punta a punta (plan do dominio 2, D2-04): sen sesión, co horario, os usos e a
 * ocupación reais; borrador e de baixa → 404; pechada → sen ocos; «só amosar os ocos» → ocos sen
 * reservar; os días do cambio de hora; e a páxina de 12 instalacións cunha soa consulta de
 * ocupación.
 */
class AvailabilityIT extends AbstractIT {

  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  static final List<String> DAYS =
      List.of(
          "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY", "HOLIDAYS");

  @Autowired FacilityQueries facilityQueries;
  @Autowired BookingRepository bookingRepository;
  @Autowired TransactionTemplate tx;

  record Facility(String slug, UUID municipality, UUID id, UUID use) {}

  static String facilities(String slug) {
    return "/api/v1/municipalities/" + slug + "/facilities";
  }

  static String availability(Facility f, UUID use, LocalDate date) {
    return "/api/v1/municipalities/"
        + f.slug()
        + "/public/facilities/"
        + f.id()
        + "/availability?useId="
        + use
        + "&date="
        + date;
  }

  static LocalDate tomorrow() {
    return LocalDate.now(ZONE).plusDays(1);
  }

  long version(String slug, UUID facility) {
    return api()
        .get(facilities(slug) + "/" + facility)
        .token(TestTokens.manager(slug))
        .send()
        .etagVersion();
  }

  /** Unha instalación nova, en borrador: sen deportes, usos nin horario. */
  UUID draft(String slug, String name) {
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
    return id(created.json());
  }

  /**
   * Instalación publicada: un uso enteiro de 60 minutos e todos os días aberta nos tramos dados
   * (minutos), ata 2030.
   */
  Facility publish(String slug, UUID municipality, String name, boolean online, int... minutes) {
    UUID facility = draft(slug, name);
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
    UUID use = use(slug, facility, Map.of());
    Map<String, Object> season = new LinkedHashMap<>();
    season.put("name", "Todo o ano");
    season.put("startDate", "2026-01-01");
    season.put("endDate", "2030-12-31");
    Api.Response created =
        api()
            .post(facilities(slug) + "/" + facility + "/seasons", season)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    List<Map<String, Object>> intervals = new ArrayList<>();
    for (int i = 0; i < minutes.length; i += 2) {
      intervals.add(Map.of("startMinute", minutes[i], "endMinute", minutes[i + 1]));
    }
    List<Map<String, Object>> week = new ArrayList<>();
    DAYS.forEach(day -> week.add(Map.of("day", day, "intervals", intervals)));
    Map<String, Object> withWeek = new LinkedHashMap<>(season);
    withWeek.put("week", week);
    Api.Response hours =
        api()
            .put(
                facilities(slug)
                    + "/"
                    + facility
                    + "/seasons/"
                    + created.json().get("id").asString(),
                withWeek)
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(hours.status()).as(hours.toString()).isEqualTo(200);
    Api.Response published =
        api()
            .put(facilities(slug) + "/" + facility + "/publication", Map.of())
            .ifMatch(version(slug, facility))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(published.status()).as(published.toString()).isEqualTo(200);
    return new Facility(slug, municipality, facility, use);
  }

  /** Un uso enteiro de 60 minutos, sempre, cos campos de {@code extra}. */
  UUID use(String slug, UUID facility, Map<String, Object> extra) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Pista enteira");
    body.put("rentalMode", "WHOLE");
    body.put("slotMinutes", 60);
    body.put("availability", "ALWAYS");
    body.putAll(extra);
    Api.Response created =
        api()
            .post(facilities(slug) + "/" + facility + "/uses", body)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return id(created.json());
  }

  void status(Facility f, String status) {
    Api.Response changed =
        api()
            .put(facilities(f.slug()) + "/" + f.id() + "/status", Map.of("status", status))
            .ifMatch(version(f.slug(), f.id()))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(changed.status()).as(changed.toString()).isEqualTo(200);
  }

  /** Unha reserva confirmada do uso enteiro coa súa ocupación activa, directamente na base. */
  void occupy(Facility f, LocalDate date, int startMinute, int minutes) {
    UUID booking = UUID.randomUUID();
    OffsetDateTime start = date.atStartOfDay(ZONE).plusMinutes(startMinute).toOffsetDateTime();
    OffsetDateTime end = start.plusMinutes(minutes);
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, rental_mode, period, slots, status,
               payment_status, price_kind, cancel_until, holder_subject, holder_name,
               holder_email, holder_language, created_at, updated_at, version)
            values (:id, :m, :code, :f, :u, 'WHOLE', tstzrange(:start, :end, '[)'), 1,
                    'CONFIRMED', 'NONE', 'NONE', :start, 'sub-ana', 'Ana Rey', 'ana@example.org',
                    'gl', now(), now(), 0)
            """)
        .param("id", booking)
        .param("m", f.municipality())
        .param("code", "FUR-" + code())
        .param("f", f.id())
        .param("u", f.use())
        .param("start", start)
        .param("end", end)
        .update();
    jdbc.sql(
            """
            insert into booking_occupancy
              (id, municipality_id, booking_id, facility_id, use_id, rental_mode, period)
            values (:id, :m, :b, :f, :u, 'WHOLE', tstzrange(:start, :end, '[)'))
            """)
        .param("id", UUID.randomUUID())
        .param("m", f.municipality())
        .param("b", booking)
        .param("f", f.id())
        .param("u", f.use())
        .param("start", start)
        .param("end", end)
        .update();
  }

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static int codes;

  private static synchronized String code() {
    int n = codes++;
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      code.append(ALPHABET.charAt(n % ALPHABET.length()));
      n /= ALPHABET.length();
    }
    return code.toString();
  }

  static List<Integer> starts(JsonNode slots) {
    List<Integer> starts = new ArrayList<>();
    slots.forEach(s -> starts.add(s.get("startMinute").asInt()));
    return starts;
  }

  Facility municipalityWith(String prefix, String name, boolean online, int... minutes) {
    String slug = uniqueSlug(prefix);
    UUID municipality = id(createMunicipality(slug));
    return publish(slug, municipality, name, online, minutes);
  }

  @Test
  void anonymousSeesTheFreeSlotsOfAUseAlignedFromTheOpening() {
    Facility f = municipalityWith("ocos", "Pista de pádel", true, 570, 840);
    LocalDate day = tomorrow();
    occupy(f, day, 630, 60);

    Api.Response response = api().get(availability(f, f.use(), day)).send();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    JsonNode body = response.json();
    assertThat(body.get("date").asString()).isEqualTo(day.toString());
    assertThat(body.get("today").asString()).isEqualTo(LocalDate.now(ZONE).toString());
    assertThat(Instant.parse(body.get("now").asString())).isBefore(Instant.now().plusSeconds(1));
    assertThat(body.get("bookable").asBoolean()).isTrue();
    assertThat(starts(body.get("slots")))
        .as("9:30, 10:30 collida, 11:30 e 12:30; 13:30 non cabe ata as 14:00")
        .containsExactly(570, 690, 750);
    JsonNode first = body.get("slots").get(0);
    assertThat(Instant.parse(first.get("start").asString()))
        .isEqualTo(day.atStartOfDay(ZONE).plusMinutes(570).toInstant());
    assertThat(first.get("maxSlots").asInt()).isEqualTo(1);
    assertThat(body.get("slots").get(1).get("maxSlots").asInt()).isEqualTo(2);
    assertThat(first.path("freeParts").isMissingNode() || first.get("freeParts").isNull())
        .as("sen partes nun uso enteiro")
        .isTrue();

    Api.Response past = api().get(availability(f, f.use(), day.minusDays(2))).send();
    assertThat(past.status()).isEqualTo(200);
    assertThat(past.json().get("slots").size()).as("un día pasado non ten ocos").isZero();
  }

  @Test
  void aDraftOrWithdrawnFacilityOrAForeignUseIsNotFound() {
    Facility f = municipalityWith("ocos-404", "Pista de pádel", true, 570, 840);
    UUID draft = draft(f.slug(), "Ximnasio en borrador");
    UUID draftUse = use(f.slug(), draft, Map.of());
    Facility other = publish(f.slug(), f.municipality(), "Campo", true, 570, 840);
    LocalDate day = tomorrow();

    Api.Response inDraft =
        api().get(availability(new Facility(f.slug(), null, draft, null), draftUse, day)).send();
    Api.Response foreignUse = api().get(availability(f, other.use(), day)).send();
    Api.Response unknownUse = api().get(availability(f, UUID.randomUUID(), day)).send();

    assertThat(inDraft.status()).as(inDraft.toString()).isEqualTo(404);
    assertThat(inDraft.contentType()).startsWith("application/problem+json");
    assertThat(foreignUse.status()).isEqualTo(404);
    assertThat(unknownUse.status()).isEqualTo(404);

    status(other, "WITHDRAWN");
    assertThat(api().get(availability(other, other.use(), day)).send().status()).isEqualTo(404);
  }

  @Test
  void aTemporarilyClosedFacilityHasNoSlotsAndOneThatOnlyShowsThemCannotBeBooked() {
    Facility closed = municipalityWith("ocos-pech", "Pista de pádel", true, 570, 840);
    status(closed, "TEMPORARILY_CLOSED");
    Facility shown = municipalityWith("ocos-ver", "Frontón", false, 570, 840);
    LocalDate day = tomorrow();

    JsonNode none = api().get(availability(closed, closed.use(), day)).send().json();
    JsonNode noButton = api().get(availability(shown, shown.use(), day)).send().json();

    assertThat(none.get("slots").size()).isZero();
    assertThat(none.get("bookable").asBoolean()).isFalse();
    assertThat(starts(noButton.get("slots"))).containsExactly(570, 630, 690, 750);
    assertThat(noButton.get("bookable").asBoolean()).isFalse();
  }

  /** Os días do cambio de hora de 2026, coa véspera como «agora». */
  @Test
  void theDaysTheClocksChangeKeepTheWallClockAndTheRealInstants() {
    Facility f = municipalityWith("ocos-dst", "Pista de pádel", true, 60, 240, 570, 690);

    UseAvailability spring =
        asMunicipality(
            f,
            () ->
                at(Instant.parse("2026-03-28T12:00:00Z"))
                    .ofUse(f.id(), f.use(), LocalDate.of(2026, 3, 29)));
    UseAvailability autumn =
        asMunicipality(
            f,
            () ->
                at(Instant.parse("2026-10-24T12:00:00Z"))
                    .ofUse(f.id(), f.use(), LocalDate.of(2026, 10, 25)));

    assertThat(spring.slots().stream().map(FreeSlot::start))
        .as("de 1:00 a 4:00 só hai dúas horas reais o 29 de marzo")
        .containsExactly(
            Instant.parse("2026-03-29T00:00:00Z"),
            Instant.parse("2026-03-29T01:00:00Z"),
            Instant.parse("2026-03-29T07:30:00Z"),
            Instant.parse("2026-03-29T08:30:00Z"));
    assertThat(spring.slots().stream().map(FreeSlot::startMinute))
        .containsExactly(60, 180, 570, 630);
    assertThat(autumn.slots().stream().map(FreeSlot::start))
        .as("e catro o 25 de outubro (a 2:00 repítese)")
        .containsExactly(
            Instant.parse("2026-10-24T23:00:00Z"),
            Instant.parse("2026-10-25T00:00:00Z"),
            Instant.parse("2026-10-25T01:00:00Z"),
            Instant.parse("2026-10-25T02:00:00Z"),
            Instant.parse("2026-10-25T08:30:00Z"),
            Instant.parse("2026-10-25T09:30:00Z"));
    assertThat(autumn.today()).isEqualTo(LocalDate.of(2026, 10, 24));
  }

  /** O servizo cun reloxo parado: os días do cambio de hora de 2026 xa pasaron. */
  AvailabilityService at(Instant now) {
    return new AvailabilityService(
        facilityQueries, bookingRepository, Clock.fixed(now, ZoneOffset.UTC));
  }

  <T> T asMunicipality(Facility f, java.util.function.Supplier<T> call) {
    return TenantContext.runAs(TenantRef.of(f.municipality()), () -> tx.execute(s -> call.get()));
  }

  @Test
  void aPageOfTwelveFacilitiesReadsTheOccupancyOnceAndWorksWithoutSession() {
    String slug = uniqueSlug("ocos-lista");
    UUID municipality = id(createMunicipality(slug));
    List<Facility> page = new ArrayList<>();
    for (int i = 1; i <= 12; i++) {
      page.add(publish(slug, municipality, String.format("Pista %02d", i), true, 570, 840));
    }
    draft(slug, "Pista 00 en borrador");
    publish(slug, municipality, "Pista 13", true, 570, 840);
    LocalDate day = tomorrow();
    occupy(page.get(0), day, 570, 60);
    occupy(page.get(0), day, 630, 60);
    occupy(page.get(1), day, 750, 60);
    status(page.get(2), "TEMPORARILY_CLOSED");

    Api.Response response;
    List<String> occupancyReads;
    try (StatementCounter.Recording recording = StatementCounter.record()) {
      response =
          api()
              .get(
                  "/api/v1/municipalities/"
                      + slug
                      + "/public/availability?date="
                      + day
                      + "&page=0&size=12")
              .send();
      occupancyReads =
          recording.statements().stream().filter(s -> s.contains("booking_occupancy")).toList();
    }

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    assertThat(occupancyReads).as("unha consulta de ocupación por páxina").hasSize(1);
    JsonNode items = response.json().get("items");
    assertThat(items.size()).isEqualTo(12);
    List<String> ids = new ArrayList<>();
    items.forEach(item -> ids.add(item.get("facilityId").asString()));
    assertThat(ids)
        .as("en orde alfabética, sen o borrador nin a décimo terceira")
        .containsExactlyElementsOf(page.stream().map(p -> p.id().toString()).toList());
    assertThat(items.get(0).get("freeCount").asInt()).isEqualTo(2);
    assertThat(starts(items.get(0).get("firstStarts"))).containsExactly(690, 750);
    assertThat(items.get(1).get("freeCount").asInt()).isEqualTo(3);
    assertThat(starts(items.get(1).get("firstStarts"))).containsExactly(570, 630, 690);
    assertThat(items.get(2).get("freeCount").asInt()).as("pechada").isZero();
    assertThat(items.get(3).get("freeCount").asInt()).isEqualTo(4);
    assertThat(response.json().get("date").asString()).isEqualTo(day.toString());

    Api.Response next =
        api()
            .get("/api/v1/municipalities/" + slug + "/public/availability?date=" + day + "&page=1")
            .send();
    assertThat(next.status()).isEqualTo(200);
    assertThat(next.json().get("items").size()).isEqualTo(1);
  }

  @Test
  void theOccupancyOfAnotherMunicipalityDoesNotCount() {
    Facility mine = municipalityWith("ocos-a", "Pista de pádel", true, 570, 690);
    Facility theirs = municipalityWith("ocos-b", "Pista de pádel", true, 570, 690);
    LocalDate day = tomorrow();
    occupy(theirs, day, 570, 60);

    JsonNode own = api().get(availability(mine, mine.use(), day)).send().json();
    JsonNode other = api().get(availability(theirs, theirs.use(), day)).send().json();
    Api.Response crossed =
        api()
            .get(
                availability(new Facility(mine.slug(), null, theirs.id(), null), theirs.use(), day))
            .send();

    assertThat(starts(own.get("slots"))).containsExactly(570, 630);
    assertThat(starts(other.get("slots"))).containsExactly(630);
    assertThat(crossed.status()).as("a instalación doutro concello non existe aquí").isEqualTo(404);
  }
}
