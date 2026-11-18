// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * De punta a punta con PostgreSQL real: feliz, edición con If-Match, erro, illamento e outro
 * concello → 403.
 */
class @Name@IT extends AbstractIT {

  private Api.Response create(String slug, String name, String token) {
    Api.Call call =
        api().post("/api/v1/municipalities/" + slug + "/@names@", Map.of("name", name));
    return (token == null ? call : call.token(token)).send();
  }

  @Test
  void createThenReadInTheSameMunicipality() {
    String slug = uniqueSlug("@names@");
    createMunicipality(slug);

    Api.Response created = create(slug, "Primeiro", TestTokens.manager(slug));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    assertThat(created.header("Location")).endsWith("/@names@/" + id(created.json()));
    assertThat(created.etagVersion()).isZero();

    Api.Response list = api().get("/api/v1/municipalities/" + slug + "/@names@").send();
    assertThat(list.status()).isEqualTo(200);
    assertThat(list.json().get("totalItems").asLong()).isEqualTo(1);
    assertThat(list.json().get("items").get(0).get("name").asString()).isEqualTo("Primeiro");

    Api.Response one =
        api().get("/api/v1/municipalities/" + slug + "/@names@/" + id(created.json())).send();
    assertThat(one.status()).isEqualTo(200);
  }

  @Test
  void updateNeedsIfMatchWithTheCurrentVersion() {
    String slug = uniqueSlug("@names@");
    createMunicipality(slug);
    Api.Response created = create(slug, "Vello", TestTokens.manager(slug));
    String url = "/api/v1/municipalities/" + slug + "/@names@/" + id(created.json());

    Api.Response updated =
        api()
            .put(url, Map.of("name", "Novo"))
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    assertThat(updated.etagVersion()).isEqualTo(1);
    assertThat(updated.json().get("name").asString()).isEqualTo("Novo");

    Api.Response stale =
        api()
            .put(url, Map.of("name", "Outro"))
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(stale.status()).isEqualTo(412);
    assertThat(stale.json().get("code").asString()).isEqualTo("common.stale-version");
  }

  @Test
  void invalidBodyIsAProblem() {
    String slug = uniqueSlug("@names@");
    createMunicipality(slug);
    Api.Response response = create(slug, " ", TestTokens.manager(slug));
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.contentType()).startsWith("application/problem+json");
    assertThat(response.json().get("code").asString()).isEqualTo("validation.failed");
  }

  @Test
  void anotherMunicipalityNeitherSeesNorReads() {
    String a = uniqueSlug("@names@-a");
    String b = uniqueSlug("@names@-b");
    createMunicipality(a);
    createMunicipality(b);
    Api.Response created = create(a, "Só en A", TestTokens.manager(a));

    Api.Response listB = api().get("/api/v1/municipalities/" + b + "/@names@").send();
    assertThat(listB.json().get("totalItems").asLong()).isZero();

    Api.Response readFromB =
        api().get("/api/v1/municipalities/" + b + "/@names@/" + id(created.json())).send();
    assertThat(readFromB.status()).isEqualTo(404);
    assertThat(readFromB.json().get("code").asString()).isEqualTo("@module@.not-found");
  }

  /** ADR-0018: a sección do módulo vai no ficheiro furar-export do seu concello e só no seu. */
  @Test
  void exportCarriesTheModuleSection() {
    String a = uniqueSlug("@names@-a");
    String b = uniqueSlug("@names@-b");
    createMunicipality(a);
    createMunicipality(b);
    Api.Response created = create(a, "Exportable", TestTokens.manager(a));

    Api.Response exportA =
        api().get("/api/v1/municipalities/" + a + "/export").token(TestTokens.manager(a)).send();
    assertThat(exportA.status()).as(exportA.toString()).isEqualTo(200);
    var rows = exportA.json().get("sections").get("@module@");
    assertThat(rows.size()).isEqualTo(1);
    assertThat(rows.get(0).get("id").asString()).isEqualTo(id(created.json()).toString());

    Api.Response exportB =
        api().get("/api/v1/municipalities/" + b + "/export").token(TestTokens.manager(b)).send();
    assertThat(exportB.json().get("sections").get("@module@").size()).isZero();
  }

  @Test
  void writesNeedATokenWithPermissionInThatMunicipality() {
    String a = uniqueSlug("@names@-a");
    String b = uniqueSlug("@names@-b");
    createMunicipality(a);
    createMunicipality(b);

    assertThat(create(a, "Sen token", null).status()).isEqualTo(401);
    Api.Response otherManager = create(a, "Xestor de B", TestTokens.manager(b));
    assertThat(otherManager.status()).isEqualTo(403);
    assertThat(otherManager.json().get("code").asString()).isEqualTo("auth.forbidden");
  }
}
