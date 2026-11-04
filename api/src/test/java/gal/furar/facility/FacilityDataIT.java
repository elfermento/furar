// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Seccións da propia instalación (D1-11b) con PostgreSQL real: Datos (deportes incluídos),
 * Descrición, Localización e Servizos con {@code If-Match}; o catálogo de deportes de só lectura e
 * «+ crear»; deporte repetido polos índices parciais; reserva por internet e ocos; o borrador sen
 * {@code SPORTS}; e illamento entre concellos.
 */
class FacilityDataIT extends AbstractIT {

  static String base(String slug) {
    return FacilityIT.base(slug);
  }

  JsonNode created(String slug, String name) {
    Api.Response response =
        api()
            .post(base(slug), Map.of("name", name, "address", FacilityIT.address()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return response.json();
  }

  static Map<String, Object> details(List<Map<String, Object>> sports) {
    Map<String, Object> details = new HashMap<>();
    details.put("sports", sports);
    details.put("type", "FIELD");
    details.put("ridegaCode", "15-054-0012");
    details.put("phone", "981 38 21 00");
    details.put("email", "deportes@naron.es");
    details.put("onlineBooking", true);
    details.put("showFreeSlots", false);
    details.put("covered", false);
    return details;
  }

  static Map<String, Object> catalog(String code) {
    return Map.of("code", code);
  }

  Api.Response put(String slug, JsonNode facility, String section, Object body, long version) {
    return api()
        .put(base(slug) + "/" + id(facility) + "/" + section, body)
        .ifMatch(version)
        .token(TestTokens.manager(slug))
        .send();
  }

  Api.Response createSport(String slug, String gl, String es) {
    return api()
        .post("/api/v1/municipalities/" + slug + "/sports", Map.of("gl", gl, "es", es))
        .token(TestTokens.manager(slug))
        .send();
  }

  @Test
  void detailsSaveTheWholeCardAndAddingASportLeavesDraftWithoutSports() {
    String slug = uniqueSlug("dat");
    createMunicipality(slug);
    JsonNode facility = created(slug, "Campo de fútbol 1");
    assertThat(facility.get("details").get("onlineBooking").asBoolean()).isTrue();
    assertThat(facility.get("details").get("sports").size()).isZero();

    Api.Response saved =
        put(
            slug,
            facility,
            "details",
            details(List.of(catalog("football"), catalog("football_7"))),
            0);
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.etagVersion()).isEqualTo(1);
    JsonNode body = saved.json();
    assertThat(body.get("missing").toString()).isEqualTo("[\"USES\",\"SCHEDULE\"]");
    JsonNode d = body.get("details");
    assertThat(d.get("type").asString()).isEqualTo("FIELD");
    assertThat(d.get("ridegaCode").asString()).isEqualTo("15-054-0012");
    assertThat(d.get("covered").asBoolean()).isFalse();
    assertThat(d.get("sports").get(0).get("code").asString()).isEqualTo("football");
    assertThat(d.get("sports").get(1).get("label").get("gl").asString()).isEqualTo("Fútbol 7");

    Api.Response list = api().get(base(slug)).token(TestTokens.staff(slug)).send();
    assertThat(list.json().get("items").get(0).get("missing").size()).isEqualTo(2);

    Api.Response fewer = put(slug, facility, "details", details(List.of()), 1);
    assertThat(fewer.status()).as(fewer.toString()).isEqualTo(200);
    assertThat(fewer.json().get("missing").get(0).asString()).isEqualTo("SPORTS");
    assertThat(
            jdbc.sql("select count(*) from facility_sport where facility_id = :id")
                .param("id", id(facility))
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void everySectionNeedsIfMatchWithTheCurrentVersion() {
    String slug = uniqueSlug("dat");
    createMunicipality(slug);
    JsonNode facility = created(slug, "Pavillón");
    Map<String, Object> moved = FacilityIT.address();
    moved.put("street", "Avenida do Mar");
    moved.put("streetNumber", null);
    Map<String, Object> description = new LinkedHashMap<>();
    description.put("gl", "Pista cuberta con bancadas.");
    description.put("es", "Pista cubierta con gradas.");
    Map<String, Object> sections = new LinkedHashMap<>();
    sections.put("details", details(List.of(catalog("handball"))));
    sections.put("description", description);
    sections.put("address", moved);
    sections.put("amenities", Map.of("amenities", List.of("SHOWERS", "CHANGING_ROOMS")));

    long version = 0;
    for (Map.Entry<String, Object> section : sections.entrySet()) {
      String url = base(slug) + "/" + id(facility) + "/" + section.getKey();
      Api.Response without =
          api().put(url, section.getValue()).token(TestTokens.manager(slug)).send();
      assertThat(without.status()).as(section.getKey()).isEqualTo(428);
      Api.Response saved = put(slug, facility, section.getKey(), section.getValue(), version);
      assertThat(saved.status()).as(section.getKey() + " " + saved).isEqualTo(200);
      assertThat(saved.etagVersion()).as(section.getKey()).isEqualTo(version + 1);
      Api.Response stale = put(slug, facility, section.getKey(), section.getValue(), version);
      assertThat(stale.status()).as(section.getKey()).isEqualTo(412);
      assertThat(stale.json().get("code").asString()).isEqualTo("common.stale-version");
      version++;
    }

    Api.Response staff =
        api()
            .put(base(slug) + "/" + id(facility) + "/amenities", Map.of("amenities", List.of()))
            .ifMatch(version)
            .token(TestTokens.staff(slug))
            .send();
    assertThat(staff.status()).isEqualTo(403);

    JsonNode one =
        api().get(base(slug) + "/" + id(facility)).token(TestTokens.staff(slug)).send().json();
    assertThat(one.get("version").asLong()).isEqualTo(4);
    assertThat(one.get("details").get("sports").get(0).get("code").asString())
        .isEqualTo("handball");
    assertThat(one.get("description").get("es").asString()).isEqualTo("Pista cubierta con gradas.");
    assertThat(one.get("address").get("street").asString()).isEqualTo("Avenida do Mar");
    assertThat(one.get("address").get("streetNumber").isNull()).isTrue();
    assertThat(one.get("amenities").toString()).isEqualTo("[\"CHANGING_ROOMS\",\"SHOWERS\"]");

    Map<String, Object> none = new HashMap<>();
    none.put("gl", "");
    none.put("es", null);
    Api.Response cleared = put(slug, facility, "description", none, 4);
    assertThat(cleared.status()).as(cleared.toString()).isEqualTo(200);
    assertThat(cleared.json().get("description").isNull()).isTrue();

    Api.Response half = put(slug, facility, "description", Map.of("gl", "Só galego"), 5);
    assertThat(half.status()).isEqualTo(422);
    assertThat(half.json().get("code").asString()).isEqualTo("facility.description-invalid");
    assertThat(half.json().get("errors").get(0).get("field").asString())
        .isEqualTo("description.es");

    Map<String, Object> broken = FacilityIT.address();
    broken.remove("ineCode");
    Api.Response noAddress = put(slug, facility, "address", broken, 5);
    assertThat(noAddress.status()).isEqualTo(422);
    assertThat(noAddress.json().get("code").asString()).isEqualTo("facility.address-required");

    Api.Response unknownAmenity =
        put(slug, facility, "amenities", Map.of("amenities", List.of("SAUNA")), 5);
    assertThat(unknownAmenity.status()).isEqualTo(400);
  }

  @Test
  void onlineBookingAndFreeSlotsNeverGoTogether() {
    String slug = uniqueSlug("dat");
    createMunicipality(slug);
    JsonNode facility = created(slug, "Pista de pádel 1");
    Map<String, Object> both = details(List.of());
    both.put("showFreeSlots", true);
    Api.Response rejected = put(slug, facility, "details", both, 0);
    assertThat(rejected.status()).as(rejected.toString()).isEqualTo(422);
    assertThat(rejected.json().get("code").asString()).isEqualTo("facility.booking-mode-invalid");

    both.put("onlineBooking", false);
    Api.Response slots = put(slug, facility, "details", both, 0);
    assertThat(slots.status()).as(slots.toString()).isEqualTo(200);
    assertThat(slots.json().get("details").get("showFreeSlots").asBoolean()).isTrue();

    Map<String, Object> bad = details(List.of());
    bad.put("phone", "teléfono");
    bad.put("email", "sen arroba");
    Api.Response invalid = put(slug, facility, "details", bad, 1);
    assertThat(invalid.status()).isEqualTo(422);
    assertThat(invalid.json().get("code").asString()).isEqualTo("facility.details-invalid");
    List<String> fields = new ArrayList<>();
    invalid.json().get("errors").forEach(e -> fields.add(e.get("field").asString()));
    assertThat(fields).containsExactly("details.phone", "details.email");
  }

  @Test
  void theBaseRejectsBookingOnlineWithFreeSlots() {
    String slug = uniqueSlug("dat");
    createMunicipality(slug);
    JsonNode facility = created(slug, "Comprobada");
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "update facility set online_booking = true, show_free_slots = true where id = :id")
                    .param("id", id(facility))
                    .update())
        .hasMessageContaining("facility_booking_mode_ck");
  }

  @Test
  void aRepeatedSportIsAConflictFromThePartialIndexes() {
    String slug = uniqueSlug("dat");
    createMunicipality(slug);
    JsonNode facility = created(slug, "Repetidos");
    Api.Response twice =
        put(slug, facility, "details", details(List.of(catalog("padel"), catalog("padel"))), 0);
    assertThat(twice.status()).as(twice.toString()).isEqualTo(409);
    assertThat(twice.json().get("code").asString()).isEqualTo("facility.sport-duplicated");

    String custom =
        createSport(slug, "Bolos celtas", "Bolos celtas").json().get("customSportId").asString();
    Api.Response twiceCustom =
        put(
            slug,
            facility,
            "details",
            details(List.of(Map.of("customSportId", custom), Map.of("customSportId", custom))),
            0);
    assertThat(twiceCustom.status()).isEqualTo(409);
    assertThat(twiceCustom.json().get("code").asString()).isEqualTo("facility.sport-duplicated");

    Api.Response unknown =
        put(slug, facility, "details", details(List.of(catalog("quidditch"))), 0);
    assertThat(unknown.status()).isEqualTo(422);
    assertThat(unknown.json().get("code").asString()).isEqualTo("facility.sport-unknown");

    Api.Response both =
        put(
            slug,
            facility,
            "details",
            details(List.of(Map.of("code", "padel", "customSportId", custom))),
            0);
    assertThat(both.status()).isEqualTo(422);
    assertThat(both.json().get("code").asString()).isEqualTo("facility.details-invalid");

    Api.Response ok =
        put(
            slug,
            facility,
            "details",
            details(List.of(catalog("padel"), Map.of("customSportId", custom))),
            0);
    assertThat(ok.status()).as(ok.toString()).isEqualTo(200);
    assertThat(ok.json().get("details").get("sports").get(1).get("label").get("es").asString())
        .isEqualTo("Bolos celtas");
  }

  @Test
  void sportsListTheCatalogAndOwnSportsAndCreateNewOnes() {
    String a = uniqueSlug("dep-a");
    String b = uniqueSlug("dep-b");
    createMunicipality(a);
    createMunicipality(b);
    String url = "/api/v1/municipalities/" + a + "/sports";

    Api.Response created = createSport(a, "Bolos celtas", "Bolos celtas");
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    assertThat(created.json().get("code").isNull()).isTrue();
    assertThat(created.json().get("label").get("gl").asString()).isEqualTo("Bolos celtas");

    Api.Response again = createSport(a, "BOLOS CELTAS", "Bolos");
    assertThat(again.status()).isEqualTo(409);
    assertThat(again.json().get("code").asString()).isEqualTo("facility.sport-name-taken");
    Api.Response half = createSport(a, "Só galego", " ");
    assertThat(half.status()).isEqualTo(422);
    assertThat(half.json().get("code").asString()).isEqualTo("facility.sport-name-invalid");
    assertThat(createSport(b, "Bolos celtas", "Bolos celtas").status()).isEqualTo(201);

    for (String language : List.of("gl", "es")) {
      Api.Response list = api().get(url).token(TestTokens.staff(a)).language(language).send();
      assertThat(list.status()).as(list.toString()).isEqualTo(200);
      List<String> labels = new ArrayList<>();
      list.json().forEach(s -> labels.add(s.get("label").get(language).asString()));
      assertThat(labels).hasSizeGreaterThanOrEqualTo(31).contains("Bolos celtas", "Pádel");
      assertThat(labels.indexOf("Bolos celtas")).isLessThan(labels.indexOf("Pádel"));
      assertThat(labels).filteredOn("Bolos celtas"::equals).hasSize(1);
    }
    Api.Response es = api().get(url).token(TestTokens.staff(a)).language("es").send();
    List<String> spanish = new ArrayList<>();
    es.json().forEach(s -> spanish.add(s.get("label").get("es").asString()));
    assertThat(spanish).contains("Balonmano");

    assertThat(
            api()
                .post(url, Map.of("gl", "Persoal", "es", "Personal"))
                .token(TestTokens.staff(a))
                .send()
                .status())
        .isEqualTo(403);
    assertThat(api().get(url).send().status()).isEqualTo(401);
    assertThat(api().get(url).token(TestTokens.resident(a)).send().status()).isEqualTo(403);
  }

  @Test
  void anotherMunicipalityNeitherChangesNorUsesOwnSports() {
    String a = uniqueSlug("dat-a");
    String b = uniqueSlug("dat-b");
    createMunicipality(a);
    createMunicipality(b);
    JsonNode inA = created(a, "Só en A");
    JsonNode inB = created(b, "Só en B");
    String sportOfA =
        createSport(a, "Pickleball", "Pickleball").json().get("customSportId").asString();

    Api.Response fromB =
        api()
            .put(base(b) + "/" + id(inA) + "/amenities", Map.of("amenities", List.of("CAFE")))
            .ifMatch(0)
            .token(TestTokens.manager(b))
            .send();
    assertThat(fromB.status()).isEqualTo(404);
    Api.Response borrowed =
        put(b, inB, "details", details(List.of(Map.of("customSportId", sportOfA))), 0);
    assertThat(borrowed.status()).as(borrowed.toString()).isEqualTo(422);
    assertThat(borrowed.json().get("code").asString()).isEqualTo("facility.sport-unknown");

    Api.Response sportsOfB =
        api().get("/api/v1/municipalities/" + b + "/sports").token(TestTokens.manager(b)).send();
    assertThat(sportsOfB.body()).doesNotContain("Pickleball");
  }

  /** ADR-0006: o catálogo é da plataforma e a API só o le. */
  @Test
  void apiRoleCannotWriteTheSportCatalog() {
    assertThat(appJdbc.sql("select count(*) from sport_catalog").query(Long.class).single())
        .isGreaterThanOrEqualTo(30);
    for (String sql :
        List.of(
            "insert into sport_catalog (code, label) values ('quidditch', '{\"gl\":\"x\",\"es\":\"x\"}')",
            "update sport_catalog set label = '{\"gl\":\"x\",\"es\":\"x\"}'",
            "delete from sport_catalog",
            "truncate sport_catalog cascade")) {
      assertThatThrownBy(() -> appJdbc.sql(sql).update())
          .as(sql)
          .rootCause()
          .hasMessageContaining("permission denied");
    }
  }

  @Test
  void sectionWritesAreAudited() {
    String slug = uniqueSlug("dat");
    JsonNode municipality = createMunicipality(slug);
    JsonNode facility = created(slug, "Auditada");
    put(slug, facility, "amenities", Map.of("amenities", List.of("PARKING")), 0);
    put(slug, facility, "amenities", Map.of("amenities", List.of("PARKING")), 0);
    createSport(slug, "Rugby cinco", "Rugby cinco");
    List<String> rows =
        jdbc.sql(
                """
                select action || ' ' || outcome from audit_event
                where municipality_id = :m order by occurred_at
                """)
            .param("m", id(municipality))
            .query(String.class)
            .list();
    assertThat(rows)
        .contains(
            "facility.amenities SUCCESS",
            "facility.amenities FAILURE",
            "facility.sport-create SUCCESS");
  }

  /** ADR-0018: os datos, a descrición, os servizos e os deportes (tamén os propios) viaxan. */
  @Test
  void exportCarriesTheDataAndOwnSports() {
    String slug = uniqueSlug("dat");
    createMunicipality(slug);
    JsonNode facility = created(slug, "Exportable");
    String custom =
        createSport(slug, "Bolos celtas", "Bolos celtas").json().get("customSportId").asString();
    put(
        slug,
        facility,
        "details",
        details(List.of(catalog("tennis"), Map.of("customSportId", custom))),
        0);
    put(slug, facility, "amenities", Map.of("amenities", List.of("LIGHTING")), 1);

    Api.Response export =
        api()
            .get("/api/v1/municipalities/" + slug + "/export")
            .token(TestTokens.manager(slug))
            .send();
    assertThat(export.status()).as(export.toString()).isEqualTo(200);
    JsonNode row = export.json().get("sections").get("facility").get(0);
    assertThat(row.get("amenities").toString()).isEqualTo("[\"LIGHTING\"]");
    assertThat(row.get("details").get("sports").get(1).get("customSportId").asString())
        .isEqualTo(custom);
    assertThat(row.get("details").get("sports").get(1).get("label").get("gl").asString())
        .isEqualTo("Bolos celtas");
  }

  /** ADR-0018: exportar, borrar o concello e importalo deixa os mesmos datos. */
  @Test
  void importRestoresTheDataAndOwnSports() {
    String slug = uniqueSlug("dat");
    JsonNode municipality = createMunicipality(slug);
    JsonNode facility = created(slug, "Ida e volta");
    String custom =
        createSport(slug, "Bolos celtas", "Bolos celtas").json().get("customSportId").asString();
    Map<String, Object> card = details(List.of(Map.of("customSportId", custom), catalog("tennis")));
    card.put("onlineBooking", false);
    card.put("showFreeSlots", true);
    put(slug, facility, "details", card, 0);
    put(slug, facility, "description", Map.of("gl", "Ola", "es", "Hola"), 1);
    put(slug, facility, "amenities", Map.of("amenities", List.of("CAFE", "HEARING_LOOP")), 2);
    JsonNode before = export(slug);

    for (String table :
        List.of("facility_sport", "facility_amenity", "facility", "custom_sport", "staff_member")) {
      jdbc.sql("delete from " + table + " where municipality_id = :m")
          .param("m", id(municipality))
          .update();
    }
    jdbc.sql("delete from municipality where id = :m").param("m", id(municipality)).update();
    Api.Response imported =
        api().post("/api/v1/municipality-imports", before).token(TestTokens.platformAdmin()).send();
    assertThat(imported.status()).as(imported.toString()).isEqualTo(201);

    JsonNode row = export(slug).get("sections").get("facility").get(0);
    JsonNode original = before.get("sections").get("facility").get(0);
    for (String field : List.of("id", "name", "details", "description", "amenities", "createdAt")) {
      assertThat(row.get(field)).as(field).isEqualTo(original.get(field));
    }
    assertThat(row.get("details").get("sports").get(0).get("customSportId").asString())
        .isEqualTo(custom);
  }

  JsonNode export(String slug) {
    Api.Response export =
        api()
            .get("/api/v1/municipalities/" + slug + "/export")
            .token(TestTokens.manager(slug))
            .send();
    assertThat(export.status()).as(export.toString()).isEqualTo(200);
    return export.json();
  }
}
