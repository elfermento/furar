// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * D15 (ADR-0007): o xestor de A en cada escritura de B → 403 {@code auth.forbidden}; o permiso
 * compróbase no caso de uso e no concello do URL. Tamén: veciños e persoal sen permiso → 403; só a
 * plataforma dá de alta concellos, e a plataforma configura o concello pero non ve os datos da
 * veciñanza (reservas, exportación, indicadores; ADR-0028, revisión do 2026-11-03).
 */
class CrossTenantAuthorizationIT extends AbstractIT {

  String a;
  String b;
  UUID fieldB;

  @BeforeEach
  void setUp() {
    a = uniqueSlug("authz-a");
    b = uniqueSlug("authz-b");
    createMunicipality(a);
    createMunicipality(b);
    fieldB = id(createField(b, textField("field_b", "PUBLIC")));
  }

  @Test
  void managerOfAnotherMunicipalityIsForbiddenOnEveryWrite() {
    assertForbidden(TestTokens.manager(a));
  }

  @Test
  void residentsAndStaffCannotManage() {
    assertForbidden(TestTokens.resident(b));
    assertForbidden(TestTokens.staff(b));
  }

  /**
   * Mínimo privilexio (ADR-0007; ADR-0028, revisión do 2026-11-03): {@code *:PLATFORM_ADMIN} non ve
   * os datos da veciñanza dun concello (reservas, exportación, indicadores).
   */
  @Test
  void platformDoesNotSeeResidentsData() {
    String base = "/api/v1/municipalities/" + b;
    List<Api.Call> dataCalls =
        List.of(
            api().get(base + "/bookings"),
            api().get(base + "/bookings/summary"),
            api().get(base + "/export"),
            api().get(base + "/indicators"),
            api().get(base + "/indicators/filters"));
    List<String> failures = new ArrayList<>();
    for (Api.Call call : dataCalls) {
      Api.Response response = call.token(TestTokens.platformAdmin()).send();
      if (response.status() != 403
          || !"auth.forbidden".equals(response.json().get("code").asString())) {
        failures.add(
            response.raw().request().method() + " " + response.raw().uri() + " → " + response);
      }
    }
    assertThat(failures).isEmpty();
  }

  /**
   * A plataforma deixa o concello configurado ela soa (ADR-0028, revisión do 2026-11-03): os seus
   * datos, os campos personalizados, as instalacións e o rexistro de actividade.
   */
  @Test
  void platformConfiguresTheMunicipality() {
    String base = "/api/v1/municipalities/" + b;
    String admin = TestTokens.platformAdmin();
    Api.Response updated =
        api().put(base, updateBody("B", Map.of())).ifMatch(0).token(admin).send();
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    Api.Response field =
        api().post(base + "/custom-fields", textField("by_platform", "STAFF")).token(admin).send();
    assertThat(field.status()).as(field.toString()).isEqualTo(201);
    Api.Response archived =
        api()
            .post(base + "/custom-fields/" + fieldB + "/deactivate", null)
            .ifMatch(0)
            .token(admin)
            .send();
    assertThat(archived.status()).as(archived.toString()).isEqualTo(200);
    Api.Response facility =
        api().post(base + "/facilities", Map.of("name", "Pavillón")).token(admin).send();
    assertThat(facility.status()).as(facility.toString()).isEqualTo(201);
    for (String path : List.of("/facilities", "/audit-events")) {
      Api.Response read = api().get(base + path).token(admin).send();
      assertThat(read.status()).as(path + " " + read).isEqualTo(200);
    }
  }

  @Test
  void onlyThePlatformCreatesMunicipalities() {
    Api.Response response =
        api()
            .post(
                "/api/v1/municipalities",
                Map.of(
                    "slug",
                    uniqueSlug("x"),
                    "name",
                    "X",
                    "languages",
                    List.of("gl"),
                    "defaultLanguage",
                    "gl"))
            .token(TestTokens.manager(b))
            .send();
    assertThat(response.status()).isEqualTo(403);
    assertThat(response.json().get("code").asString()).isEqualTo("auth.forbidden");
  }

  @Test
  void ownManagerIsAllowed() {
    Api.Response response =
        api()
            .post("/api/v1/municipalities/" + b + "/custom-fields/" + fieldB + "/deactivate", null)
            .token(TestTokens.manager(b))
            .ifMatch(0)
            .send();
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().get("status").asString()).isEqualTo("INACTIVE");
  }

  /**
   * Persoal (D4-07): o xestor doutro concello, a veciñanza e o persoal STAFF non ven nin xestionan
   * o persoal de B. A plataforma si pode (ver, invitar xestores, desactivar e reactivar): está en
   * {@code StaffManagementIT}.
   */
  @Test
  void staffManagementIsForbiddenOutsideTheMunicipality() {
    String staff = "/api/v1/municipalities/" + b + "/staff";
    String member = staff + "/" + UUID.randomUUID();
    List<String> failures = new ArrayList<>();
    for (String token :
        List.of(TestTokens.manager(a), TestTokens.resident(b), TestTokens.staff(b))) {
      for (Api.Call call :
          List.of(
              api().get(staff),
              api().get(member),
              api().post(staff, Map.of("email", "x@example.org", "role", "MANAGER")),
              api().post(member + "/invitation", null).ifMatch(0),
              api().put(member + "/role", Map.of("role", "MANAGER")).ifMatch(0),
              api().post(member + "/deactivate", null).ifMatch(0),
              api().post(member + "/reactivate", null).ifMatch(0))) {
        Api.Response response = call.token(token).send();
        if (response.status() != 403
            || !"auth.forbidden".equals(response.json().get("code").asString())) {
          failures.add(
              response.raw().request().method() + " " + response.raw().uri() + " → " + response);
        }
      }
    }
    assertThat(failures).isEmpty();
  }

  private void assertForbidden(String token) {
    String base = "/api/v1/municipalities/" + b;
    List<Api.Call> calls =
        List.of(
            api().put(base, updateBody("B", Map.of())).ifMatch(0),
            api().post(base + "/custom-fields", textField("new_field", "PUBLIC")),
            api()
                .put(
                    base + "/custom-fields/" + fieldB,
                    Map.of(
                        "label",
                        Map.of("gl", "x", "es", "x"),
                        "usages",
                        List.of(Map.of("target", "MUNICIPALITY", "visibility", "PUBLIC"))))
                .ifMatch(0),
            api().post(base + "/custom-fields/" + fieldB + "/deactivate", null).ifMatch(0),
            api().post(base + "/custom-fields/" + fieldB + "/activate", null).ifMatch(0),
            api().delete(base + "/custom-fields/" + fieldB).ifMatch(0),
            api().get(base + "/custom-fields/" + fieldB),
            api().get(base + "/custom-fields?target=MUNICIPALITY&includeInactive=true"));
    List<String> failures = new ArrayList<>();
    for (Api.Call call : calls) {
      Api.Response response = call.token(token).send();
      if (response.status() != 403
          || !"auth.forbidden".equals(response.json().get("code").asString())) {
        failures.add(
            response.raw().request().method() + " " + response.raw().uri() + " → " + response);
      }
    }
    assertThat(failures).isEmpty();
    Integer unchanged =
        jdbc.sql(
                "select count(*) from custom_field where id = :id and status = 'ACTIVE' and version = 0")
            .param("id", fieldB)
            .query(Integer.class)
            .single();
    assertThat(unchanged).isEqualTo(1);
  }
}
