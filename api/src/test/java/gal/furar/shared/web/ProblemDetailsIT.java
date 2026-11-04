// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestSupportConfig;
import gal.furar.testsupport.TestTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * D16 (ADR-0008): todo erro, tamén os que nacen en filtros, sae en {@code application/problem+json}
 * co {@code code} estable, texto traducido segundo {@code Accept-Language} e {@code traceId}.
 */
class ProblemDetailsIT extends AbstractIT {

  String slug;

  @BeforeEach
  void setUp() {
    slug = uniqueSlug("prob");
    createMunicipality(slug);
  }

  @Test
  void noToken() {
    Api.Response r = api().put("/api/v1/municipalities/" + slug, updateBody("x", Map.of())).send();
    assertProblem(r, 401, "auth.unauthenticated");
    assertThat(r.json().get("title").asString()).isEqualTo("Tes que iniciar sesión");
  }

  @Test
  void expiredToken() {
    Api.Response r =
        api()
            .put("/api/v1/municipalities/" + slug, updateBody("x", Map.of()))
            .token(TestTokens.expired())
            .send();
    assertProblem(r, 401, "auth.unauthenticated");
  }

  @Test
  void withoutRole() {
    Api.Response r =
        api()
            .put("/api/v1/municipalities/" + slug, updateBody("x", Map.of()))
            .token(TestTokens.resident(slug))
            .ifMatch(0)
            .language("es")
            .send();
    assertProblem(r, 403, "auth.forbidden");
    assertThat(r.json().get("title").asString()).isEqualTo("Sin permiso");
    assertThat(r.header("Content-Language")).isEqualTo("es");
  }

  @Test
  void unknownMunicipalityFromTheFilter() {
    Api.Response r =
        api().get("/api/v1/municipalities/non-existe").language("es-ES,es;q=0.9").send();
    assertProblem(r, 404, "municipality.not-found");
    assertThat(r.json().get("title").asString()).isEqualTo("Ayuntamiento no encontrado");
    Api.Response gl = api().get("/api/v1/municipalities/non-existe/theme").send();
    assertThat(gl.json().get("title").asString()).isEqualTo("Concello non atopado");
  }

  @Test
  void unknownRoute() {
    Api.Response r = api().get("/api/v1/nothing-here").token(TestTokens.platformAdmin()).send();
    assertProblem(r, 404, "common.not-found");
  }

  @Test
  void actuatorIsNotOnTheApiPort() {
    Api.Response r = api().get("/actuator/env").send();
    assertProblem(r, 404, "common.not-found");
  }

  @Test
  void malformedJson() {
    Api.Response r =
        api().post("/api/v1/municipalities", "{not json").token(TestTokens.platformAdmin()).send();
    assertProblem(r, 400, "common.malformed-json");
  }

  @Test
  void methodNotAllowed() {
    Api.Response r =
        api().delete("/api/v1/municipalities/" + slug).token(TestTokens.manager(slug)).send();
    assertProblem(r, 405, "common.method-not-allowed");
  }

  @Test
  void validationWithFieldErrors() {
    Api.Response r =
        api()
            .post(
                "/api/v1/municipalities",
                Map.of(
                    "slug", "X!", "name", "", "languages", List.of("en"), "defaultLanguage", "gl"))
            .token(TestTokens.platformAdmin())
            .send();
    assertProblem(r, 400, "validation.failed");
    JsonNode errors = r.json().get("errors");
    assertThat(errors.toString())
        .contains(
            "\"field\":\"slug\"",
            "\"field\":\"name\"",
            "validation.pattern",
            "validation.required");
  }

  @Test
  void staleVersion() {
    Api.Response r =
        api()
            .put("/api/v1/municipalities/" + slug, updateBody("x", Map.of()))
            .token(TestTokens.manager(slug))
            .ifMatch(7)
            .send();
    assertProblem(r, 412, "common.stale-version");
  }

  @Test
  void internalErrorHidesDetails() {
    Api.Response r = api().get(TestSupportConfig.BOOM_PATH).send();
    assertProblem(r, 500, "common.internal");
    assertThat(r.body()).doesNotContain("secret", "jdbc", "Exception", "at gal.");
  }

  private static void assertProblem(Api.Response r, int status, String code) {
    assertThat(r.status()).as(r.body()).isEqualTo(status);
    assertThat(r.contentType()).startsWith("application/problem+json");
    JsonNode body = r.json();
    assertThat(field(body, "code").asString()).isEqualTo(code);
    assertThat(field(body, "status").asInt()).isEqualTo(status);
    assertThat(field(body, "type").asString()).isEqualTo("https://furar.gal/problemas/" + code);
    assertThat(field(body, "title").asString()).isNotBlank();
    assertThat(field(body, "detail").asString()).isNotBlank();
    assertThat(field(body, "traceId").asString()).matches("[0-9a-f]{16}");
    field(body, "params");
  }

  /** O campo do problema; se falta, unha aserción que o nomea (fallo, non NPE: N8). */
  static JsonNode field(JsonNode body, String name) {
    assertThat(body.has(name)).as("problem field '%s' missing in %s", name, body).isTrue();
    return body.get(name);
  }

  /** Autoproba de D16: un problema sen {@code traceId} falla cunha aserción que o nomea. */
  @Test
  void missingFieldFailsWithAnAssertionNamingIt() {
    JsonNode body = Api.JSON.readTree("{\"code\":\"x\"}");
    assertThatThrownBy(() -> field(body, "traceId"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("problem field 'traceId' missing");
  }
}
