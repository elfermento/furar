// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * As cabeceiras do contrato son as das respostas reais (ADR-0009, curso: «o contrato non leva
 * Location nin ETag»). Chama de verdade a cada operación con resposta correcta e compara co {@code
 * api/openapi.json} subido: un 201 declara e manda {@code Location}; unha operación {@link
 * VersionedResource} declara e manda {@code ETag}, e as demais nin o declaran nin o mandan.
 */
class ContractHeadersIT extends AbstractIT {

  @Test
  void declaredHeadersAreTheOnesTheApiSends() throws IOException {
    Map<String, Api.Response> calls = new TreeMap<>();
    String slug = uniqueSlug("cab");
    calls.put(
        "createMunicipality",
        api()
            .post(
                "/api/v1/municipalities",
                Map.of(
                    "slug",
                    slug,
                    "name",
                    "Concello " + slug,
                    "languages",
                    List.of("gl", "es"),
                    "defaultLanguage",
                    "gl"))
            .token(TestTokens.platformAdmin())
            .send());
    String base = "/api/v1/municipalities/" + slug;
    calls.put("listMunicipalities", api().get("/api/v1/municipalities").send());
    calls.put("getMunicipality", api().get(base).send());
    calls.put("getMunicipalityTheme", api().get(base + "/theme").send());
    calls.put("getPrivacyNotice", api().get("/api/v1/privacy-notice").send());
    calls.put("getDeploymentSettings", api().get("/api/v1/deployment-settings").send());
    calls.put(
        "updateMunicipality",
        api()
            .put(base, updateBody("Concello editado", Map.of()))
            .token(TestTokens.manager(slug))
            .ifMatch(version(calls.get("getMunicipality")))
            .send());
    Api.Response created =
        api()
            .post(base + "/custom-fields", textField("cabeceira", "PUBLIC"))
            .token(TestTokens.manager(slug))
            .send();
    calls.put("createCustomField", created);
    String field = base + "/custom-fields/" + id(created.json());
    calls.put("listCustomFields", api().get(base + "/custom-fields?target=MUNICIPALITY").send());
    calls.put("getCustomField", api().get(field).token(TestTokens.manager(slug)).send());
    calls.put(
        "deactivateCustomField",
        api()
            .post(field + "/deactivate", Map.of())
            .token(TestTokens.manager(slug))
            .ifMatch(version(created))
            .send());
    calls.put(
        "activateCustomField",
        api()
            .post(field + "/activate", Map.of())
            .token(TestTokens.manager(slug))
            .ifMatch(version(calls.get("deactivateCustomField")))
            .send());
    calls.put(
        "deleteCustomField",
        api()
            .delete(field)
            .token(TestTokens.manager(slug))
            .ifMatch(version(calls.get("activateCustomField")))
            .send());

    // Persoal (D4-07): invitar, reenviar, cambiar o rol e desactivar, cada un co ETag anterior.
    Api.Response invited =
        api()
            .post(
                base + "/staff",
                Map.of("email", "cabeceira@example.org", "role", "STAFF", "language", "gl"))
            .token(TestTokens.manager(slug))
            .send();
    calls.put("inviteStaffMember", invited);
    String member = base + "/staff/" + id(invited.json());
    calls.put("listStaff", api().get(base + "/staff").token(TestTokens.manager(slug)).send());
    calls.put("getStaffMember", api().get(member).token(TestTokens.manager(slug)).send());
    // O reenvío pide 2 minutos desde o envío anterior.
    jdbc.sql(
            """
            update staff_invitation
               set created_at = created_at - interval '3 minutes',
                   expires_at = expires_at - interval '3 minutes'
             where member_id = :m
            """)
        .param("m", id(invited.json()))
        .update();
    calls.put(
        "resendStaffInvitation",
        api()
            .post(member + "/invitation", Map.of())
            .token(TestTokens.manager(slug))
            .ifMatch(version(invited))
            .send());
    calls.put(
        "changeStaffRole",
        api()
            .put(member + "/role", Map.of("role", "MANAGER"))
            .token(TestTokens.manager(slug))
            .ifMatch(version(calls.get("resendStaffInvitation")))
            .send());
    calls.put(
        "deactivateStaffMember",
        api()
            .post(member + "/deactivate", Map.of())
            .token(TestTokens.manager(slug))
            .ifMatch(version(calls.get("changeStaffRole")))
            .send());
    // Reactivar: só quen aceptou algunha vez, así que unha persoa xa desactivada (por JDBC).
    UUID deactivated = UUID.randomUUID();
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, deactivated_by,
               deactivated_at, created_at, updated_at, version)
            values (:id, :m, 'cabeceira-desactivada@example.org', 'Persoa', :subject, 'STAFF',
                    'DEACTIVATED', 'gl', 'sub-semente', now(), now(), 'sub-semente',
                    now(), now(), now(), 0)
            """)
        .param("id", deactivated)
        .param("m", id(calls.get("createMunicipality").json()))
        .param("subject", "sub-" + deactivated)
        .update();
    calls.put(
        "reactivateStaffMember",
        api()
            .post(base + "/staff/" + deactivated + "/reactivate", Map.of())
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send());

    JsonNode contract = Api.JSON.readTree(Files.readString(Path.of("openapi.json")));
    List<String> problems = new ArrayList<>();
    calls.forEach(
        (operationId, r) -> {
          if (r.status() / 100 != 2) {
            problems.add(operationId + ": status " + r.status() + " " + r.body());
            return;
          }
          JsonNode headers = declaredHeaders(contract, operationId, r.status());
          for (String h : List.of("Location", "ETag")) {
            boolean declared = headers != null && headers.has(h);
            boolean sent = r.header(h) != null;
            if (declared != sent) {
              problems.add(operationId + ": " + h + " declared=" + declared + " sent=" + sent);
            }
          }
        });
    assertThat(problems).as("headers of api/openapi.json vs real responses").isEmpty();
  }

  /** Autoproba: todo 201 do contrato declara Location (tamén os que este test non chama). */
  @Test
  void every201DeclaresLocation() throws IOException {
    JsonNode contract = Api.JSON.readTree(Files.readString(Path.of("openapi.json")));
    List<String> missing = new ArrayList<>();
    contract
        .get("paths")
        .properties()
        .forEach(
            path ->
                path.getValue()
                    .properties()
                    .forEach(
                        op -> {
                          JsonNode created = op.getValue().path("responses").path("201");
                          if (!created.isMissingNode()
                              && !created.path("headers").has("Location")) {
                            missing.add(op.getValue().path("operationId").asString());
                          }
                        }));
    assertThat(missing).as("201 responses without Location in api/openapi.json").isEmpty();
  }

  private static long version(Api.Response r) {
    String etag = r.header("ETag");
    return etag == null ? 0 : Long.parseLong(etag.replace("\"", "").replace("W/", ""));
  }

  private static JsonNode declaredHeaders(JsonNode contract, String operationId, int status) {
    for (var path : contract.get("paths").properties()) {
      for (var op : path.getValue().properties()) {
        if (operationId.equals(op.getValue().path("operationId").asString())) {
          return op.getValue().path("responses").path(String.valueOf(status)).get("headers");
        }
      }
    }
    throw new AssertionError("operation " + operationId + " not in api/openapi.json");
  }
}
