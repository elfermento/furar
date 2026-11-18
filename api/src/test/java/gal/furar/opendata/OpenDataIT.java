// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Datos abertos dun concello de punta a punta (ADR-0034): sen token, os tres conxuntos e a ficha;
 * só o público; a ocupación só das instalacións cos ocos á vista; CORS só nos ficheiros; ETag e
 * 304; o interruptor; o tope de descargas.
 */
class OpenDataIT extends AbstractOpenDataIT {

  String slug;
  UUID municipality;
  Published online;
  Published counter;

  void setUp() {
    slug = uniqueSlug("od");
    municipality = municipalityReady(slug);
    online = publish(slug, municipality, "Pista de pádel", true, false);
    // Só no mostrador e sen ocos á vista: nas instalacións si, na ocupación non.
    counter = publish(slug, municipality, "Frontón", false, false);
    createField(slug, facilityField("pavimento", "TEXT", "PUBLIC"));
    createField(slug, facilityField("chave", "TEXT", "STAFF"));
    fieldValues(online, Map.of("pavimento", "Herba artificial", "chave", "Armario 3"));
  }

  Api.Response get(String path) {
    return api().get(openData(slug) + path).send();
  }

  @Test
  void theThreeDatasetsWithoutTokenOnlyPublicDataAndCorsOnlyOnFiles() {
    setUp();
    Api.Response summary = get("");
    assertThat(summary.status()).as(summary.toString()).isEqualTo(200);
    assertThat(summary.header("Access-Control-Allow-Origin")).isNull();
    JsonNode body = summary.json();
    assertThat(body.get("datasets")).hasSize(3);
    assertThat(body.get("occupancy").get("maxMonths").asInt()).isEqualTo(12);
    assertThat(body.get("catalog").asBoolean()).isFalse();
    assertThat(body.get("customFields").get(0).get("code").asString()).isEqualTo("pavimento");

    Api.Response csv = get("/facilities.csv");
    assertThat(csv.status()).as(csv.toString()).isEqualTo(200);
    assertThat(csv.header("Access-Control-Allow-Origin")).isEqualTo("*");
    assertThat(csv.header("Content-Type")).startsWith("text/csv");
    assertThat(csv.header("Content-Disposition")).contains(slug + "-instalacions.csv");
    assertThat(csv.header("Cache-Control")).isEqualTo("public, max-age=300");
    String text = csv.body();
    assertThat(text)
        .contains("campo_pavimento")
        .contains("Herba artificial")
        .contains("Pista de pádel")
        .contains("Frontón")
        .doesNotContain("Armario 3")
        .doesNotContain("chave")
        .doesNotContain("15-054-0012")
        .doesNotContain("ana@example.org");

    Api.Response json = get("/facilities.json?lang=es");
    assertThat(json.status()).isEqualTo(200);
    assertThat(json.json().get("conxunto").asString()).isEqualTo("instalacions");
    assertThat(json.json().get("atribucion").asString()).isEqualTo("Concello de Proba");
    Api.Response geo = get("/facilities.geojson");
    assertThat(geo.header("Content-Type")).startsWith("application/geo+json");
    assertThat(geo.json().get("features")).hasSize(2);

    Api.Response closures = get("/closures.json");
    assertThat(closures.status()).isEqualTo(200);
    assertThat(closures.json().get("conxunto").asString()).isEqualTo("peches");

    assertThat(get("/facilities.csv?lang=en").status()).isEqualTo(400);
    assertThat(get("/occupancy.geojson").status()).isEqualTo(404);
    // Sen DIR3, sen ficha; o 404 tamén leva CORS.
    Api.Response dcat = get("/dcat.rdf");
    assertThat(dcat.status()).isEqualTo(404);
    assertThat(dcat.header("Access-Control-Allow-Origin")).isEqualTo("*");
  }

  @Test
  void theEtagGivesA304() {
    setUp();
    Api.Response first = get("/facilities.csv");
    String etag = first.header("ETag");
    assertThat(etag).startsWith("\"");
    Api.Response again =
        api().get(openData(slug) + "/facilities.csv").header("If-None-Match", etag).send();
    assertThat(again.status()).isEqualTo(304);
    Api.Response gzip =
        api()
            .get(openData(slug) + "/facilities.csv")
            .header("If-None-Match", "W/\"" + etag.replace("\"", "") + "-gzip\"")
            .send();
    assertThat(gzip.status()).isEqualTo(304);
  }

