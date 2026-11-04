// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Busca de enderezos de punta a punta co buscador {@code fixed} (application-test: ningún test sae
 * á rede, ADR-0023): suxestións filtradas polo INE do concello, resolución, punto → enderezo, erros
 * e permisos (xestor e persoal; a veciñanza non).
 */
class AddressSearchIT extends AbstractIT {

  static String base(String slug) {
    return "/api/v1/municipalities/" + slug + "/address-search";
  }

  Api.Response suggest(String slug, String q, String token) {
    return api()
        .get(base(slug) + "?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8))
        .token(token)
        .send();
  }

  String ferrol() {
    String slug = uniqueSlug("end");
    createMunicipality(slug);
    jdbc.sql("update municipality set ine_code = '15036' where slug = :slug")
        .param("slug", slug)
        .update();
    return slug;
  }

  static List<String> labels(JsonNode list) {
    List<String> out = new ArrayList<>();
    list.forEach(n -> out.add(n.get("label").asString()));
    return out;
  }

  @Test
  void suggestionsAreThoseOfTheMunicipalityAndResolveToAFullAddress() {
    String slug = ferrol();

    Api.Response found = suggest(slug, "  Praza   ESPANA ", TestTokens.manager(slug));
    assertThat(found.status()).as(found.toString()).isEqualTo(200);
    assertThat(labels(found.json())).containsExactly("Praza de España 10, Ferrol");
    // Narón non é deste concello.
    assertThat(labels(suggest(slug, "castela", TestTokens.manager(slug)).json())).isEmpty();

    String id = found.json().get(0).get("id").asString();
    Api.Response resolved = api().get(base(slug) + "/" + id).token(TestTokens.staff(slug)).send();
    assertThat(resolved.status()).as(resolved.toString()).isEqualTo(200);
    JsonNode address = resolved.json();
    assertThat(address.get("street").asString()).isEqualTo("Praza de España");
    assertThat(address.get("streetNumber").asString()).isEqualTo("10");
    assertThat(address.get("postalCode").asString()).isEqualTo("15403");
    assertThat(address.get("locality").asString()).isEqualTo("Ferrol");
    assertThat(address.get("ineCode").asString()).isEqualTo("15036");
    assertThat(address.get("latitude").decimalValue()).isEqualByComparingTo("43.486943");
    assertThat(address.get("longitude").decimalValue()).isEqualByComparingTo("-8.228466");
  }

  @Test
  void shortQueriesGiveNothing() {
    String slug = ferrol();

    Api.Response response = suggest(slug, "ru", TestTokens.manager(slug));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().size()).isZero();
    assertThat(api().get(base(slug)).token(TestTokens.manager(slug)).send().json().size()).isZero();
  }

  @Test
  void withoutIneCodeTheMunicipalityNameFilters() {
    String slug = uniqueSlug("end");
    createMunicipality(slug); // «Concello end-…»: ningún enderezo gardado é del

    assertThat(suggest(slug, "rúa real", TestTokens.manager(slug)).json().size()).isZero();
  }

  @Test
  void aPointOnTheMapGivesTheNearestAddress() {
    String slug = ferrol();

    Api.Response near =
        api()
            .get(base(slug) + "/reverse?lat=43.4858&lon=-8.2295")
            .token(TestTokens.manager(slug))
            .send();
    assertThat(near.status()).as(near.toString()).isEqualTo(200);
    assertThat(near.json().get("street").asString()).isEqualTo("Rúa Real");

    Api.Response far =
        api()
            .get(base(slug) + "/reverse?lat=40.4168&lon=-3.7038")
            .token(TestTokens.manager(slug))
            .send();
    assertThat(far.status()).isEqualTo(404);
    assertThat(far.json().get("code").asString()).isEqualTo("facility.address-not-found");

    Api.Response invalid =
        api().get(base(slug) + "/reverse?lat=95&lon=0").token(TestTokens.manager(slug)).send();
    assertThat(invalid.json().get("code").asString()).isEqualTo("validation.failed");
  }

  @Test
  void anUnknownSuggestionIsNotFoundInGalicianAndSpanish() {
    String slug = ferrol();

    Api.Response gl =
        api().get(base(slug) + "/f.nope").token(TestTokens.manager(slug)).language("gl").send();
    Api.Response es =
        api().get(base(slug) + "/f.nope").token(TestTokens.manager(slug)).language("es").send();

    assertThat(gl.status()).isEqualTo(404);
    assertThat(gl.json().get("code").asString()).isEqualTo("facility.address-not-found");
    assertThat(gl.json().get("title").asString()).isEqualTo("Enderezo non atopado");
    assertThat(es.json().get("title").asString()).isEqualTo("Dirección no encontrada");
  }

  @Test
  void residentsCannotSearch() {
    String slug = ferrol();

    Api.Response response = suggest(slug, "rúa real", TestTokens.resident(slug));

    assertThat(response.status()).isEqualTo(403);
  }
}
