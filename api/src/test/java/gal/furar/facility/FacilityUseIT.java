// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Usos de punta a punta con PostgreSQL real (D1-12): modos, períodos, nome repetido, arquivo que
 * conserva as filas, {@code If-Match} por uso, intercambio de nomes de partes nun gardado
 * (restrición diferida), erro diferido traducido, dous usos que bloquean solapados, borrador e
 * modal de reservas afectadas cun dobre de {@link BookingImpact}.
 */
class FacilityUseIT extends AbstractIT {

  @Autowired ProgrammableBookingImpact impact;

  record Facility(String slug, UUID id, String url) {
    String uses() {
      return url + "/uses";
    }
  }

  Facility facility() {
    String slug = uniqueSlug("uso");
    createMunicipality(slug);
    String base = "/api/v1/municipalities/" + slug + "/facilities";
    Api.Response created =
        api()
            .post(
                base,
                Map.of("name", "Campo de fútbol 1 de O Alto", "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID id = id(created.json());
    return new Facility(slug, id, base + "/" + id);
  }

  static Map<String, Object> use(String name, String mode) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("rentalMode", mode);
    body.put("slotMinutes", 60);
    body.put("availability", "ALWAYS");
    return body;
  }

  static Map<String, Object> parts(String name, Object... parts) {
    Map<String, Object> body = use(name, "PARTS");
    List<Object> list = new ArrayList<>();
    for (Object part : parts) {
      list.add(part instanceof String s ? Map.of("name", s) : part);
    }
    body.put("parts", list);
    return body;
  }

  static Map<String, Object> blocking(String name, String from, String to) {
    Map<String, Object> body = use(name, "WHOLE");
    body.put("availability", "DATES");
    body.put("blocksOthers", true);
    body.put("periods", List.of(Map.of("from", from, "to", to)));
    return body;
  }

  Api.Response post(Facility f, Map<String, Object> body) {
    return api().post(f.uses(), body).token(TestTokens.manager(f.slug())).send();
  }

  Api.Response put(Facility f, UUID use, long version, Map<String, Object> body) {
    return api()
        .put(f.uses() + "/" + use, body)
        .ifMatch(version)
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  JsonNode created(Facility f, Map<String, Object> body) {
    Api.Response response = post(f, body);
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return response.json();
  }

  static void assertProblem(Api.Response response, int status, String code) {
    assertThat(response.status()).as(response.toString()).isEqualTo(status);
    assertThat(response.json().get("code").asString()).isEqualTo(code);
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

  @Test
  void createReadAndUpdateWithIfMatchOfTheUse() {
    Facility f = facility();
    Api.Response created = post(f, use("Fútbol 11", "WHOLE"));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID useId = id(created.json());
    assertThat(created.header("Location")).endsWith("/facilities/" + f.id() + "/uses/" + useId);
    assertThat(created.etagVersion()).isZero();
    assertThat(created.json().get("rentalMode").asString()).isEqualTo("WHOLE");
    assertThat(created.json().get("parts").size()).isZero();
    assertThat(missing(f)).isEqualTo("[\"SPORTS\",\"SCHEDULE\"]");

    Api.Response one = api().get(f.uses() + "/" + useId).token(TestTokens.staff(f.slug())).send();
    assertThat(one.status()).isEqualTo(200);
    assertThat(one.etagVersion()).isZero();
    Api.Response list = api().get(f.uses()).token(TestTokens.staff(f.slug())).send();
    assertThat(list.json().size()).isEqualTo(1);
    assertThat(list.json().get(0).get("version").asLong()).isZero();

    Map<String, Object> pool = use("Natación libre", "SEATS");
    pool.put("capacity", 30);
    pool.put("slotMinutes", 30);
    Api.Response updated = put(f, useId, 0, pool);
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    assertThat(updated.etagVersion()).isEqualTo(1);
    assertThat(updated.json().get("capacity").asInt()).isEqualTo(30);

    assertProblem(put(f, useId, 0, pool), 412, "common.stale-version");
    Api.Response without =
        api().put(f.uses() + "/" + useId, pool).token(TestTokens.manager(f.slug())).send();
    assertProblem(without, 428, "common.precondition-required");
  }

  @Test
  void eachModeHasItsRules() {
    Facility f = facility();
    assertProblem(post(f, parts("Fútbol 7", "Campo A")), 422, "facility.use-parts-required");
    assertProblem(post(f, use("Natación", "SEATS")), 422, "facility.use-capacity-required");
    Map<String, Object> tooMany = use("Natación", "SEATS");
    tooMany.put("capacity", 10_001);
    Api.Response invalid = post(f, tooMany);
    assertProblem(invalid, 422, "facility.use-invalid");
    assertThat(invalid.json().get("errors").get(0).get("field").asString()).isEqualTo("capacity");
    Map<String, Object> dates = use("Samaín", "WHOLE");
    dates.put("availability", "DATES");
    assertProblem(post(f, dates), 422, "facility.use-dates-required");
    Map<String, Object> blocksAlways = use("Torneo", "WHOLE");
    blocksAlways.put("blocksOthers", true);
    assertProblem(post(f, blocksAlways), 422, "facility.use-blocks-others-needs-dates");
    Map<String, Object> slot = use("Pádel", "WHOLE");
    slot.put("slotMinutes", 45);
    assertProblem(post(f, slot), 422, "facility.use-invalid");

    JsonNode athletics = created(f, parts("Atletismo", "Pista", "Lonxitude", "Céspede"));
    List<String> names = new ArrayList<>();
    athletics.get("parts").forEach(p -> names.add(p.get("name").asString()));
    assertThat(names).containsExactly("Pista", "Lonxitude", "Céspede");
    assertThat(missing(f)).doesNotContain("USES");
  }

  @Test
  void periodsCannotOverlapAndAreReplacedWhole() {
    Facility f = facility();
    Map<String, Object> body = use("Xornadas de Samaín", "WHOLE");
    body.put("availability", "DATES");
    body.put(
        "periods",
        List.of(
            Map.of("from", "2026-10-30", "to", "2026-11-01"),
            Map.of("from", "2026-11-01", "to", "2026-11-02")));
    assertProblem(post(f, body), 422, "facility.use-period-overlap");

    body.put(
        "periods",
        List.of(
            Map.of("from", "2026-11-01", "to", "2026-11-01"),
            Map.of("from", "2026-10-30", "to", "2026-10-31")));
    JsonNode samain = created(f, body);
    assertThat(samain.get("periods").get(0).get("from").asString()).isEqualTo("2026-10-30");

    // As mesmas datas outra vez: se os INSERT fosen antes ca os DELETE, a exclusión saltaría.
    Api.Response same = put(f, id(samain), 0, body);
    assertThat(same.status()).as(same.toString()).isEqualTo(200);
    body.put("periods", List.of(Map.of("from", "2026-12-24", "to", "2027-01-06")));
    Api.Response replaced = put(f, id(samain), 1, body);
    assertThat(replaced.status()).as(replaced.toString()).isEqualTo(200);
    assertThat(replaced.json().get("periods").size()).isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from facility_use_period where use_id = :u")
                .param("u", id(samain))
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void namesAreUniqueAmongLiveUsesIgnoringCase() {
    Facility f = facility();
    JsonNode first = created(f, use("Fútbol 11", "WHOLE"));
    assertProblem(post(f, use("FÚTBOL 11", "WHOLE")), 409, "facility.use-name-taken");
    JsonNode other = created(f, use("Fútbol 7", "WHOLE"));
    assertProblem(put(f, id(other), 0, use("fútbol 11", "WHOLE")), 409, "facility.use-name-taken");

    Api.Response archived =
        api()
            .post(f.uses() + "/" + id(first) + "/archive", Map.of())
            .ifMatch(0)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(archived.status()).as(archived.toString()).isEqualTo(200);
    assertThat(post(f, use("Fútbol 11", "WHOLE")).status()).isEqualTo(201);
  }

  @Test
  void archiveKeepsTheRowsAndBringsBackTheDraft() {
    Facility f = facility();
    JsonNode seven = created(f, parts("Fútbol 7", "Campo A", "Campo B"));
    UUID useId = id(seven);
    assertThat(missing(f)).doesNotContain("USES");

    String archive = f.uses() + "/" + useId + "/archive";
    assertThat(api().post(archive, Map.of()).token(TestTokens.manager(f.slug())).send().status())
        .isEqualTo(428);
    Api.Response archived =
        api().post(archive, Map.of()).ifMatch(0).token(TestTokens.manager(f.slug())).send();
    assertThat(archived.status()).as(archived.toString()).isEqualTo(200);
    assertThat(archived.json().get("archivedAt").isNull()).isFalse();
    assertThat(archived.etagVersion()).isEqualTo(1);

    assertThat(api().get(f.uses()).token(TestTokens.staff(f.slug())).send().json().size()).isZero();
    assertThat(missing(f)).contains("USES");
    assertThat(
            jdbc.sql("select count(*) from facility_use where id = :u and archived_at is not null")
                .param("u", useId)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from facility_use_part where use_id = :u")
                .param("u", useId)
                .query(Integer.class)
                .single())
        .isEqualTo(2);
    assertProblem(put(f, useId, 1, use("Fútbol 7", "WHOLE")), 409, "facility.use-archived");
  }

  @Test
  void swappingTwoPartNamesInOneSaveWorks() {
    Facility f = facility();
    JsonNode seven = created(f, parts("Fútbol 7", "Campo A", "Campo B"));
    UUID a = UUID.fromString(seven.get("parts").get(0).get("id").asString());
    UUID b = UUID.fromString(seven.get("parts").get(1).get("id").asString());

    Api.Response swapped =
        put(
            f,
            id(seven),
            0,
            parts(
                "Fútbol 7",
                Map.of("id", a, "name", "Campo B"),
                Map.of("id", b, "name", "Campo A")));
    assertThat(swapped.status()).as(swapped.toString()).isEqualTo(200);
    JsonNode parts = swapped.json().get("parts");
    assertThat(parts.get(0).get("id").asString()).isEqualTo(a.toString());
    assertThat(parts.get(0).get("name").asString()).isEqualTo("Campo B");
    assertThat(parts.get(1).get("name").asString()).isEqualTo("Campo A");

    // Quitar unha parte e crear outra co seu nome no mesmo gardado: a vella arquívase.
    Api.Response replaced =
        put(f, id(seven), 1, parts("Fútbol 7", Map.of("id", a, "name", "Campo B"), "Campo A"));
    assertThat(replaced.status()).as(replaced.toString()).isEqualTo(200);
    assertThat(replaced.json().get("parts").get(1).get("id").asString()).isNotEqualTo(b.toString());
    assertThat(
            jdbc.sql("select archived_at is not null from facility_use_part where id = :p")
                .param("p", b)
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  void repeatedPartNamesFailAtTheDeferredCheckWithTheirOwnCode() {
    Facility f = facility();
    assertProblem(
        post(f, parts("Fútbol 7", "Campo A", "CAMPO A")), 409, "facility.use-part-name-taken");
    assertThat(api().get(f.uses()).token(TestTokens.staff(f.slug())).send().json().size()).isZero();

    JsonNode seven = created(f, parts("Fútbol 7", "Campo A", "Campo B"));
    UUID a = UUID.fromString(seven.get("parts").get(0).get("id").asString());
    UUID b = UUID.fromString(seven.get("parts").get(1).get("id").asString());
    assertProblem(
        put(
            f,
            id(seven),
            0,
            parts(
                "Fútbol 7",
                Map.of("id", a, "name", "Campo A"),
                Map.of("id", b, "name", "campo a"))),
        409,
        "facility.use-part-name-taken");
    // Nada quedou a medias: a versión e os nomes son os de antes.
    Api.Response after =
        api().get(f.uses() + "/" + id(seven)).token(TestTokens.staff(f.slug())).send();
    assertThat(after.etagVersion()).isZero();
    assertThat(after.json().get("parts").get(1).get("name").asString()).isEqualTo("Campo B");
  }

  @Test
  void twoBlockingUsesWithOverlappingDatesAre409() {
    Facility f = facility();
    created(f, blocking("Torneo de verán", "2026-07-01", "2026-07-31"));
    Api.Response clash = post(f, blocking("Campus", "2026-07-31", "2026-08-15"));
    assertProblem(clash, 409, "facility.exclusive-use-overlap");
    assertThat(clash.json().get("params").get("name").asString()).isEqualTo("Torneo de verán");
    assertThat(post(f, blocking("Campus", "2026-08-01", "2026-08-15")).status()).isEqualTo(201);
  }

  @Test
  void aBlockingUseOpensTheAffectedBookingsModal() {
    Facility f = facility();
    UUID booking = UUID.randomUUID();
    impact.affect(
        f.id(),
        List.of(
            new AffectedBooking(
                booking, "Uxía Pérez", true, LocalDate.of(2026, 11, 3), 480, 570, "Fútbol 7")));
    Map<String, Object> body = blocking("Torneo de Samaín", "2026-11-01", "2026-11-05");
    Api.Response first = post(f, body);
    assertProblem(first, 409, "facility.affected-bookings");
    assertThat(first.json().get("affected").get(0).get("bookingId").asString())
        .isEqualTo(booking.toString());

    body.put(
        "decisions",
        List.of(Map.of("bookingId", booking, "action", "CANCEL", "channels", List.of("EMAIL"))));
    Api.Response second = post(f, body);
    assertThat(second.status()).as(second.toString()).isEqualTo(201);
    assertThat(impact.appliedTo(f.id()))
        .singleElement()
        .satisfies(
            applied ->
                assertThat(applied.change())
                    .isEqualTo(
                        new FacilityChange.ExclusiveUse(
                            f.id(),
                            id(second.json()),
                            List.of(
                                new FacilityChange.Dates(
                                    LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5))))));

    // Con reservas futuras, cambiar a duración ou quitar o uso non se deixa (P1).
    body.remove("decisions");
    body.put("slotMinutes", 90);
    assertProblem(put(f, id(second.json()), 0, body), 409, "facility.use-has-bookings");
    Api.Response archive =
        api()
            .post(f.uses() + "/" + id(second.json()) + "/archive", Map.of())
            .ifMatch(0)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertProblem(archive, 409, "facility.use-has-bookings");
  }

  @Test
  void onlyManagersWriteAndEachMunicipalitySeesItsOwn() {
    Facility f = facility();
    JsonNode football = created(f, use("Fútbol 11", "WHOLE"));
    Api.Response staff =
        api().post(f.uses(), use("Fútbol 7", "WHOLE")).token(TestTokens.staff(f.slug())).send();
    assertProblem(staff, 403, "auth.forbidden");
    assertThat(api().get(f.uses()).token(TestTokens.resident(f.slug())).send().status())
        .isEqualTo(403);
    assertThat(api().get(f.uses()).send().status()).isEqualTo(401);

    // Outro concello: a instalación non existe para el.
    String other = uniqueSlug("uso-b");
    createMunicipality(other);
    String foreign = "/api/v1/municipalities/" + other + "/facilities/" + f.id() + "/uses";
    assertProblem(
        api().get(foreign).token(TestTokens.manager(other)).send(), 404, "facility.not-found");
    assertProblem(
        api().post(foreign, use("Intrusa", "WHOLE")).token(TestTokens.manager(other)).send(),
        404,
        "facility.not-found");

    // O uso doutra instalación do mesmo concello tampouco.
    Facility second = facility();
    assertProblem(
        api().get(second.uses() + "/" + id(football)).token(TestTokens.staff(second.slug())).send(),
        404,
        "facility.use-not-found");
  }

  @Test
  void writesAreAudited() {
    Facility f = facility();
    created(f, use("Fútbol 11", "WHOLE"));
    post(f, use("FÚTBOL 11", "WHOLE"));
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
    assertThat(rows).contains("facility.use-create SUCCESS", "facility.use-create FAILURE");
  }

  // Condicións do uso (D2-02).

  static Map<String, Object> withTerms(Map<String, Object> body) {
    body.put("weekdays", 1 | 2 | 4 | 8 | 16);
    body.put("price", Map.of("kind", "PAID", "amountCents", 3000));
    Map<String, Object> rules = new LinkedHashMap<>();
    rules.put("minAdvanceHours", 2);
    rules.put("maxAdvanceDays", 7);
    rules.put("maxSlots", 2);
    rules.put("capCount", 2);
    rules.put("capPeriod", "WEEK");
    rules.put("cancelHours", 24);
    body.put("rules", rules);
    return body;
  }

  @Test
  void savesAndReadsWeekdaysPriceAndRulesWithTheIfMatchOfTheUse() {
    Facility f = facility();
    JsonNode plain = created(f, use("Fútbol 7", "WHOLE"));
    assertThat(plain.get("weekdays").asInt()).isEqualTo(127);
    assertThat(plain.get("price").get("kind").asString()).isEqualTo("NONE");
    assertThat(plain.get("price").get("per").asString()).isEqualTo("BOOKING");
    assertThat(plain.get("rules").get("maxSlots").isNull()).isTrue();
    UUID useId = id(plain);

    Api.Response updated = put(f, useId, 0, withTerms(use("Fútbol 7", "WHOLE")));
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    assertThat(updated.etagVersion()).isEqualTo(1);
    JsonNode read =
        api().get(f.uses() + "/" + useId).token(TestTokens.staff(f.slug())).send().json();
    assertThat(read.get("weekdays").asInt()).isEqualTo(31);
    assertThat(read.get("price").get("kind").asString()).isEqualTo("PAID");
    assertThat(read.get("price").get("amountCents").asInt()).isEqualTo(3000);
    JsonNode rules = read.get("rules");
    assertThat(rules.get("minAdvanceHours").asInt()).isEqualTo(2);
    assertThat(rules.get("maxAdvanceDays").asInt()).isEqualTo(7);
    assertThat(rules.get("maxSlots").asInt()).isEqualTo(2);
    assertThat(rules.get("capCount").asInt()).isEqualTo(2);
    assertThat(rules.get("capPeriod").asString()).isEqualTo("WEEK");
    assertThat(rules.get("cancelHours").asInt()).isEqualTo(24);

    assertProblem(
        put(f, useId, 0, withTerms(use("Fútbol 7", "WHOLE"))), 412, "common.stale-version");
    Api.Response without =
        api()
            .put(f.uses() + "/" + useId, withTerms(use("Fútbol 7", "WHOLE")))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(without.status()).isEqualTo(428);

    Map<String, Object> seats = use("Natación libre", "SEATS");
    seats.put("capacity", 30);
    seats.put("price", Map.of("kind", "FREE"));
    Api.Response emptied = put(f, useId, 1, seats);
    assertThat(emptied.status()).as(emptied.toString()).isEqualTo(200);
    assertThat(emptied.json().get("weekdays").asInt()).isEqualTo(127);
    assertThat(emptied.json().get("price").get("kind").asString()).isEqualTo("FREE");
    assertThat(emptied.json().get("price").get("per").asString()).isEqualTo("PERSON");
    assertThat(emptied.json().get("rules").get("capCount").isNull()).isTrue();
  }

  @Test
  void refusesAPaidPriceWithoutAmountNoWeekdaysAndRulesOutOfRange() {
    Facility f = facility();
    Map<String, Object> paid = use("Pádel", "WHOLE");
    paid.put("price", Map.of("kind", "PAID"));
    assertProblem(post(f, paid), 422, "facility.use-price-invalid");

    Map<String, Object> noDays = use("Pádel", "WHOLE");
    noDays.put("weekdays", 0);
    assertProblem(post(f, noDays), 422, "facility.use-weekdays-invalid");

    Map<String, Object> rules = use("Pádel", "WHOLE");
    rules.put("rules", Map.of("maxSlots", 17));
    Api.Response refused = post(f, rules);
    assertProblem(refused, 422, "facility.use-rule-invalid");
    assertThat(refused.json().get("errors").get(0).get("field").asString())
        .isEqualTo("rules.maxSlots");

    Map<String, Object> cap = use("Pádel", "WHOLE");
    cap.put("rules", Map.of("capCount", 2));
    assertProblem(post(f, cap), 422, "facility.use-rule-invalid");
    assertThat(api().get(f.uses()).token(TestTokens.staff(f.slug())).send().json().size()).isZero();
  }
}
