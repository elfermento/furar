// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Especificación 1b: edición dos usos e das opcións nas táboas tipadas (orde dos cambios de dato
 * persoal fronte á visibilidade, opcións novas e desactivadas) e listas en orde alfabética no
 * idioma de quen mira. Revisión do 2026-11-04: un campo, un sitio; eliminar só sen valores (o campo
 * e as opcións); desactivar e activar; a reserva sen campos públicos.
 */
class CustomFieldsUsagesIT extends AbstractIT {

  @Test
  void personalDataAndVisibilityChangeTogetherInBothDirections() {
    String slug = uniqueSlug("usage");
    createMunicipality(slug);
    JsonNode field = createField(slug, textField("contact", "PUBLIC"));

    // Marcar dato persoal e deixar de ser público nun só gardado.
    JsonNode personal = put(slug, field, 0, true, "STAFF", List.of());
    assertThat(personal.get("personalData").asBoolean()).isTrue();
    assertThat(personal.get("usages").get(0).get("visibility").asString()).isEqualTo("STAFF");
    assertThat(personal.get("version").asLong()).isEqualTo(1);

    // E ao revés: desmarcar e volver a público.
    JsonNode open = put(slug, field, 1, false, "PUBLIC", List.of());
    assertThat(open.get("personalData").asBoolean()).isFalse();
    assertThat(open.get("usages").get(0).get("visibility").asString()).isEqualTo("PUBLIC");

    // Dato persoal público: 422 no dominio, antes da base.
    Api.Response refused = send(slug, field, 2, true, "PUBLIC", List.of());
    assertThat(refused.status()).isEqualTo(422);
    assertThat(refused.json().get("code").asString()).isEqualTo("customfield.personal-data-public");
  }

  @Test
  void optionsAreAddedAndDeactivatedAndListsAreAlphabetical() {
    String slug = uniqueSlug("opts");
    createMunicipality(slug);
    JsonNode zone =
        createField(
            slug,
            field(
                "zone",
                "SELECT",
                "PUBLIC",
                Map.of(
                    "label",
                    Map.of("gl", "Ánimo", "es", "Zumo"),
                    "options",
                    List.of(option("north", "Norte", "Norte"), option("south", "Sur", "Sur")))));
    createField(
        slug,
        field("bench", "TEXT", "PUBLIC", Map.of("label", Map.of("gl", "Banco", "es", "Asiento"))));

    List<Map<String, Object>> options = new ArrayList<>();
    options.add(
        Map.of("code", "north", "label", Map.of("gl", "Norte", "es", "Norte"), "inactive", true));
    options.add(option("south", "Sur", "Sur"));
    options.add(option("east", "Leste", "Este"));
    JsonNode updated = put(slug, zone, 0, false, "PUBLIC", options);
    List<String> gl = new ArrayList<>();
    updated.get("options").forEach(o -> gl.add(o.get("code").asString() + ":" + o.get("inactive")));
    // Orde alfabética da etiqueta en galego: Leste, Norte, Sur.
    assertThat(gl).containsExactly("east:false", "north:true", "south:false");

    assertThat(codes(slug, "gl")).containsExactly("zone", "bench");
    assertThat(codes(slug, "es")).containsExactly("bench", "zone");
  }

