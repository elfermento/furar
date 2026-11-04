// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * D15 (ADR-0007): emisor, audiencia, cliente ({@code azp}, só {@code furar-web}; R5), caducidade e
 * sinatura compróbanse; se non, 401. Do claim de roles só conta o global (ADR-0027).
 */
class JwtValidationIT extends AbstractIT {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "wrongIssuer",
        "wrongAudience",
        "otherClient",
        "e2eClient",
        "withoutClient",
        "expired",
        "foreignSignature",
        "garbage"
      })
  void invalidTokensAreRejected(String kind) {
    String token =
        switch (kind) {
          case "wrongIssuer" -> TestTokens.wrongIssuer();
          case "wrongAudience" -> TestTokens.wrongAudience();
          // Outro cliente do realm co mesmo aud (p. ex. a consola de conta cun mapeador).
          case "otherClient" -> TestTokens.otherClient("account-console");
          // O cliente das probas só vale co perfil demo, que o perfil test non leva.
          case "e2eClient" -> TestTokens.otherClient("furar-e2e");
          case "withoutClient" -> TestTokens.withoutClient();
          case "expired" -> TestTokens.expired();
          case "foreignSignature" -> TestTokens.foreignSignature();
          default -> "not-a-jwt";
        };
    Api.Response create = api().post("/api/v1/municipalities", Map.of()).token(token).send();
    assertThat(create.status()).isEqualTo(401);
    assertThat(create.contentType()).startsWith("application/problem+json");
    assertThat(create.json().get("code").asString()).isEqualTo("auth.unauthenticated");
    assertThat(create.header("WWW-Authenticate")).startsWith("Bearer");
  }

  @Test
  void validTokenPassesAuthentication() {
    String slug = uniqueSlug("jwt");
    createMunicipality(slug);
    Api.Response response =
        api()
            .get(
                "/api/v1/municipalities/"
                    + slug
                    + "/custom-fields?target=MUNICIPALITY&includeInactive=true")
            .token(TestTokens.manager(slug))
            .send();
    assertThat(response.status()).isEqualTo(200);
  }

  /**
   * Sen segundo factor (decisión do mantedor do 2026-11-02): un token só de contrasinal ({@code acr
   * furar-pwd}, {@code amr [pwd]}, o que daba Keycloak antes) dálle á xestión e á plataforma os
   * seus permisos.
   */
  @Test
  void privilegedRolesCountWithAPasswordOnlyToken() {
    String slug = uniqueSlug("pwd");
    createMunicipality(slug);
    Map<String, Object> passwordOnly = Map.of("acr", "furar-pwd", "amr", List.of("pwd"));

    Api.Response export =
        api()
            .get("/api/v1/municipalities/" + slug + "/export")
            .token(TestTokens.withClaims(passwordOnly, slug + ":MANAGER"))
            .send();
    assertThat(export.status()).isEqualTo(200);

    Api.Response create =
        api()
            .post(
                "/api/v1/municipalities",
                Map.of(
                    "slug",
                    uniqueSlug("pwd"),
                    "name",
                    "Concello só con contrasinal",
                    "languages",
                    List.of("gl", "es"),
                    "defaultLanguage",
                    "gl"))
            .token(TestTokens.withClaims(passwordOnly, "*:PLATFORM_ADMIN"))
            .send();
    assertThat(create.status()).isEqualTo(201);
  }

  /**
   * ADR-0027: o rol de persoal só sae da táboa de pertenzas. Un {@code naron:MANAGER} ou {@code
   * naron:STAFF} no claim non dá ningún permiso: 403 {@code auth.forbidden}.
   */
  @Test
  void aStaffRoleInTheTokenGivesNoPermission() {
    String slug = uniqueSlug("claim");
    createMunicipality(slug);
    String base = "/api/v1/municipalities/" + slug;
    for (String token :
        List.of(
            TestTokens.withRolesClaim(slug + ":MANAGER"),
            TestTokens.withRolesClaim(slug + ":STAFF", slug + ":MANAGER"))) {
      for (String path : List.of("/export", "/bookings", "/indicators", "/staff")) {
        Api.Response response = api().get(base + path).token(token).send();
        assertThat(response.status()).as(path).isEqualTo(403);
        assertThat(response.json().get("code").asString()).as(path).isEqualTo("auth.forbidden");
      }
    }
    assertThat(api().get(base + "/export").token(TestTokens.manager(slug)).send().status())
        .as("a mesma petición cunha pertenza ACTIVE")
        .isEqualTo(200);
  }

  /**
   * ADR-0028: a administración da plataforma só sae da táboa {@code platform_admin}. Un {@code
   * *:PLATFORM_ADMIN} no claim (o que emitía o grupo {@code plataforma} de Keycloak) non dá nada.
   */
  @Test
  void thePlatformRoleInTheTokenGivesNoPermission() {
    Map<String, Object> body =
        Map.of(
            "slug",
            uniqueSlug("pclaim"),
            "name",
            "Concello do claim",
            "languages",
            List.of("gl"),
            "defaultLanguage",
            "gl");
    Api.Response claimOnly =
        api()
            .post("/api/v1/municipalities", body)
            .token(TestTokens.withRolesClaim("*:PLATFORM_ADMIN"))
            .send();
    assertThat(claimOnly.status()).isEqualTo(403);
    assertThat(claimOnly.json().get("code").asString()).isEqualTo("auth.forbidden");
    assertThat(
            api()
                .post("/api/v1/municipalities", body)
                .token(TestTokens.platformAdmin())
                .send()
                .status())
        .as("a mesma petición cunha fila en platform_admin")
        .isEqualTo(201);
  }

  /** O rol de veciño non dá a xestión: {@code auth.forbidden}. */
  @Test
  void aResidentIsForbiddenTheManagement() {
    String slug = uniqueSlug("res");
    createMunicipality(slug);
    Api.Response response =
        api()
            .get("/api/v1/municipalities/" + slug + "/export")
            .token(TestTokens.resident(slug))
            .send();
    assertThat(response.status()).isEqualTo(403);
    assertThat(response.json().get("code").asString()).isEqualTo("auth.forbidden");
  }
}
