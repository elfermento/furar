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
import tools.jackson.databind.JsonNode;

/**
 * «Buscar xente para xogar» do concello de punta a punta (ADR-0040, Concello › Xeral): apagado por
 * defecto con 12 horas, a xestión e a plataforma cámbiano co {@code If-Match} do concello, as horas
 * van de 1 a 72, e gardar apunta o último cambio.
 */
class MunicipalityOpenMatchPolicyIT extends AbstractIT {

  static String url(String slug) {
    return "/api/v1/municipalities/" + slug;
  }

  JsonNode policy(String slug) {
    return api().get(url(slug)).send().json().get("openMatchPolicy");
  }

  long version(String slug) {
    return api().get(url(slug)).token(TestTokens.manager(slug)).send().etagVersion();
  }

  Api.Response save(String slug, String token, long version, boolean enabled, int hours) {
    Api.Call call =
        api()
            .put(url(slug) + "/open-match-policy", Map.of("enabled", enabled, "cancelHours", hours))
            .ifMatch(version);
    return token == null ? call.send() : call.token(token).send();
  }

  @Test
  void offByDefaultAndTheManagerTurnsItOn() {
    String slug = uniqueSlug("partidas");
    UUID municipality = id(createMunicipality(slug));
    assertThat(policy(slug).toString()).isEqualTo("{\"enabled\":false,\"cancelHours\":12}");

    Api.Response saved = save(slug, TestTokens.manager(slug), version(slug), true, 24);

    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.json().toString()).isEqualTo("{\"enabled\":true,\"cancelHours\":24}");
    assertThat(saved.etagVersion()).isEqualTo(version(slug));
    assertThat(policy(slug).get("enabled").asBoolean()).isTrue();
    assertThat(
            jdbc.sql(
                    "select open_match_since is not null and open_match_policy_changed_at is not"
                        + " null from municipality where id = :id")
                .param("id", municipality)
                .query(Boolean.class)
                .single())
        .isTrue();
    assertThat(
            jdbc.sql(
                    """
                    select details::text from audit_event where municipality_id = :id
                      and action = 'municipality.open-match-policy'
                    """)
                .param("id", municipality)
                .query(String.class)
                .single())
        .contains("openMatch.enabled", "openMatch.cancelHours");
    // A plataforma configura o concello enteiro (ADR-0028).
    assertThat(save(slug, TestTokens.platformAdmin(), version(slug), false, 24).status())
        .isEqualTo(200);
    assertThat(policy(slug).get("enabled").asBoolean()).isFalse();
    assertThat(policy(slug).get("cancelHours").asInt())
        .as("apagado, as horas quedan")
        .isEqualTo(24);
  }

  @Test
  void hoursOutOfRangeAndOtherRolesAreRejected() {
    String slug = uniqueSlug("partidas-rango");
    createMunicipality(slug);
    String manager = TestTokens.manager(slug);
    long version = version(slug);

    for (int hours : new int[] {0, 73}) {
      Api.Response refused = save(slug, manager, version, true, hours);
      assertThat(refused.status()).as(refused.toString()).isEqualTo(422);
      assertThat(refused.json().get("code").asString())
          .isEqualTo("municipality.open-match-policy-invalid");
    }
    for (String token :
        List.of(TestTokens.staff(slug), TestTokens.resident(slug), TestTokens.manager("outro"))) {
      assertThat(save(slug, token, version, true, 12).status()).isEqualTo(403);
    }
    assertThat(save(slug, null, version, true, 12).status()).isEqualTo(401);
    assertThat(save(slug, manager, version + 1, true, 12).status()).isEqualTo(412);
    assertThat(policy(slug).get("enabled").asBoolean()).isFalse();
  }
}
