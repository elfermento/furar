// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Aviso para instalar no móbil (Concello › Xeral): aceso por defecto, visible no dato público do
 * concello que le a portada; a xestión e a plataforma apágano co {@code If-Match} do concello e
 * queda no rexistro de actividade.
 */
class MunicipalityInstallHintIT extends AbstractIT {

  static String url(String slug) {
    return "/api/v1/municipalities/" + slug;
  }

  boolean publicValue(String slug) {
    return api().get(url(slug)).send().json().get("installHint").asBoolean();
  }

  long version(String slug) {
    return api().get(url(slug)).token(TestTokens.manager(slug)).send().etagVersion();
  }

  Api.Response save(String slug, String token, long version, boolean enabled) {
    Api.Call call =
        api().put(url(slug) + "/install-hint", Map.of("enabled", enabled)).ifMatch(version);
    return token == null ? call.send() : call.token(token).send();
  }

  @Test
  void onByDefaultAndTheManagerTurnsItOff() {
    String slug = uniqueSlug("instalar");
    UUID municipality = id(createMunicipality(slug));
    assertThat(publicValue(slug)).isTrue();

    Api.Response saved = save(slug, TestTokens.manager(slug), version(slug), false);

    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.json().toString()).isEqualTo("{\"enabled\":false}");
    assertThat(saved.etagVersion()).isEqualTo(version(slug));
    assertThat(publicValue(slug)).isFalse();
    assertThat(
            jdbc.sql(
                    """
                    select details::text from audit_event where municipality_id = :id
                      and action = 'municipality.install-hint'
                    """)
                .param("id", municipality)
                .query(String.class)
                .single())
        .contains("installHint");
    // A plataforma configura o concello enteiro (ADR-0028).
    assertThat(save(slug, TestTokens.platformAdmin(), version(slug), true).status()).isEqualTo(200);
    assertThat(publicValue(slug)).isTrue();
  }

  @Test
  void otherRolesAndStaleVersionsAreRejected() {
    String slug = uniqueSlug("instalar-roles");
    createMunicipality(slug);
    long version = version(slug);
    for (String token :
        List.of(TestTokens.staff(slug), TestTokens.resident(slug), TestTokens.manager("outro"))) {
      assertThat(save(slug, token, version, false).status()).isEqualTo(403);
    }
    assertThat(save(slug, null, version, false).status()).isEqualTo(401);
    assertThat(save(slug, TestTokens.manager(slug), version + 1, false).status()).isEqualTo(412);
    assertThat(publicValue(slug)).isTrue();
  }
}
