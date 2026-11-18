// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A penalización das faltas do concello de punta a punta (ADR-0032, puntos 2, 6 e 8): apagada por
 * defecto cos números previstos, só a xestión a cambia co {@code If-Match} do concello, os rangos e
 * o rexistro de actividade co que cambiou.
 */
class MunicipalityNoShowPolicyIT extends AbstractIT {

  static String url(String slug) {
    return "/api/v1/municipalities/" + slug;
  }

  JsonNode policy(String slug) {
    return api().get(url(slug)).token(TestTokens.manager(slug)).send().json().get("noShowPolicy");
  }

  long version(String slug) {
    return api().get(url(slug)).token(TestTokens.manager(slug)).send().etagVersion();
  }

  Api.Response save(String slug, String token, long version, Map<String, Object> body) {
    Api.Call call = api().put(url(slug) + "/no-show-policy", body).ifMatch(version);
    return token == null ? call.send() : call.token(token).send();
  }

  static Map<String, Object> body(boolean enabled, int threshold, int windowDays, int blockDays) {
    return Map.of(
        "enabled",
        enabled,
        "threshold",
        threshold,
        "windowDays",
        windowDays,
        "blockDays",
        blockDays);
  }

  @Test
  void offByDefaultAndTheManagerTurnsItOnWithItsNumbers() {
    String slug = uniqueSlug("faltas");
    UUID municipality = id(createMunicipality(slug));
    assertThat(policy(slug).toString())
        .isEqualTo("{\"enabled\":false,\"threshold\":3,\"windowDays\":30,\"blockDays\":15}");

    Api.Response saved = save(slug, TestTokens.manager(slug), version(slug), body(true, 2, 60, 7));

    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.json().toString())
        .isEqualTo("{\"enabled\":true,\"threshold\":2,\"windowDays\":60,\"blockDays\":7}");
    assertThat(saved.etagVersion()).isEqualTo(version(slug));
    assertThat(policy(slug).get("enabled").asBoolean()).isTrue();
    assertThat(
            jdbc.sql("select no_show_penalty_since is not null from municipality where id = :id")
                .param("id", municipality)
                .query(Boolean.class)
                .single())
        .as("garda desde cando: só contan as faltas posteriores")
        .isTrue();

    // Apagala deixa os números gardados.
    Api.Response off = save(slug, TestTokens.manager(slug), version(slug), body(false, 2, 60, 7));
    assertThat(off.status()).isEqualTo(200);
    assertThat(off.json().get("enabled").asBoolean()).isFalse();
    assertThat(off.json().get("windowDays").asInt()).isEqualTo(60);
    assertThat(
            jdbc.sql(
                    """
                    select action || ' ' || outcome || ' ' || coalesce(details ->> 'code', '')
                    from audit_event where municipality_id = :id
                      and action = 'municipality.no-show-policy'
                    order by occurred_at
                    """)
                .param("id", municipality)
                .query(String.class)
                .list())
        .containsExactly(
            "municipality.no-show-policy SUCCESS ", "municipality.no-show-policy SUCCESS ");
    assertThat(
            jdbc.sql(
                    """
                    select details::text from audit_event where municipality_id = :id
                      and action = 'municipality.no-show-policy'
                    order by occurred_at limit 1
                    """)
                .param("id", municipality)
                .query(String.class)
                .single())
        .as("o que cambiou sae nos cambios")
        .contains("noShow.penalty", "noShow.threshold", "noShow.windowDays", "noShow.blockDays");
  }

  @Test
  void theNumbersOutOfRangeAndTheStaleVersionAreRejected() {
    String slug = uniqueSlug("faltas-rango");
    createMunicipality(slug);
    String manager = TestTokens.manager(slug);
    long version = version(slug);

    for (Map<String, Object> bad :
        java.util.List.of(
            body(true, 0, 30, 15),
            body(true, 21, 30, 15),
            body(true, 3, 0, 15),
            body(true, 3, 366, 15),
            body(true, 3, 30, 0),
            body(false, 3, 30, 366))) {
      Api.Response refused = save(slug, manager, version, bad);
      assertThat(refused.status()).as(refused.toString()).isEqualTo(422);
      assertThat(refused.json().get("code").asString())
          .isEqualTo("municipality.no-show-policy-invalid");
    }
    assertThat(save(slug, manager, version, Map.of("enabled", true)).status())
        .as("os tres números son obrigatorios")
        .isEqualTo(400);
    assertThat(save(slug, manager, version + 1, body(true, 3, 30, 15)).status()).isEqualTo(412);
    assertThat(policy(slug).get("enabled").asBoolean()).isFalse();
  }

  @Test
  void onlyTheManagementOfTheMunicipalityChangesIt() {
    String slug = uniqueSlug("faltas-permiso");
    String other = uniqueSlug("faltas-outro");
    createMunicipality(slug);
    createMunicipality(other);
    long version = version(slug);

    assertThat(save(slug, null, version, body(true, 3, 30, 15)).status()).isEqualTo(401);
    assertThat(save(slug, TestTokens.staff(slug), version, body(true, 3, 30, 15)).status())
        .isEqualTo(403);
    assertThat(save(slug, TestTokens.manager(other), version, body(true, 3, 30, 15)).status())
        .isEqualTo(403);
    assertThat(save(slug, TestTokens.resident(slug), version, body(true, 3, 30, 15)).status())
        .isEqualTo(403);
    assertThat(policy(slug).get("enabled").asBoolean()).isFalse();
  }
}
