// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Fluxos HTTP do concello: alta, lista, detalle, tema e edición con versión. */
class MunicipalityIT extends AbstractIT {

  @Test
  void platformCreatesAndAnyoneReads() {
    String slug = uniqueSlug("coruna");
    JsonNode created = createMunicipality(slug);
    assertThat(created.get("slug").asString()).isEqualTo(slug);
    assertThat(created.get("version").asLong()).isZero();
    assertThat(created.get("theme").get("primary").asString()).isEqualTo("#0b4f8a");

    Api.Response detail = api().get("/api/v1/municipalities/" + slug).send();
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.header("ETag")).isEqualTo("\"0\"");
    assertThat(detail.json().get("languages").toString()).isEqualTo("[\"gl\",\"es\"]");

    Api.Response theme = api().get("/api/v1/municipalities/" + slug + "/theme").send();
    assertThat(theme.status()).isEqualTo(200);
    assertThat(theme.json().get("text").asString()).isEqualTo("#1a1a1a");

    Api.Response list = api().get("/api/v1/municipalities?size=100").send();
    assertThat(list.status()).isEqualTo(200);
    assertThat(list.json().get("items").toString()).contains(slug);
    assertThat(list.json().get("size").asInt()).isEqualTo(100);
  }

  @Test
  void duplicatedSlugIsAConflict() {
    String slug = uniqueSlug("lugo");
    createMunicipality(slug);
    Api.Response again =
        api()
            .post(
                "/api/v1/municipalities",
                Map.of(
                    "slug",
                    slug,
                    "name",
                    "Lugo",
                    "languages",
                    List.of("gl"),
                    "defaultLanguage",
                    "gl"))
            .token(TestTokens.platformAdmin())
            .send();
    assertThat(again.status()).isEqualTo(409);
    assertThat(again.json().get("code").asString()).isEqualTo("municipality.slug-taken");
    assertThat(again.json().get("params").get("slug").asString()).isEqualTo(slug);
  }

  @Test
  void reservedSlugAndLowContrastAreRejected() {
    Api.Response reserved =
        api()
            .post(
                "/api/v1/municipalities",
                Map.of(
                    "slug",
                    "admin",
                    "name",
                    "Admin",
                    "languages",
                    List.of("gl"),
                    "defaultLanguage",
                    "gl"))
            .token(TestTokens.platformAdmin())
            .send();
    assertThat(reserved.status()).isEqualTo(422);
    assertThat(reserved.json().get("code").asString()).isEqualTo("municipality.slug-reserved");

    Api.Response lowContrast =
        api()
            .post(
                "/api/v1/municipalities",
                Map.of(
                    "slug",
                    uniqueSlug("gris"),
                    "name",
                    "Gris",
                    "languages",
                    List.of("gl"),
                    "defaultLanguage",
                    "gl",
                    "theme",
                    Map.of(
                        "primary", "#777777",
                        "onPrimary", "#888888",
                        "accent", "#b3541e",
                        "background", "#ffffff",
                        "text", "#1a1a1a")))
            .token(TestTokens.platformAdmin())
            .send();
    assertThat(lowContrast.status()).isEqualTo(422);
    assertThat(lowContrast.json().get("code").asString())
        .isEqualTo("municipality.theme-low-contrast");
  }

  @Test
  void managerUpdatesWithVersion() {
    String slug = uniqueSlug("ourense");
    createMunicipality(slug);
    Api.Response updated =
        api()
            .put("/api/v1/municipalities/" + slug, updateBody("Ourense", Map.of()))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(updated.status()).as(updated.body()).isEqualTo(200);
    assertThat(updated.json().get("name").asString()).isEqualTo("Ourense");
    assertThat(updated.etagVersion()).isEqualTo(1);

    Api.Response stale =
        api()
            .put("/api/v1/municipalities/" + slug, updateBody("Ourense 2", Map.of()))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(stale.status()).isEqualTo(412);
    assertThat(stale.json().get("code").asString()).isEqualTo("common.stale-version");

    Api.Response noIfMatch =
        api()
            .put("/api/v1/municipalities/" + slug, updateBody("Ourense 3", Map.of()))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(noIfMatch.status()).isEqualTo(428);
    assertThat(noIfMatch.json().get("code").asString()).isEqualTo("common.precondition-required");
  }

  @Test
  void createPublishesEvent() {
    String slug = uniqueSlug("vigo");
    createMunicipality(slug);
    Integer publications =
        jdbc.sql("select count(*) from event_publication where serialized_event like :s")
            .param("s", "%" + slug + "%")
            .query(Integer.class)
            .single();
    assertThat(publications).isGreaterThanOrEqualTo(0);
  }
}
