// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * De punta a punta con PostgreSQL real (D1-11a): alta, listado alfabético por idioma, ficha, nome e
 * estado con {@code If-Match}, nome repetido sen maiúsculas, permisos e illamento entre concellos.
 */
class FacilityIT extends AbstractIT {

  static Map<String, Object> address() {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("street", "Rúa do Alto");
    address.put("streetNumber", "12");
    address.put("postalCode", "15570");
    address.put("locality", "Narón");
    address.put("ineCode", "15054");
    address.put("latitude", 43.5167);
    address.put("longitude", -8.1833);
    return address;
  }

  static String base(String slug) {
    return "/api/v1/municipalities/" + slug + "/facilities";
  }

  Api.Response create(String slug, String name, String token) {
    Api.Call call = api().post(base(slug), Map.of("name", name, "address", address()));
    return (token == null ? call : call.token(token)).send();
  }

  JsonNode created(String slug, String name) {
    Api.Response response = create(slug, name, TestTokens.manager(slug));
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return response.json();
  }

  List<String> names(Api.Response list) {
    List<String> names = new ArrayList<>();
    list.json().get("items").forEach(item -> names.add(item.get("name").asString()));
    return names;
  }

  @Test
  void createReturnsTheDraftWithLocationAndETag() {
    String slug = uniqueSlug("fac");
    createMunicipality(slug);

    Api.Response created = create(slug, "Campo de fútbol 1 de O Alto", TestTokens.manager(slug));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    assertThat(created.header("Location")).endsWith("/facilities/" + id(created.json()));
    assertThat(created.etagVersion()).isZero();
    JsonNode body = created.json();
    assertThat(body.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(body.get("missing").toString()).isEqualTo("[\"SPORTS\",\"USES\",\"SCHEDULE\"]");
    assertThat(body.get("address").get("streetNumber").asString()).isEqualTo("12");
    assertThat(body.get("address").get("latitude").decimalValue()).isEqualByComparingTo("43.5167");

    Api.Response one = api().get(base(slug) + "/" + id(body)).token(TestTokens.staff(slug)).send();
    assertThat(one.status()).isEqualTo(200);
    assertThat(one.etagVersion()).isZero();
    assertThat(one.json().get("name").asString()).isEqualTo("Campo de fútbol 1 de O Alto");
  }

  @Test
  void listIsAlphabeticalInTheLanguageOfTheReaderWithVersionAndMissing() {
    String slug = uniqueSlug("fac");
    createMunicipality(slug);
    for (String name : List.of("Pista de pádel 2", "zona exterior", "Ágora", "Ñandú", "Campo")) {
      created(slug, name);
    }
    for (String language : List.of("gl", "es")) {
      Api.Response list =
          api().get(base(slug)).token(TestTokens.staff(slug)).language(language).send();
      assertThat(list.status()).as(list.toString()).isEqualTo(200);
      assertThat(names(list))
          .as(language)
          .containsExactly("Ágora", "Campo", "Ñandú", "Pista de pádel 2", "zona exterior");
      JsonNode first = list.json().get("items").get(0);
      assertThat(first.get("version").asLong()).isZero();
      assertThat(first.get("missing").size()).isEqualTo(3);
      assertThat(first.get("address").get("locality").asString()).isEqualTo("Narón");
    }
    Api.Response page =
        api().get(base(slug) + "?page=1&size=2").token(TestTokens.manager(slug)).send();
    assertThat(names(page)).containsExactly("Ñandú", "Pista de pádel 2");
    assertThat(page.json().get("totalItems").asLong()).isEqualTo(5);
  }

  @Test
  void namesAreUniqueIgnoringCase() {
    String slug = uniqueSlug("fac");
    createMunicipality(slug);
    created(slug, "Pabellón");
    Api.Response again = create(slug, "PABELLÓN", TestTokens.manager(slug));
    assertThat(again.status()).as(again.toString()).isEqualTo(409);
    assertThat(again.json().get("code").asString()).isEqualTo("facility.name-taken");

    JsonNode other = created(slug, "Pavillón");
    Api.Response rename =
        api()
            .put(base(slug) + "/" + id(other) + "/name", Map.of("name", "pabellón"))
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(rename.status()).as(rename.toString()).isEqualTo(409);
    assertThat(rename.json().get("code").asString()).isEqualTo("facility.name-taken");

    String elsewhere = uniqueSlug("fac");
    createMunicipality(elsewhere);
    assertThat(create(elsewhere, "PABELLÓN", TestTokens.manager(elsewhere)).status())
        .isEqualTo(201);
  }

  @Test
  void aDraftNeedsOnlyTheNameButAnAddressGoesWhole() {
    String slug = uniqueSlug("fac");
    createMunicipality(slug);
    Api.Response none =
        api()
            .post(base(slug), Map.of("name", "Sen enderezo"))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(none.status()).as(none.toString()).isEqualTo(201);
    assertThat(none.json().get("address").isNull()).isTrue();
    assertThat(none.json().get("published").asBoolean()).isFalse();
    Api.Response early =
        api()
            .put(base(slug) + "/" + id(none.json()) + "/publication", Map.of())
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(early.status()).as(early.toString()).isEqualTo(409);
    assertThat(early.json().get("code").asString()).isEqualTo("facility.not-publishable");

    Map<String, Object> noPoint = address();
    noPoint.remove("latitude");
    noPoint.remove("longitude");
    Api.Response withoutPoint =
        api()
            .post(base(slug), Map.of("name", "Sen punto", "address", noPoint))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(withoutPoint.status()).as(withoutPoint.toString()).isEqualTo(201);
    assertThat(withoutPoint.json().get("address").get("latitude").isNull()).isTrue();

    Map<String, Object> partial = address();
    partial.remove("postalCode");
    partial.put("latitude", 120);
    Api.Response missing =
        api()
            .post(base(slug), Map.of("name", "Parcial", "address", partial))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(missing.status()).isEqualTo(422);
    assertThat(missing.json().get("code").asString()).isEqualTo("facility.address-required");
    assertThat(missing.json().get("errors").get(0).get("field").asString())
        .isEqualTo("address.postalCode");

    Map<String, Object> wrong = address();
    wrong.put("latitude", 120);
    Api.Response invalid =
        api()
            .post(base(slug), Map.of("name", "Errado", "address", wrong))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(invalid.status()).isEqualTo(422);
    assertThat(invalid.json().get("code").asString()).isEqualTo("facility.address-invalid");
  }

  @Test
  void renameNeedsIfMatchWithTheCurrentVersion() {
    String slug = uniqueSlug("fac");
    createMunicipality(slug);
    String url = base(slug) + "/" + id(created(slug, "Vello")) + "/name";

    Api.Response renamed =
        api().put(url, Map.of("name", "Novo")).ifMatch(0).token(TestTokens.manager(slug)).send();
    assertThat(renamed.status()).as(renamed.toString()).isEqualTo(200);
    assertThat(renamed.etagVersion()).isEqualTo(1);
    assertThat(renamed.json().get("name").asString()).isEqualTo("Novo");

    Api.Response stale =
        api().put(url, Map.of("name", "Outro")).ifMatch(0).token(TestTokens.manager(slug)).send();
    assertThat(stale.status()).isEqualTo(412);
    assertThat(stale.json().get("code").asString()).isEqualTo("common.stale-version");

    Api.Response without =
        api().put(url, Map.of("name", "Outro")).token(TestTokens.manager(slug)).send();
    assertThat(without.status()).isEqualTo(428);
    assertThat(without.json().get("code").asString()).isEqualTo("common.precondition-required");
  }

  @Test
  void statusNeedsIfMatchAndChangesTheVersion() {
    String slug = uniqueSlug("fac");
    createMunicipality(slug);
    String url = base(slug) + "/" + id(created(slug, "Pista de pádel 1")) + "/status";

    Api.Response closed =
        api()
            .put(url, Map.of("status", "TEMPORARILY_CLOSED"))
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(closed.status()).as(closed.toString()).isEqualTo(200);
    assertThat(closed.json().get("status").asString()).isEqualTo("TEMPORARILY_CLOSED");
    assertThat(closed.etagVersion()).isEqualTo(1);

    Api.Response stale =
        api()
            .put(url, Map.of("status", "WITHDRAWN"))
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(stale.status()).isEqualTo(412);
    Api.Response without =
        api().put(url, Map.of("status", "WITHDRAWN")).token(TestTokens.manager(slug)).send();
    assertThat(without.status()).isEqualTo(428);
    Api.Response unknown =
        api().put(url, Map.of("status", "DRAFT")).ifMatch(1).token(TestTokens.manager(slug)).send();
    assertThat(unknown.status()).isEqualTo(400);
  }

  @Test
  void writesAreAudited() {
    String slug = uniqueSlug("fac");
    JsonNode municipality = createMunicipality(slug);
    created(slug, "Auditada");
    create(slug, "AUDITADA", TestTokens.manager(slug));
    List<Map<String, Object>> rows =
        jdbc.sql(
                "select action, outcome from audit_event where municipality_id = :m order by occurred_at")
            .param("m", id(municipality))
            .query()
            .listOfRows();
    assertThat(rows)
        .extracting(r -> r.get("action") + " " + r.get("outcome"))
        .contains("facility.create SUCCESS", "facility.create FAILURE");
  }

  @Test
  void onlyManagersWriteAndStaffReads() {
    String a = uniqueSlug("fac-a");
    String b = uniqueSlug("fac-b");
    createMunicipality(a);
    createMunicipality(b);

    assertThat(create(a, "Sen token", null).status()).isEqualTo(401);
    Api.Response staff = create(a, "Persoal", TestTokens.staff(a));
    assertThat(staff.status()).isEqualTo(403);
    assertThat(staff.json().get("code").asString()).isEqualTo("auth.forbidden");
    assertThat(create(a, "Xestor de B", TestTokens.manager(b)).status()).isEqualTo(403);
    assertThat(api().get(base(a)).send().status()).isEqualTo(401);
    assertThat(api().get(base(a)).token(TestTokens.resident(a)).send().status()).isEqualTo(403);
  }

  @Test
  void anotherMunicipalityNeitherSeesNorChanges() {
    String a = uniqueSlug("fac-a");
    String b = uniqueSlug("fac-b");
    createMunicipality(a);
    createMunicipality(b);
    JsonNode onlyInA = created(a, "Só en A");

    Api.Response listB = api().get(base(b)).token(TestTokens.manager(b)).send();
    assertThat(listB.json().get("totalItems").asLong()).isZero();

    Api.Response readFromB =
        api().get(base(b) + "/" + id(onlyInA)).token(TestTokens.manager(b)).send();
    assertThat(readFromB.status()).isEqualTo(404);
    assertThat(readFromB.json().get("code").asString()).isEqualTo("facility.not-found");

    Api.Response statusFromB =
        api()
            .put(base(b) + "/" + id(onlyInA) + "/status", Map.of("status", "WITHDRAWN"))
            .ifMatch(0)
            .token(TestTokens.manager(b))
            .send();
    assertThat(statusFromB.status()).isEqualTo(404);
    Api.Response renameFromB =
        api()
            .put(base(b) + "/" + id(onlyInA) + "/name", Map.of("name", "De B"))
            .ifMatch(0)
            .token(TestTokens.manager(b))
            .send();
    assertThat(renameFromB.status()).isEqualTo(404);
    assertThat(
            jdbc.sql("select name || status from facility where id = :id")
                .param("id", id(onlyInA))
                .query(String.class)
                .single())
        .isEqualTo("Só en AACTIVE");
  }

  /** ADR-0018: a sección do módulo vai no ficheiro furar-export do seu concello e só no seu. */
  @Test
  void exportCarriesTheModuleSection() {
    String a = uniqueSlug("fac-a");
    String b = uniqueSlug("fac-b");
    createMunicipality(a);
    createMunicipality(b);
    JsonNode exportable = created(a, "Exportable");

    Api.Response exportA =
        api().get("/api/v1/municipalities/" + a + "/export").token(TestTokens.manager(a)).send();
    assertThat(exportA.status()).as(exportA.toString()).isEqualTo(200);
    var rows = exportA.json().get("sections").get("facility");
    assertThat(rows.size()).isEqualTo(1);
    assertThat(rows.get(0).get("id").asString()).isEqualTo(id(exportable).toString());

    Api.Response exportB =
        api().get("/api/v1/municipalities/" + b + "/export").token(TestTokens.manager(b)).send();
    assertThat(exportB.json().get("sections").get("facility").size()).isZero();
  }
}
