// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D18 (ADR-0011): máximo 50 definicións activas por entidade e 32 KB de valores. */
class CustomFieldsLimitsIT extends AbstractIT {

  @Test
  void fiftyFirstDefinitionIsRejected() {
    String slug = uniqueSlug("lim");
    createMunicipality(slug);
    for (int i = 1; i <= 50; i++) {
      createField(slug, textField("field_" + i, "PUBLIC"));
    }
    Api.Response r =
        api()
            .post(
                "/api/v1/municipalities/" + slug + "/custom-fields",
                textField("field_51", "PUBLIC"))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(r.status()).isEqualTo(422);
    assertThat(r.json().get("code").asString()).isEqualTo("customfield.limit-exceeded");
    assertThat(r.json().get("params").get("limit").asInt()).isEqualTo(50);
  }

  @Test
  void valuesAbove32KbAreRejected() {
    String slug = uniqueSlug("big");
    createMunicipality(slug);
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 1; i <= 7; i++) {
      createField(slug, field("long_" + i, "LONG_TEXT", "PUBLIC", Map.of()));
      values.put("long_" + i, "x".repeat(5_000));
    }
    Api.Response tooBig =
        api()
            .put("/api/v1/municipalities/" + slug, updateBody("Grande", values))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(tooBig.status()).isEqualTo(422);
    assertThat(tooBig.json().get("code").asString()).isEqualTo("customfield.limit-exceeded");

    values.remove("long_7");
    Api.Response fits =
        api()
            .put("/api/v1/municipalities/" + slug, updateBody("Grande", values))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(fits.status()).isEqualTo(200);
  }
}
