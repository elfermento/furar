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
 * AP4-01 (ADR-0017 punto 8): cada concello garda o seu DPD, os datos da declaración de
 * accesibilidade e do aviso legal e o modo de cada páxina legal; saen no detalle público e no
 * {@code furar-export}; unha edición sen {@code legal} non os borra; un formato mal dá 400 por
 * campo; a páxina propia pide enderezo https; só o xestor do concello os cambia.
 */
class MunicipalityLegalDetailsIT extends AbstractIT {

  @Autowired MunicipalityQueries mailContexts;

  static final Map<String, Object> LEGAL =
      Map.ofEntries(
          Map.entry("dir3Code", "L01150548"),
          Map.entry("nif", "P1505400J"),
          Map.entry("address", "Praza de Galicia, 1. 15570 Narón"),
          Map.entry("generalEmail", "concello@naron.gal"),
          Map.entry("dpoEmail", "dpd@naron.gal"),
          Map.entry("dpoPhone", "981 000 000"),
          Map.entry("processor", "Aloxamentos Exemplo SL"),
          Map.entry("accessibilityEmail", "accesibilidade@naron.gal"),
          Map.entry("accessibilityComplaintUrl", "https://sede.naron.gal/reclamacions"),
          Map.entry("accessibilityUnit", "Departamento de Novas Tecnoloxías"),
          Map.entry("privacyMode", "URL"),
          Map.entry("privacyUrl", "https://naron.gal/privacidade"),
          Map.entry("accessibilityMode", "TEXT"),
          Map.entry("legalNoticeUrl", "https://naron.gal/aviso-legal"));

  @Test
  void managerSetsLegalDetailsAndTheyArePublicAndExported() {
    String slug = uniqueSlug("legal");
    createMunicipality(slug);

    Api.Response saved = put(slug, 0, LEGAL);
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);

    JsonNode legal = api().get("/api/v1/municipalities/" + slug).send().json().get("legal");
    assertThat(legal.get("dpoEmail").asString()).isEqualTo("dpd@naron.gal");
    assertThat(legal.get("dpoPhone").asString()).isEqualTo("981 000 000");
    assertThat(legal.get("processor").asString()).isEqualTo("Aloxamentos Exemplo SL");
    assertThat(legal.get("nif").asString()).isEqualTo("P1505400J");
    assertThat(legal.get("generalEmail").asString()).isEqualTo("concello@naron.gal");
    assertThat(legal.get("privacyMode").asString()).isEqualTo("URL");
    assertThat(legal.get("accessibilityMode").asString()).isEqualTo("TEXT");
    assertThat(legal.get("legalNoticeMode").asString())
        .as("sen modo, a páxina de Furar; o enderezo queda gardado")
        .isEqualTo("FURAR");
    assertThat(legal.get("legalNoticeUrl").asString()).isEqualTo("https://naron.gal/aviso-legal");
    assertThat(legal.has("dpoContact")).isFalse();
    assertThat(legal.get("accessibilityComplaintUrl").asString())
        .isEqualTo("https://sede.naron.gal/reclamacions");
    assertThat(legal.get("ineCode").isNull()).isTrue();

    Api.Response withoutLegal = put(slug, 1, null);
    assertThat(withoutLegal.status()).isEqualTo(200);
    assertThat(withoutLegal.json().get("legal").get("dir3Code").asString()).isEqualTo("L01150548");

