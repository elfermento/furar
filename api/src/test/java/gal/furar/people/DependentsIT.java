// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * As persoas a cargo da conta, de punta a punta con PostgreSQL real (ADR-0031, puntos 1, 6 e 9):
 * alta, edición con {@code If-Match}, duplicada, límite, outra conta → 404, sen correo verificado e
 * eliminar unha ficha que nunca se usou (bórrase). Eliminar unha con reservas (desactívase) vai en
 * {@code ReserveParticipantsIT}, que é quen as fai.
 */
class DependentsIT extends AbstractIT {

  static final String URL = "/api/v1/me/dependents";

  static String person() {
    return TestTokens.person("p-" + UUID.randomUUID(), true);
  }

  static Map<String, Object> body(String name, Integer birthYear, Boolean inMyCare) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("birthYear", birthYear);
    if (inMyCare != null) {
      body.put("inMyCare", inMyCare);
    }
    return body;
  }

  Api.Response create(String token, String name, int birthYear) {
    return api().post(URL, body(name, birthYear, true)).token(token).send();
  }

  @Test
  void savesListsAndEditsThePersonsOwnRecords() {
    String ana = person();

    Api.Response created = create(ana, "Uxía Rey Souto", 2016);
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID id = id(created.json());
    assertThat(created.header("Location")).isEqualTo(URL + "/" + id);
    assertThat(created.etagVersion()).isZero();
    assertThat(created.json().get("birthYear").asInt()).isEqualTo(2016);
    create(ana, "Antón Rey Souto", 2019);

    JsonNode list = api().get(URL).token(ana).send().json();
    assertThat(list.size()).isEqualTo(2);
    assertThat(list.get(0).get("name").asString()).as("por nome").isEqualTo("Antón Rey Souto");

    Api.Response edited =
        api().put(URL + "/" + id, body("Uxía Rey", 2015, true)).ifMatch(0).token(ana).send();
    assertThat(edited.status()).as(edited.toString()).isEqualTo(200);
    assertThat(edited.etagVersion()).isEqualTo(1);
    assertThat(edited.json().get("name").asString()).isEqualTo("Uxía Rey");
    assertThat(edited.json().get("birthYear").asInt()).isEqualTo(2015);

    Api.Response stale =
        api().put(URL + "/" + id, body("Outra", 2015, true)).ifMatch(0).token(ana).send();
    assertThat(stale.status()).isEqualTo(412);
    assertThat(stale.json().get("code").asString()).isEqualTo("common.stale-version");
  }

  @Test
  void theDeclarationAndAValidYearAreMandatory() {
    String ana = person();
    int thisYear = Year.now().getValue();

    Api.Response undeclared = api().post(URL, body("Uxía Rey", 2016, null)).token(ana).send();
    assertThat(undeclared.status()).isEqualTo(422);
    assertThat(undeclared.json().get("code").asString())
        .isEqualTo("people.dependent-declaration-required");
    Api.Response unchecked = api().post(URL, body("Uxía Rey", 2016, false)).token(ana).send();
    assertThat(unchecked.json().get("code").asString())
        .isEqualTo("people.dependent-declaration-required");

    for (int year : new int[] {thisYear + 1, thisYear - 121}) {
      Api.Response wrong = create(ana, "Uxía Rey", year);
      assertThat(wrong.status()).as("ano %s", year).isEqualTo(422);
      assertThat(wrong.json().get("code").asString())
          .isEqualTo("people.dependent-birth-year-invalid");
    }
    Api.Response blank = api().post(URL, body(" ", 2016, true)).token(ana).send();
    assertThat(blank.status()).isEqualTo(400);
    assertThat(blank.json().get("code").asString()).isEqualTo("validation.failed");
    assertThat(api().get(URL).token(ana).send().json().size()).isZero();
  }

  @Test
  void noTwoActiveRecordsWithTheSameNameAndYearAndAtMostTen() {
    String ana = person();
    UUID first = id(create(ana, "Uxía Rey", 2016).json());

    Api.Response same = create(ana, " uxía  REY ", 2016);
    assertThat(same.status()).as(same.toString()).isEqualTo(409);
    assertThat(same.json().get("code").asString()).isEqualTo("people.dependent-duplicate");
    assertThat(create(ana, "Uxía Rey", 2017).status()).as("outro ano, outra persoa").isEqualTo(201);
    UUID other = id(create(ana, "Antón Rey", 2019).json());
    Api.Response renamed =
        api().put(URL + "/" + other, body("Uxía Rey", 2016, true)).ifMatch(0).token(ana).send();
    assertThat(renamed.status()).isEqualTo(409);

    for (int i = 0; i < 7; i++) {
      assertThat(create(ana, "Persoa " + i, 2010).status()).isEqualTo(201);
    }
    Api.Response eleventh = create(ana, "Unha máis", 2010);
    assertThat(eleventh.status()).as(eleventh.toString()).isEqualTo(422);
    assertThat(eleventh.json().get("code").asString()).isEqualTo("people.dependent-limit");
    assertThat(eleventh.json().get("params").get("max").asInt()).isEqualTo(10);

    // Eliminar unha deixa sitio a outra, tamén co mesmo nome e ano: é unha ficha nova.
    assertThat(api().delete(URL + "/" + first).token(ana).send().status()).isEqualTo(204);
    Api.Response again = create(ana, "Uxía Rey", 2016);
    assertThat(again.status()).isEqualTo(201);
    assertThat(id(again.json())).isNotEqualTo(first);
  }

  @Test
  void anotherAccountNeitherSeesNorChangesThem() {
    String ana = person();
    String brais = person();
    UUID id = id(create(ana, "Uxía Rey", 2016).json());

    assertThat(api().get(URL).token(brais).send().json().size()).isZero();
    Api.Response edit =
        api().put(URL + "/" + id, body("Roubada", 2016, true)).ifMatch(0).token(brais).send();
    assertThat(edit.status()).isEqualTo(404);
    assertThat(edit.json().get("code").asString()).isEqualTo("people.dependent-not-found");
    assertThat(api().delete(URL + "/" + id).token(brais).send().status()).isEqualTo(404);
    assertThat(api().get(URL).token(ana).send().json().get(0).get("name").asString())
        .isEqualTo("Uxía Rey");
  }

  @Test
  void needsASessionWithAVerifiedEmail() {
    assertThat(api().get(URL).send().status()).isEqualTo(401);
    Api.Response unverified =
        api().get(URL).token(TestTokens.person("p-" + UUID.randomUUID(), false)).send();
    assertThat(unverified.status()).isEqualTo(403);
    assertThat(unverified.json().get("code").asString()).isEqualTo("auth.email-not-verified");
    Api.Response create =
        api()
            .post(URL, body("Uxía Rey", 2016, true))
            .token(TestTokens.person("p-" + UUID.randomUUID(), false))
            .send();
    assertThat(create.status()).isEqualTo(403);
  }

  @Test
  void deletingARecordNeverUsedRemovesTheRow() {
    String ana = person();
    UUID id = id(create(ana, "Uxía Rey", 2016).json());

    assertThat(api().delete(URL + "/" + id).token(ana).send().status()).isEqualTo(204);

    assertThat(api().get(URL).token(ana).send().json().size()).isZero();
    assertThat(
            jdbc.sql("select count(*) from account_dependent where id = :id")
                .param("id", id)
                .query(Integer.class)
                .single())
        .as("sen reservas, a fila bórrase do todo")
        .isZero();
    assertThat(api().delete(URL + "/" + id).token(ana).send().status()).isEqualTo(404);
  }
}