  /** Eliminar só sen valores: o campo, 409 {@code customfield.has-values}; a opción, igual. */
  @Test
  void fieldsAndOptionsAreDeletedOnlyWithoutValues() {
    String slug = uniqueSlug("borra");
    createMunicipality(slug);
    JsonNode zone =
        createField(
            slug,
            field(
                "zone",
                "SELECT",
                "PUBLIC",
                Map.of(
                    "options",
                    List.of(option("north", "Norte", "Norte"), option("south", "Sur", "Sur")))));
    JsonNode spare = createField(slug, textField("spare", "PUBLIC"));
    assertThat(zone.get("hasValues").asBoolean()).isFalse();

    // Sen valores: 204 e xa non está.
    Api.Response deleted =
        api()
            .delete("/api/v1/municipalities/" + slug + "/custom-fields/" + id(spare))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(deleted.status()).as(deleted.toString()).isEqualTo(204);
    assertThat(
            api()
                .get("/api/v1/municipalities/" + slug + "/custom-fields/" + id(spare))
                .token(TestTokens.manager(slug))
                .send()
                .status())
        .isEqualTo(404);

    // Con valores: o concello escolle «south».
    Api.Response saved =
        api()
            .put(
                "/api/v1/municipalities/" + slug,
                updateBody("Concello " + slug, Map.of("zone", "south")))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    JsonNode used =
        api()
            .get("/api/v1/municipalities/" + slug + "/custom-fields/" + id(zone))
            .token(TestTokens.manager(slug))
            .send()
            .json();
    assertThat(used.get("hasValues").asBoolean()).isTrue();
    List<String> optionUse = new ArrayList<>();
    used.get("options")
        .forEach(o -> optionUse.add(o.get("code").asString() + ":" + o.get("hasValues")));
    assertThat(optionUse).containsExactlyInAnyOrder("north:false", "south:true");

    Api.Response refused =
        api()
            .delete("/api/v1/municipalities/" + slug + "/custom-fields/" + id(zone))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(refused.status()).as(refused.toString()).isEqualTo(409);
    assertThat(refused.json().get("code").asString()).isEqualTo("customfield.has-values");

    // A opción con valores non se quita; a outra, si.
    Api.Response optionRefused =
        send(slug, zone, 0, false, "PUBLIC", List.of(option("north", "Norte", "Norte")));
    assertThat(optionRefused.status()).as(optionRefused.toString()).isEqualTo(409);
    assertThat(optionRefused.json().get("code").asString())
        .isEqualTo("customfield.option-has-values");
    JsonNode onlySouth =
        put(slug, zone, 0, false, "PUBLIC", List.of(option("south", "Sur", "Sur")));
    assertThat(onlySouth.get("options").size()).isEqualTo(1);

    // Desactivar e activar: os valores quedan.
    Api.Response inactive =
        api()
            .post(
                "/api/v1/municipalities/" + slug + "/custom-fields/" + id(zone) + "/deactivate",
                null)
            .token(TestTokens.manager(slug))
            .ifMatch(1)
            .send();
    assertThat(inactive.status()).as(inactive.toString()).isEqualTo(200);
    assertThat(inactive.json().get("status").asString()).isEqualTo("INACTIVE");
    // Na lista pública segue (para ler os valores gardados), pero desactivado.
    JsonNode listed =
        api()
            .get("/api/v1/municipalities/" + slug + "/custom-fields?target=MUNICIPALITY")
            .send()
            .json();
    for (JsonNode d : listed) {
      if ("zone".equals(d.get("code").asString())) {
        assertThat(d.get("status").asString()).isEqualTo("INACTIVE");
      }
    }
    Api.Response active =
        api()
            .post(
                "/api/v1/municipalities/" + slug + "/custom-fields/" + id(zone) + "/activate", null)
            .token(TestTokens.manager(slug))
            .ifMatch(2)
            .send();
    assertThat(active.json().get("status").asString()).isEqualTo("ACTIVE");
    assertThat(active.json().get("hasValues").asBoolean()).isTrue();

    // ADR-0022 punto 14: cada escritura dos campos queda no rexistro, tamén as rexeitadas.
    List<String> audited = new ArrayList<>();
    jdbc.sql("select action, outcome from audit_event where municipality_slug = :s")
        .param("s", slug)
        .query()
        .listOfRows()
        .forEach(r -> audited.add(r.get("action") + " " + r.get("outcome")));
    assertThat(audited)
        .contains(
            "customfield.create SUCCESS",
            "customfield.delete SUCCESS",
            "customfield.delete FAILURE",
            "customfield.update SUCCESS",
            "customfield.update FAILURE",
            "customfield.deactivate SUCCESS",
            "customfield.activate SUCCESS",
            "municipality.update SUCCESS");
  }