  @Test
  void occupancyOnlyFromFacilitiesThatShowTheirSlotsAndOnlyValidDates() {
    setUp();
    LocalDate yesterday = today().minusDays(1);
    occupy(online, yesterday, 600, 60);
    occupy(counter, yesterday, 600, 60);
    assertThat(refresh.refresh()).isZero();
    // Idempotente.
    assertThat(refresh.refresh()).isZero();

    Api.Response file = get("/occupancy.csv?desde=" + yesterday + "&ata=" + yesterday);
    assertThat(file.status()).as(file.toString()).isEqualTo(200);
    assertThat(file.header("Content-Disposition"))
        .contains(slug + "-ocupacion-" + yesterday + "-" + yesterday + ".csv");
    String csv = file.body();
    assertThat(csv).contains(online.id().toString()).doesNotContain(counter.id().toString());
    // 13 horas abertas: 52 tramos; os catro das 10:00 ao 100 co uso.
    long rows = csv.lines().count() - 1;
    assertThat(rows).isEqualTo(52);
    assertThat(csv).contains(";Pista enteira;enteira;" + yesterday + ";").contains(";100\r");
    assertThat(
            jdbc.sql("select count(*) from open_data_occupancy where facility_id = :f")
                .param("f", counter.id())
                .query(Long.class)
                .single())
        .isZero();

    Api.Response json = get("/occupancy.json?desde=" + yesterday + "&ata=" + yesterday);
    assertThat(json.json().get("datos")).hasSize(52);
    assertThat(json.json().get("desde").asString()).isEqualTo(yesterday.toString());

    String etag = file.header("ETag");
    assertThat(
            api()
                .get(openData(slug) + "/occupancy.csv?desde=" + yesterday + "&ata=" + yesterday)
                .header("If-None-Match", etag)
                .send()
                .status())
        .isEqualTo(304);

    assertThat(get("/occupancy.csv?desde=" + yesterday).status()).isEqualTo(400);
    assertThat(get("/occupancy.csv?desde=" + today() + "&ata=" + today()).status()).isEqualTo(400);
    assertThat(
            get("/occupancy.csv?desde=" + today().minusMonths(13) + "&ata=" + yesterday).status())
        .isEqualTo(400);
    // Sen datas, o último mes enteiro.
    Api.Response last = get("/occupancy.csv");
    assertThat(last.status()).isEqualTo(200);
    LocalDate first = today().withDayOfMonth(1).minusMonths(1);
    assertThat(last.header("Content-Disposition"))
        .contains(first + "-" + first.withDayOfMonth(first.lengthOfMonth()));

    JsonNode summary = get("").json();
    assertThat(summary.get("datasets").get(2).get("updatedOn").asString())
        .isEqualTo(today().toString());
  }

  @Test
  void switchingOffGives404EverywhereAndTheNightDeletesTheRows() {
    setUp();
    occupy(online, today().minusDays(1), 600, 60);
    refresh.refresh();
    long version =
        api()
            .get("/api/v1/municipalities/" + slug)
            .token(TestTokens.manager(slug))
            .send()
            .etagVersion();
    Api.Response staff =
        api()
            .put(openData(slug) + "-publication", Map.of("enabled", false))
            .ifMatch(version)
            .token(TestTokens.staff(slug))
            .send();
    assertThat(staff.status()).isEqualTo(403);
    Api.Response off =
        api()
            .put(openData(slug) + "-publication", Map.of("enabled", false))
            .ifMatch(version)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(off.status()).as(off.toString()).isEqualTo(200);
    assertThat(off.json().get("enabled").asBoolean()).isFalse();
    assertThat(off.header("Access-Control-Allow-Origin")).isNull();
    assertThat(
            api().get("/api/v1/municipalities/" + slug).send().json().get("openData").asBoolean())
        .isFalse();
    for (String path : new String[] {"", "/facilities.csv", "/closures.json", "/occupancy.csv"}) {
      Api.Response r = get(path);
      assertThat(r.status()).as(path).isEqualTo(404);
      assertThat(r.json().get("code").asString()).isEqualTo("opendata.not-published");
    }
    assertThat(get("/facilities.csv").header("Access-Control-Allow-Origin")).isEqualTo("*");
    refresh.refresh();
    assertThat(
            jdbc.sql("select count(*) from open_data_occupancy where municipality_id = :m")
                .param("m", municipality)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql(
                    "select count(*) from audit_event where municipality_id = :m"
                        + " and action = 'municipality.open-data'")
                .param("m", municipality)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void withDir3TheCatalogRecordExists() {
    setUp();
    jdbc.sql("update municipality set dir3_code = 'L01150548' where id = :id")
        .param("id", municipality)
        .update();
    Api.Response dcat = get("/dcat.rdf");
    assertThat(dcat.status()).as(dcat.toString()).isEqualTo(200);
    assertThat(dcat.header("Content-Type")).startsWith("application/rdf+xml");
    assertThat(dcat.body()).contains("Organismo/L01150548").contains("dcat:Catalog");
    assertThat(get("").json().get("catalog").asBoolean()).isTrue();
  }
}