    JsonNode exported =
        api()
            .get("/api/v1/municipalities/" + slug + "/export")
            .token(TestTokens.manager(slug))
            .send()
            .json();
    assertThat(exported.get("municipality").get("legal").get("accessibilityUnit").asString())
        .isEqualTo("Departamento de Novas Tecnoloxías");
  }

  @Test
  void invalidFormatIsRejectedNextToTheField() {
    String slug = uniqueSlug("legal");
    createMunicipality(slug);
    Map<String, Object> bad = new HashMap<>(LEGAL);
    bad.put("accessibilityComplaintUrl", "http://sen-tls.gal");
    bad.put("dir3Code", "naron");

    Api.Response response = put(slug, 0, bad);

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().get("code").asString()).isEqualTo("validation.failed");
    assertThat(response.body()).contains("legal.accessibilityComplaintUrl", "legal.dir3Code");
  }

  @Test
  void theOwnPageModeNeedsAnHttpsAddress() {
    String slug = uniqueSlug("legal");
    createMunicipality(slug);
    Map<String, Object> withoutUrl = new HashMap<>(LEGAL);
    withoutUrl.remove("privacyUrl");
    withoutUrl.put("legalNoticeMode", "URL");
    withoutUrl.put("legalNoticeUrl", "http://naron.gal/aviso-legal");
    withoutUrl.put("accessibilityMode", "PDF");

    // Primeiro o formato (DTO): enderezo sen https e modo descoñecido.
    Api.Response badFormat = put(slug, 0, withoutUrl);
    assertThat(badFormat.status()).isEqualTo(400);
    assertThat(badFormat.body()).contains("legal.legalNoticeUrl", "legal.accessibilityMode");

    // Co formato ben, o dominio pide o enderezo da páxina propia.
    withoutUrl.put("legalNoticeUrl", "https://naron.gal/aviso-legal");
    withoutUrl.put("accessibilityMode", "TEXT");
    Api.Response missing = put(slug, 0, withoutUrl);
    assertThat(missing.status()).isEqualTo(400);
    assertThat(missing.body()).contains("legal.privacyUrl", "validation.required");
  }

  @Test
  void onlyTheMunicipalityManagementChangesItsLegalDetails() {
    String slug = uniqueSlug("legal");
    String other = uniqueSlug("legal-b");
    createMunicipality(slug);
    createMunicipality(other);
    Map<String, Object> body = new HashMap<>(updateBody("Concello " + slug, Map.of()));
    body.put("legal", LEGAL);
    for (String token : new String[] {TestTokens.staff(slug), TestTokens.manager(other)}) {
      Api.Response response =
          api().put("/api/v1/municipalities/" + slug, body).token(token).ifMatch(0).send();
      assertThat(response.status()).as(response.toString()).isEqualTo(403);
    }
    // A plataforma deixa o concello configurado (ADR-0028, revisión do 2026-11-03).
    Api.Response byPlatform =
        api()
            .put("/api/v1/municipalities/" + slug, body)
            .token(TestTokens.platformAdmin())
            .ifMatch(0)
            .send();
    assertThat(byPlatform.status()).as(byPlatform.toString()).isEqualTo(200);
    assertThat(byPlatform.json().get("legal").get("processor").asString())
        .isEqualTo("Aloxamentos Exemplo SL");
  }

  private Api.Response put(String slug, long version, Map<String, Object> legal) {
    Map<String, Object> body = new HashMap<>(updateBody("Concello " + slug, Map.of()));
    if (legal != null) {
      body.put("legal", legal);
    }
    return api()
        .put("/api/v1/municipalities/" + slug, body)
        .token(TestTokens.manager(slug))
        .ifMatch(version)
        .send();
  }

  @Test
  void withoutPageIsSavedAndTheMailHasNoPrivacyLink() {
    String slug = uniqueSlug("lnone");
    UUID id = id(createMunicipality(slug));
    Map<String, Object> none = new HashMap<>(LEGAL);
    none.put("privacyMode", "NONE");
    none.put("accessibilityMode", "NONE");
    Api.Response saved = put(slug, 0, none);
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    JsonNode legal = saved.json().get("legal");
    assertThat(legal.get("privacyMode").asString()).isEqualTo("NONE");
    assertThat(legal.get("privacyUrl").asString())
        .as("o enderezo queda gardado")
        .isEqualTo("https://naron.gal/privacidade");
    assertThat(mailContexts.mailContext(id).orElseThrow().privacyPublished()).isFalse();
  }
}