  /** Un campo, un sitio: o sitio cambia só sen valores; a reserva non ten campos públicos. */
  @Test
  void oneFieldOnePlaceAndBookingFieldsAreNotPublic() {
    String slug = uniqueSlug("sitio");
    createMunicipality(slug);
    Map<String, Object> twoPlaces = new java.util.LinkedHashMap<>(textField("twice", "STAFF"));
    twoPlaces.put(
        "usages",
        List.of(
            Map.of("target", "MUNICIPALITY", "visibility", "STAFF"),
            Map.of("target", "FACILITY", "visibility", "STAFF")));
    Api.Response two =
        api()
            .post("/api/v1/municipalities/" + slug + "/custom-fields", twoPlaces)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(two.status()).as(two.toString()).isEqualTo(400);

    Map<String, Object> publicBooking = new java.util.LinkedHashMap<>(textField("level", "PUBLIC"));
    publicBooking.put("usages", List.of(Map.of("target", "BOOKING", "visibility", "PUBLIC")));
    Api.Response refused =
        api()
            .post("/api/v1/municipalities/" + slug + "/custom-fields", publicBooking)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(refused.status()).as(refused.toString()).isEqualTo(422);
    assertThat(refused.json().get("code").asString())
        .isEqualTo("customfield.visibility-not-allowed");

    JsonNode web = createField(slug, textField("web", "PUBLIC"));
    Api.Response saved =
        api()
            .put(
                "/api/v1/municipalities/" + slug,
                updateBody("Concello " + slug, Map.of("web", "https://example.gal")))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    Map<String, Object> toFacility = new java.util.LinkedHashMap<>();
    toFacility.put("label", Map.of("gl", "Web", "es", "Web"));
    toFacility.put("usages", List.of(Map.of("target", "FACILITY", "visibility", "PUBLIC")));
    Api.Response locked =
        api()
            .put("/api/v1/municipalities/" + slug + "/custom-fields/" + id(web), toFacility)
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(locked.status()).as(locked.toString()).isEqualTo(409);
    assertThat(locked.json().get("code").asString()).isEqualTo("customfield.target-has-values");

    JsonNode free = createField(slug, textField("free", "STAFF"));
    toFacility.put("label", Map.of("gl", "Libre", "es", "Libre"));
    Api.Response moved =
        api()
            .put("/api/v1/municipalities/" + slug + "/custom-fields/" + id(free), toFacility)
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(moved.status()).as(moved.toString()).isEqualTo(200);
    assertThat(moved.json().get("usages").get(0).get("target").asString()).isEqualTo("FACILITY");
  }

  private List<String> codes(String slug, String language) {
    Api.Response list =
        api()
            .get("/api/v1/municipalities/" + slug + "/custom-fields?target=MUNICIPALITY")
            .header("Accept-Language", language)
            .send();
    assertThat(list.status()).isEqualTo(200);
    List<String> codes = new ArrayList<>();
    list.json().forEach(d -> codes.add(d.get("code").asString()));
    return codes;
  }

  private static Map<String, Object> option(String code, String gl, String es) {
    return Map.of("code", code, "label", Map.of("gl", gl, "es", es));
  }

  private JsonNode put(
      String slug,
      JsonNode field,
      long version,
      boolean personalData,
      String visibility,
      List<Map<String, Object>> options) {
    Api.Response response = send(slug, field, version, personalData, visibility, options);
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return response.json();
  }

  private Api.Response send(
      String slug,
      JsonNode field,
      long version,
      boolean personalData,
      String visibility,
      List<Map<String, Object>> options) {
    Map<String, Object> body =
        Map.of(
            "label",
            Map.of(
                "gl",
                field.get("label").get("gl").asString(),
                "es",
                field.get("label").get("es").asString()),
            "personalData",
            personalData,
            "options",
            options,
            "usages",
            List.of(Map.of("target", "MUNICIPALITY", "visibility", visibility)));
    return api()
        .put("/api/v1/municipalities/" + slug + "/custom-fields/" + id(field), body)
        .token(TestTokens.manager(slug))
        .ifMatch(version)
        .send();
  }
}
