// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Textos propios das páxinas legais (ADR-0017 punto 8): lectura pública, escritura só do xestor do
 * concello co {@code If-Match} do concello; o HTML gárdase saneado no servidor (unha carga XSS non
 * chega á base nin á resposta); cada concello ve só os seus; cambiar de modo non borra o texto nin
 * o enderezo; a páxina propia de privacidade chega ao pé dos correos.
 */
class MunicipalityLegalTextsIT extends AbstractIT {

  @Autowired MunicipalityQueries queries;

  static String url(String slug) {
    return "/api/v1/municipalities/" + slug + "/legal-texts";
  }

  static final String XSS =
      "<h2 style=\"color:red\" class=\"x\">Titular</h2><script>alert(1)</script>"
          + "<p onclick=\"alert(1)\">Texto <strong>forte</strong><img src=x onerror=alert(1)></p>"
          + "<p><a href=\"javascript:alert(1)\">mal</a> <a href=\"https://sede.naron.gal\""
          + " target=\"_blank\">sede</a></p>";

  @Test
  void managerSavesSanitizedTextsAndTheyArePublic() {
    String slug = uniqueSlug("textos");
    createMunicipality(slug);
    Api.Response empty = api().get(url(slug)).send();
    assertThat(empty.status()).as(empty.toString()).isEqualTo(200);
    assertThat(empty.json().get("privacy").isNull()).isTrue();
    long version = empty.etagVersion();

    Map<String, Object> body =
        Map.of(
            "legalNotice", Map.of("gl", XSS, "es", "<p>Aviso</p>"),
            "privacy", Map.of("gl", "<p></p>", "es", " "));
    Api.Response saved =
        api().put(url(slug), body).ifMatch(version).token(TestTokens.manager(slug)).send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.etagVersion()).isEqualTo(version + 1);
    String clean =
        "<h2>Titular</h2><p>Texto <strong>forte</strong></p><p><a rel=\"noopener noreferrer\">mal"
            + "</a> <a href=\"https://sede.naron.gal\" rel=\"noopener noreferrer\">sede</a></p>";
    assertThat(saved.json().get("legalNotice").get("gl").asString()).isEqualTo(clean);
    assertThat(saved.json().get("privacy").isNull()).as("sen texto visible = sen texto").isTrue();

    JsonNode read = api().get(url(slug)).send().json();
    assertThat(read.get("legalNotice").get("gl").asString()).isEqualTo(clean);
    assertThat(read.get("legalNotice").get("es").asString()).isEqualTo("<p>Aviso</p>");
    String stored =
        jdbc.sql("select legal_notice_text ->> 'gl' from municipality where slug = :slug")
            .param("slug", slug)
            .query(String.class)
            .single();
    assertThat(stored).isEqualTo(clean).doesNotContain("script", "onerror", "onclick", "style");

    Api.Response stale =
        api().put(url(slug), body).ifMatch(version).token(TestTokens.manager(slug)).send();
    assertThat(stale.status()).isEqualTo(412);
  }

  @Test
  void onlyTheMunicipalityManagementWritesAndEachSeesItsOwn() {
    String a = uniqueSlug("textos-a");
    String b = uniqueSlug("textos-b");
    createMunicipality(a);
    createMunicipality(b);
    Map<String, Object> body = Map.of("privacy", Map.of("gl", "<p>Só de A</p>"));

    for (String token :
        new String[] {TestTokens.staff(a), TestTokens.resident(a), TestTokens.manager(b)}) {
      Api.Response denied = api().put(url(a), body).ifMatch(0).token(token).send();
      assertThat(denied.status()).as(denied.toString()).isEqualTo(403);
    }
    assertThat(api().put(url(a), body).ifMatch(0).send().status()).isEqualTo(401);

    Api.Response saved = api().put(url(a), body).ifMatch(0).token(TestTokens.manager(a)).send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    // A plataforma tamén edita os textos legais (ADR-0028, revisión do 2026-11-03).
    Api.Response byPlatform =
        api().put(url(a), body).ifMatch(1).token(TestTokens.platformAdmin()).send();
    assertThat(byPlatform.status()).as(byPlatform.toString()).isEqualTo(200);
    assertThat(api().get(url(b)).send().json().get("privacy").isNull()).isTrue();
    assertThat(api().get(url(a)).send().json().get("privacy").get("gl").asString())
        .isEqualTo("<p>Só de A</p>");
  }

  @Test
  void tooLongTextIsRejectedNextToTheField() {
    String slug = uniqueSlug("textos-longo");
    createMunicipality(slug);
    Map<String, Object> body =
        Map.of("accessibility", Map.of("es", "<p>" + "x".repeat(30_001) + "</p>"));

    Api.Response response =
        api().put(url(slug), body).ifMatch(0).token(TestTokens.manager(slug)).send();

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body()).contains("accessibility.es");
  }

  @Test
  void changingTheModeKeepsTheTextAndTheAddressAndMailsLinkTheOwnPage() {
    String slug = uniqueSlug("textos-modo");
    UUID municipality = id(createMunicipality(slug));
    Api.Response texts =
        api()
            .put(url(slug), Map.of("privacy", Map.of("gl", "<p>Propia</p>")))
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(texts.status()).as(texts.toString()).isEqualTo(200);

    Api.Response own = putLegal(slug, 1, "URL");
    assertThat(own.status()).as(own.toString()).isEqualTo(200);
    assertThat(queries.mailContext(municipality).orElseThrow().privacyUrl())
        .isEqualTo("https://naron.gal/privacidade");

    Api.Response furar = putLegal(slug, 2, "FURAR");
    assertThat(furar.status()).as(furar.toString()).isEqualTo(200);
    assertThat(furar.json().get("legal").get("privacyUrl").asString())
        .isEqualTo("https://naron.gal/privacidade");
    assertThat(queries.mailContext(municipality).orElseThrow().privacyUrl()).isNull();
    assertThat(api().get(url(slug)).send().json().get("privacy").get("gl").asString())
        .isEqualTo("<p>Propia</p>");
  }

  private Api.Response putLegal(String slug, long version, String privacyMode) {
    Map<String, Object> body = new HashMap<>(updateBody("Concello " + slug, Map.of()));
    body.put(
        "legal", Map.of("privacyMode", privacyMode, "privacyUrl", "https://naron.gal/privacidade"));
    return api()
        .put("/api/v1/municipalities/" + slug, body)
        .token(TestTokens.manager(slug))
        .ifMatch(version)
        .send();
  }
}
