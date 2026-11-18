// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * As preferencias de accesibilidade da conta (ADR-0031, revisión do 2026-11-11): 204 ata que se
 * gardan, só as da propia conta, {@code null} = segue o sistema, e a baixa da conta bórraas.
 */
class DisplayPreferencesIT extends AbstractIT {

  static final String URL = "/api/v1/me/display-preferences";

  static Map<String, Object> body(String size, Boolean contrast, Boolean motion) {
    Map<String, Object> body = new HashMap<>();
    body.put("textSize", size);
    body.put("highContrast", contrast);
    body.put("reducedMotion", motion);
    return body;
  }

  @Test
  void savedOnlyForTheOwnAccount() {
    String subject = "prefs-" + UUID.randomUUID();
    String ana = TestTokens.person(subject, true);
    String bea = TestTokens.person("prefs-" + UUID.randomUUID(), true);

    assertThat(api().get(URL).token(ana).send().status()).isEqualTo(204);

    Api.Response saved = api().put(URL, body("x-large", true, null)).token(ana).send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(204);
    Api.Response read = api().get(URL).token(ana).send();
    assertThat(read.status()).isEqualTo(200);
    JsonNode json = read.json();
    assertThat(json.get("textSize").asString()).isEqualTo("x-large");
    assertThat(json.get("highContrast").asBoolean()).isTrue();
    assertThat(json.get("reducedMotion").isNull()).isTrue();

    // Gardar outra vez substitúe.
    assertThat(api().put(URL, body("normal", null, false)).token(ana).send().status())
        .isEqualTo(204);
    JsonNode again = api().get(URL).token(ana).send().json();
    assertThat(again.get("textSize").asString()).isEqualTo("normal");
    assertThat(again.get("highContrast").isNull()).isTrue();
    assertThat(again.get("reducedMotion").asBoolean()).isFalse();

    // Outra conta non as ve.
    assertThat(api().get(URL).token(bea).send().status()).isEqualTo(204);

    // A baixa da conta bórraas.
    jdbc.sql("select close_account(:s)").param("s", subject).query().singleRow();
    assertThat(api().get(URL).token(ana).send().status()).isEqualTo(204);
  }

  @Test
  void anUnknownSizeOrNoSessionIsRejected() {
    String ana = TestTokens.person("prefs-" + UUID.randomUUID(), true);
    assertThat(api().put(URL, body("huge", null, null)).token(ana).send().status()).isEqualTo(400);
    assertThat(api().put(URL, body(null, null, null)).token(ana).send().status()).isEqualTo(400);
    assertThat(api().get(URL).send().status()).isEqualTo(401);
  }
}
