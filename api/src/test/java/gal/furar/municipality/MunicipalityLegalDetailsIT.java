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
 * Cada concello garda o seu DPD, os datos da declaración de accesibilidade e do aviso legal e o
 * modo de cada páxina legal; saen no detalle público e no {@code furar-export}; unha edición sen
 * {@code legal} non os borra; un formato mal dá 400 por campo; a páxina propia pide enderezo https;
 * só o xestor do concello os cambia.
 */
class MunicipalityLegalDetailsIT extends AbstractIT {

  @Autowired MunicipalityQueries mailContexts;

  static final Map<String, Object> LEGAL =
      Map.ofEntries(
          Map.entry("dir3Code", "L01150548"),
          Map.entry("nif", "P1505400J"),
          Map.entry("address", "Praza de Galicia, 1. 15570 Narón"),
          Map.entry("generalEmail", "concello@naron.example.org"),
          Map.entry("dpoEmail", "dpd@naron.example.org"),
          Map.entry("dpoPhone", "981 000 000"),
          Map.entry("processor", "Aloxamentos Exemplo SL"),
          Map.entry("accessibilityEmail", "accesibilidade@naron.example.org"),
          Map.entry("accessibilityComplaintUrl", "https://sede.naron.example.org/reclamacions"),
          Map.entry("accessibilityUnit", "Departamento de Novas Tecnoloxías"),
          Map.entry("generalPhone", "981 111 111"),
          Map.entry("processingRegisterUrl", "https://sede.naron.example.org/rat"),
          Map.entry("useRegulationUrl", "https://naron.example.org/normativa-instalacions"),
          Map.entry("privacyMode", "URL"),
          Map.entry("privacyUrl", "https://naron.example.org/privacidade"),
          Map.entry("accessibilityMode", "TEXT"),
          Map.entry("legalNoticeUrl", "https://naron.example.org/aviso-legal"));

  @Test
  void managerSetsLegalDetailsAndTheyArePublicAndExported() {
    String slug = uniqueSlug("legal");
    createMunicipality(slug);

    Api.Response saved = put(slug, 0, LEGAL);
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);

    JsonNode legal = api().get("/api/v1/municipalities/" + slug).send().json().get("legal");
    assertThat(legal.get("dpoEmail").asString()).isEqualTo("dpd@naron.example.org");
    assertThat(legal.get("dpoPhone").asString()).isEqualTo("981 000 000");
    assertThat(legal.get("processor").asString()).isEqualTo("Aloxamentos Exemplo SL");
    assertThat(legal.get("nif").asString()).isEqualTo("P1505400J");
    assertThat(legal.get("generalEmail").asString()).isEqualTo("concello@naron.example.org");
    assertThat(legal.get("privacyMode").asString()).isEqualTo("URL");
    assertThat(legal.get("accessibilityMode").asString()).isEqualTo("TEXT");
    assertThat(legal.get("legalNoticeMode").asString())
        .as("sen modo, a páxina de Furar; o enderezo queda gardado")
        .isEqualTo("FURAR");
    assertThat(legal.get("legalNoticeUrl").asString())
        .isEqualTo("https://naron.example.org/aviso-legal");
    assertThat(legal.has("dpoContact")).isFalse();
    assertThat(legal.get("accessibilityComplaintUrl").asString())
        .isEqualTo("https://sede.naron.example.org/reclamacions");
    assertThat(legal.get("generalPhone").asString()).isEqualTo("981 111 111");
    assertThat(legal.get("processingRegisterUrl").asString())
        .isEqualTo("https://sede.naron.example.org/rat");
    assertThat(legal.get("useRegulationUrl").asString())
        .isEqualTo("https://naron.example.org/normativa-instalacions");

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
  void theNifIsTheEntityOneWithItsCheckDigit() {
    String slug = uniqueSlug("legal");
    createMunicipality(slug);
    Map<String, Object> legal = new HashMap<>(LEGAL);

    legal.put("nif", "34871265E");
    Api.Response personal = put(slug, 0, legal);
    assertThat(personal.status()).isEqualTo(400);
    assertThat(fieldCode(personal, "legal.nif")).isEqualTo("municipality.nif-personal");

    legal.put("nif", "Q1573421D");
    Api.Response checkDigit = put(slug, 0, legal);
    assertThat(checkDigit.status()).isEqualTo(400);
    assertThat(fieldCode(checkDigit, "legal.nif")).isEqualTo("municipality.nif-check-digit");

    // Unha importación ou unha copia con minúsculas e guións gárdase normalizada.
    legal.put("nif", "q-1573421 c");
    Api.Response saved = put(slug, 0, legal);
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    assertThat(saved.json().get("legal").get("nif").asString()).isEqualTo("Q1573421C");
  }

  private static String fieldCode(Api.Response response, String field) {
    for (JsonNode error : response.json().get("errors")) {
      if (field.equals(error.get("field").asString())) {
        return error.get("code").asString();
      }
    }
    return "";
  }

  @Test
  void theOwnPageModeNeedsAnHttpsAddress() {
    String slug = uniqueSlug("legal");
    createMunicipality(slug);
    Map<String, Object> withoutUrl = new HashMap<>(LEGAL);
    withoutUrl.remove("privacyUrl");
    withoutUrl.put("legalNoticeMode", "URL");
    withoutUrl.put("legalNoticeUrl", "http://naron.example.org/aviso-legal");
    withoutUrl.put("accessibilityMode", "PDF");

    // Primeiro o formato (DTO): enderezo sen https e modo descoñecido.
    Api.Response badFormat = put(slug, 0, withoutUrl);
    assertThat(badFormat.status()).isEqualTo(400);
    assertThat(badFormat.body()).contains("legal.legalNoticeUrl", "legal.accessibilityMode");

    // Co formato ben, o dominio pide o enderezo da páxina propia.
    withoutUrl.put("legalNoticeUrl", "https://naron.example.org/aviso-legal");
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
        .isEqualTo("https://naron.example.org/privacidade");
    assertThat(mailContexts.mailContext(id).orElseThrow().privacyPublished()).isFalse();
  }
}
