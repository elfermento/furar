// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * D50 (ADR-0018, AP2-01): portabilidade dos datos dun concello. Exportar un concello con campos
 * (activos e arquivados, de persoal e dato persoal) e valores; borralo da base como faría outra
 * instalación limpa; importalo e volver exportar → o mesmo ficheiro salvo {@code exportedAt} e as
 * versións de bloqueo optimista. Outro concello non aparece; importar dúas veces é un conflito; un
 * ficheiro mal formado non deixa nada a medias. Un ficheiro v1 gardado (un destino por definición)
 * segue entrando. As instalacións viaxan con todo o seu, menos os bytes das fotos (D1-18, P32).
 */
class MunicipalityPortabilityIT extends AbstractIT {

  private static final Map<String, Object> OPTIONS =
      Map.of(
          "options",
          List.of(
              Map.of("code", "north", "label", Map.of("gl", "Norte", "es", "Norte")),
              Map.of("code", "south", "label", Map.of("gl", "Sur", "es", "Sur"))));

  @Test
  void exportImportExportIsTheSameFile() throws Exception {
    String slug = uniqueSlug("porta");
    String other = uniqueSlug("outro");
    createMunicipality(slug);
    createMunicipality(other);
    createField(other, textField("only_other", "PUBLIC"));

    createField(slug, field("zone", "SELECT", "PUBLIC", OPTIONS));
    createField(slug, field("budget", "NUMBER", "STAFF", Map.of()));
    createField(slug, field("contact_email", "TEXT", "STAFF", Map.of("personalData", true)));
    JsonNode legacy = createField(slug, textField("legacy_code", "PUBLIC"));
    Api.Response updated =
        api()
            .put(
                "/api/v1/municipalities/" + slug,
                updateBody(
                    "Concello exportado",
                    Map.of(
                        "zone", "north",
                        "budget", 1250.5,
                        "contact_email", "deportes@example.gal",
                        "legacy_code", "L-1")))
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    // Os textos propios das páxinas legais viaxan co concello (ADR-0017 punto 8).
    Api.Response texts =
        api()
            .put(
                "/api/v1/municipalities/" + slug + "/legal-texts",
                Map.of(
                    "accessibility",
                    Map.of("gl", "<h2>Declaración</h2><p>Texto propio</p>", "es", "<p>Propio</p>")))
            .token(TestTokens.manager(slug))
            .ifMatch(1)
            .send();
    assertThat(texts.status()).as(texts.toString()).isEqualTo(200);
    Api.Response deactivated =
        api()
            .post(
                "/api/v1/municipalities/" + slug + "/custom-fields/" + id(legacy) + "/deactivate",
                null)
            .token(TestTokens.manager(slug))
            .ifMatch(0)
            .send();
    assertThat(deactivated.status()).as(deactivated.toString()).isEqualTo(200);

    Api.Response first = export(slug, TestTokens.manager(slug));
    assertThat(first.status()).as(first.toString()).isEqualTo(200);
    assertThat(first.header("Content-Disposition"))
        .startsWith("attachment")
        .contains("furar-export-" + slug + "-");
    JsonNode file = first.json();
    assertThat(file.get("format").asString()).isEqualTo("furar-export");
    assertThat(file.get("version").asInt()).isEqualTo(3);
    assertThat(file.get("municipality").get("customFields").get("contact_email").asString())
        .as("o xestor exporta tamén os campos de persoal")
        .isEqualTo("deportes@example.gal");
    assertThat(file.get("municipality").get("legalTexts").get("accessibility").get("gl").asString())
        .isEqualTo("<h2>Declaración</h2><p>Texto propio</p>");
    JsonNode definitions = file.get("sections").get("customfields");
    assertThat(definitions.size()).isEqualTo(4);
    assertThat(definitions.toString()).doesNotContain("only_other");
    assertThat(definitions.toString()).contains("\"status\":\"INACTIVE\"");
    assertThat(definitions.get(0).has("target")).as("v2: usages[] no canto de target").isFalse();
    assertThat(definitions.get(0).get("usages").get(0).get("target").asString())
        .isEqualTo("MUNICIPALITY");

    UUID municipalityId = UUID.fromString(file.get("municipality").get("id").asString());
    deleteAsAdministrator(municipalityId);
    assertThat(api().get("/api/v1/municipalities/" + slug).send().status()).isEqualTo(404);

    Api.Response imported = importFile(file, TestTokens.platformAdmin());
    assertThat(imported.status()).as(imported.toString()).isEqualTo(201);
    assertThat(imported.header("Location")).endsWith("/api/v1/municipalities/" + slug);

    Api.Response second = export(slug, TestTokens.manager(slug));
    assertThat(second.status()).isEqualTo(200);
    assertThat(comparable(second.json())).isEqualTo(comparable(file));

    Api.Response again = importFile(file, TestTokens.platformAdmin());
    assertThat(again.status()).isEqualTo(409);
    assertThat(again.json().get("code").asString()).isEqualTo("portability.conflict");
  }

  @Test
  void onlyTheManagerExportsAndOnlyThePlatformImports() {
    String a = uniqueSlug("porta-a");
    String b = uniqueSlug("porta-b");
    createMunicipality(a);
    createMunicipality(b);

    assertThat(export(a, null).status()).isEqualTo(401);
    assertThat(export(a, TestTokens.manager(b)).status()).isEqualTo(403);
    assertThat(export(a, TestTokens.staff(a)).status()).isEqualTo(403);
    assertThat(export(a, TestTokens.resident(a)).status()).isEqualTo(403);
    // ADR-0007: a plataforma non ve os datos dun concello, tampouco exportándoos.
    Api.Response byPlatform = export(a, TestTokens.platformAdmin());
    assertThat(byPlatform.status()).isEqualTo(403);
    assertThat(byPlatform.json().get("code").asString()).isEqualTo("auth.forbidden");

    Api.Response byOwnManager = export(a, TestTokens.manager(a));
    assertThat(byOwnManager.status()).isEqualTo(200);
    JsonNode file = byOwnManager.json();
    Api.Response byManager = importFile(file, TestTokens.manager(a));
    assertThat(byManager.status()).isEqualTo(403);
    assertThat(byManager.json().get("code").asString()).isEqualTo("auth.forbidden");
  }

  @Test
  void aBrokenFileLeavesNothingBehind() {
    String slug = uniqueSlug("porta-mal");
    createMunicipality(slug);
    createField(slug, textField("note", "PUBLIC"));
    ObjectNode file = (ObjectNode) export(slug, TestTokens.manager(slug)).json();
    UUID id = UUID.fromString(file.get("municipality").get("id").asString());
    deleteAsAdministrator(id);

    ObjectNode wrongFormat = file.deepCopy();
    wrongFormat.put("format", "other");
    Api.Response format = importFile(wrongFormat, TestTokens.platformAdmin());
    assertThat(format.status()).isEqualTo(400);
    assertThat(format.json().get("code").asString()).isEqualTo("validation.failed");

    ObjectNode unknownSection = file.deepCopy();
    ((ObjectNode) unknownSection.get("sections")).putArray("nonexistent");
    Api.Response unknown = importFile(unknownSection, TestTokens.platformAdmin());
    assertThat(unknown.status()).isEqualTo(422);
    assertThat(unknown.json().get("code").asString()).isEqualTo("portability.invalid-file");

    // A definición falla despois de crear o concello: a transacción desfai tamén o concello.
    ObjectNode badDefinition = file.deepCopy();
    ((ObjectNode) badDefinition.get("sections").get("customfields").get(0)).put("code", "X!");
    Api.Response bad = importFile(badDefinition, TestTokens.platformAdmin());
    assertThat(bad.status()).as(bad.toString()).isBetween(400, 499);
    assertThat(api().get("/api/v1/municipalities/" + slug).send().status()).isEqualTo(404);

    // Un valor sen definición tampouco entra.
    ObjectNode unknownValue = file.deepCopy();
    ((ObjectNode) unknownValue.get("municipality").get("customFields")).put("ghost", "x");
    Api.Response ghost = importFile(unknownValue, TestTokens.platformAdmin());
    assertThat(ghost.status()).isEqualTo(400);
    assertThat(ghost.json().get("code").asString()).isEqualTo("validation.failed");
    assertThat(api().get("/api/v1/municipalities/" + slug).send().status()).isEqualTo(404);

    assertThat(importFile(file, TestTokens.platformAdmin()).status()).isEqualTo(201);
  }

  /**
   * ADR-0018, versión 2: un ficheiro v1 gardado antes das táboas tipadas impórtase; cada definición
   * queda cun único uso (o seu destino, obrigatorio e visibilidade) e a orde, o grupo e as
   * condicións ignóranse. A exportación seguinte xa é v2 e fai a ida e volta.
   */
  @Test
  void aSavedFileFromVersionOneIsImported() throws Exception {
    ObjectNode v1 = savedVersion1File();
    String slug = v1.get("municipality").get("slug").asString();

    ObjectNode withoutTarget = v1.deepCopy();
    ((ObjectNode) withoutTarget.get("sections").get("customfields").get(0)).remove("target");
    Api.Response missing = importFile(withoutTarget, TestTokens.platformAdmin());
    assertThat(missing.status()).as(missing.toString()).isEqualTo(422);
    assertThat(missing.json().get("code").asString()).isEqualTo("portability.invalid-file");
    assertThat(api().get("/api/v1/municipalities/" + slug).send().status()).isEqualTo(404);

    ObjectNode future = v1.deepCopy();
    future.put("version", 4);
    assertThat(importFile(future, TestTokens.platformAdmin()).status()).isEqualTo(400);

    Api.Response imported = importFile(v1, TestTokens.platformAdmin());
    assertThat(imported.status()).as(imported.toString()).isEqualTo(201);

    JsonNode v2 = export(slug, TestTokens.manager(slug)).json();
    assertThat(v2.get("version").asInt()).isEqualTo(3);
    assertThat(v2.get("municipality").get("customFields"))
        .isEqualTo(v1.get("municipality").get("customFields"));
    assertThat(v2.get("municipality").get("legal").get("dir3Code").asString())
        .isEqualTo("L01150548");
    JsonNode definitions = v2.get("sections").get("customfields");
    assertThat(definitions.size()).isEqualTo(4);
    for (JsonNode old : v1.get("sections").get("customfields")) {
      JsonNode now = byCode(definitions, old.get("code").asString());
      assertThat(now.get("id")).isEqualTo(old.get("id"));
      assertThat(now.get("usages").size()).isEqualTo(1);
      JsonNode usage = now.get("usages").get(0);
      assertThat(usage.get("target")).isEqualTo(old.get("target"));
      assertThat(usage.get("required")).isEqualTo(old.get("required"));
      assertThat(usage.get("visibility")).isEqualTo(old.get("visibility"));
      // v1/v2 → v3: arquivado pasa a desactivado.
      assertThat(now.get("status").asString())
          .isEqualTo(
              "ARCHIVED".equals(old.get("status").asString())
                  ? "INACTIVE"
                  : old.get("status").asString());
      for (String field :
          List.of("type", "label", "help", "personalData", "createdAt", "updatedAt")) {
        assertThat(value(now, field))
            .as(old.get("code") + "." + field)
            .isEqualTo(value(old, field));
      }
      for (String gone : List.of("target", "required", "visibility", "position", "group")) {
        assertThat(now.has(gone)).as(gone).isFalse();
      }
      assertThat(now.has("appliesWhen")).isFalse();
    }
    assertThat(byCode(definitions, "zone").get("options").size()).isEqualTo(2);
    assertThat(byCode(definitions, "budget").get("rules").get("decimals").asInt()).isEqualTo(2);
    assertThat(byCode(definitions, "contact_email").get("rules").get("format").asString())
        .isEqualTo("EMAIL");

    deleteAsAdministrator(UUID.fromString(v2.get("municipality").get("id").asString()));
    assertThat(importFile(v2, TestTokens.platformAdmin()).status()).isEqualTo(201);
    assertThat(comparable(export(slug, TestTokens.manager(slug)).json())).isEqualTo(comparable(v2));
  }

  /**
   * ADR-0018, sección {@code facility} (D1-18): unha instalación con todo o que colga dela (datos e
   * campo propio, deporte do concello e outro sen usar, servizos, foto sen bytes, uso por partes
   * cunha parte quitada, uso arquivado, tempada coa semana, excepción e bloqueo) sae, entra nunha
   * instalación limpa e volve saír igual. Os bytes da foto non viaxan (P32): a imaxe non se serve.
   */
  @Test
  void facilitiesTravelWithEverythingButPhotoBytes() {
    String slug = uniqueSlug("porta-inst");
    createMunicipality(slug);
    String manager = TestTokens.manager(slug);
    Map<String, Object> parkingField = new java.util.LinkedHashMap<>();
    parkingField.put("code", "parking");
    parkingField.put("type", "TEXT");
    parkingField.put("label", Map.of("gl", "Aparcadoiro", "es", "Aparcamiento"));
    parkingField.put(
        "usages", List.of(Map.of("target", "FACILITY", "visibility", "STAFF", "required", false)));
    createField(slug, parkingField);
    String sports = "/api/v1/municipalities/" + slug + "/sports";
    String bolos =
        ok(api().post(sports, Map.of("gl", "Bolos celtas", "es", "Bolos celtas")).token(manager))
            .get("customSportId")
            .asString();
    ok(api().post(sports, Map.of("gl", "Sen usar", "es", "Sin usar")).token(manager));

    String facilities = "/api/v1/municipalities/" + slug + "/facilities";
    Map<String, Object> address = new java.util.LinkedHashMap<>();
    address.put("street", "Rúa do Alto");
    address.put("streetNumber", "12");
    address.put("postalCode", "15570");
    address.put("locality", "Narón");
    address.put("ineCode", "15054");
    address.put("latitude", 43.5167);
    address.put("longitude", -8.1833);
    JsonNode facility =
        ok(
            api()
                .post(facilities, Map.of("name", "Campo de O Alto", "address", address))
                .token(manager));
    String url = facilities + "/" + id(facility);
    Map<String, Object> details = new java.util.LinkedHashMap<>();
    details.put("sports", List.of(Map.of("code", "football"), Map.of("customSportId", bolos)));
    details.put("type", "FIELD");
    details.put("phone", "981 38 21 00");
    details.put("onlineBooking", true);
    details.put("showFreeSlots", false);
    details.put("covered", false);
    details.put("customFields", Map.of("parking", "Diante da bancada"));
    ok(api().put(url + "/details", details).token(manager).ifMatch(0));
    ok(
        api()
            .put(url + "/amenities", Map.of("amenities", List.of("LIGHTING")))
            .token(manager)
            .ifMatch(1));
    UUID photo = UUID.randomUUID();
    jdbc.sql(
            """
            with p as (
              insert into library_photo (id, municipality_id, alt, width, height, content_type,
                                         created_at)
              select :photo, municipality_id, '{"gl": "Campo", "es": "Campo"}', 1600, 900,
                     'image/jpeg', now()
              from facility where id = :facility
              returning id, municipality_id)
            insert into facility_image (id, municipality_id, facility_id, position, photo_id,
                                        drawing_code)
            select gen_random_uuid(), p.municipality_id, :facility, 0, p.id, null from p
            union all
            select gen_random_uuid(), p.municipality_id, :facility, 1, null, 'football_field' from p
            """)
        .param("photo", photo)
        .param("facility", id(facility))
        .update();
    jdbc.sql(
            """
            insert into library_photo_content (municipality_id, photo_id, variant, bytes)
            select municipality_id, id, v, '\\x01'::bytea from library_photo,
                   (values ('LARGE'), ('THUMB')) as variants (v)
            where id = :photo
            """)
        .param("photo", photo)
        .update();

    Map<String, Object> parts = new java.util.LinkedHashMap<>();
    parts.put("name", "Fútbol 7");
    parts.put("rentalMode", "PARTS");
    parts.put("slotMinutes", 60);
    parts.put("availability", "ALWAYS");
    parts.put("parts", List.of(Map.of("name", "A"), Map.of("name", "B"), Map.of("name", "C")));
    JsonNode use = ok(api().post(url + "/uses", parts).token(manager));
    List<Map<String, Object>> kept = new java.util.ArrayList<>();
    for (int i = 0; i < 2; i++) {
      JsonNode part = use.get("parts").get(i);
      kept.add(Map.of("id", part.get("id").asString(), "name", part.get("name").asString()));
    }
    parts.put("parts", kept);
    // Condicións do uso (D2-02): viaxan no ficheiro co uso.
    parts.put("weekdays", 32 | 64);
    parts.put("price", Map.of("kind", "PAID", "amountCents", 3000));
    parts.put("rules", Map.of("maxSlots", 2, "capCount", 2, "capPeriod", "WEEK"));
    ok(api().put(url + "/uses/" + id(use), parts).token(manager).ifMatch(0));
    ok(
        api()
            .put(url + "/house-rules", Map.of("gl", "Calzado axeitado.", "es", "Calzado adecuado."))
            .token(manager)
            .ifMatch(2));
    Map<String, Object> whole = new java.util.LinkedHashMap<>();
    whole.put("name", "Fútbol 11");
    whole.put("rentalMode", "WHOLE");
    whole.put("slotMinutes", 90);
    whole.put("availability", "ALWAYS");
    JsonNode old = ok(api().post(url + "/uses", whole).token(manager));
    ok(api().post(url + "/uses/" + id(old) + "/archive", Map.of()).token(manager).ifMatch(0));

    JsonNode season =
        ok(
            api()
                .post(
                    url + "/seasons",
                    Map.of("name", "Inverno", "startDate", "2027-01-01", "endDate", "2027-06-30"))
                .token(manager));
    Map<String, Object> week = new java.util.LinkedHashMap<>();
    week.put("name", "Inverno");
    week.put("startDate", "2027-01-01");
    week.put("endDate", "2027-06-30");
    week.put(
        "week",
        List.of(
            Map.of(
                "day",
                "MONDAY",
                "intervals",
                List.of(Map.of("startMinute", 480, "endMinute", 1320)))));
    ok(api().put(url + "/seasons/" + id(season), week).token(manager).ifMatch(0));
    // Unha tempada sen fin (maqueta 54) viaxa sen «endDate».
    Map<String, Object> open = new java.util.LinkedHashMap<>();
    open.put("name", "Desde o verán");
    open.put("startDate", "2027-07-01");
    open.put("endDate", null);
    ok(api().post(url + "/seasons", open).token(manager));
    ok(
        api()
            .post(
                url + "/schedule-exceptions",
                Map.of("date", "2027-03-02", "open", false, "reason", "Obras"))
            .token(manager));
    ok(
        api()
            .post(
                url + "/blocks",
                Map.of(
                    "date", "2027-03-09", "startMinute", 600, "endMinute", 720, "reason", "Rega"))
            .token(manager));

    JsonNode file = export(slug, manager).json();
    JsonNode row = file.get("sections").get("facility").get(0);
    assertThat(row.get("details").get("customFields").get("parking").asString())
        .isEqualTo("Diante da bancada");
    assertThat(row.get("uses").size()).isEqualTo(2);
    assertThat(row.get("houseRules").get("es").asString()).isEqualTo("Calzado adecuado.");
    JsonNode terms = row.get("uses").get(0);
    assertThat(terms.get("weekdays").asInt()).isEqualTo(96);
    assertThat(terms.get("price").get("amountCents").asInt()).isEqualTo(3000);
    assertThat(terms.get("rules").get("capPeriod").asString()).isEqualTo("WEEK");
    assertThat(row.get("archivedParts").size()).isEqualTo(1);
    assertThat(row.get("images").get(0).get("photoId").asString()).isEqualTo(photo.toString());
    assertThat(row.get("images").get(1).get("drawing").asString()).isEqualTo("football_field");
    assertThat(row.get("images").toString()).as("sen bytes (P32)").doesNotContain("bytes");
    assertThat(row.get("seasons").get(0).get("week").toString()).contains("1320");
    assertThat(row.get("seasons").get(1).path("endDate").asString(null)).isNull();
    assertThat(row.get("exceptions").size()).isEqualTo(1);
    assertThat(row.get("blocks").get(0).get("createdBy").asString()).isNotBlank();
    assertThat(file.get("sections").get("facility-sports").size())
        .as("tamén o deporte que non usa ninguén")
        .isEqualTo(2);

    deleteAsAdministrator(UUID.fromString(file.get("municipality").get("id").asString()));
    Api.Response imported = importFile(file, TestTokens.platformAdmin());
    assertThat(imported.status()).as(imported.toString()).isEqualTo(201);

    // A pertenza non viaxa no ficheiro: a persoa xestora do concello importado é outra.
    JsonNode again = export(slug, TestTokens.manager(slug)).json();
    assertThat(withoutVersions(again)).isEqualTo(withoutVersions(file));
    Api.Response image =
        api().get("/api/v1/municipalities/" + slug + "/photos/" + photo + "/large").send();
    assertThat(image.status()).as("os bytes van na copia da base").isEqualTo(404);
  }

  private JsonNode ok(Api.Call call) {
    Api.Response response = call.send();
    assertThat(response.status()).as(response.toString()).isBetween(200, 201);
    return response.json();
  }

  /** Sen {@code exportedAt} nin ningunha versión de bloqueo optimista, tampouco as aniñadas. */
  private static JsonNode withoutVersions(JsonNode file) {
    ObjectNode copy = ((ObjectNode) file).deepCopy();
    copy.remove("exportedAt");
    stripVersions(copy.get("sections"));
    return copy;
  }

  private static void stripVersions(JsonNode node) {
    if (node instanceof ObjectNode object) {
      object.remove("version");
    }
    node.forEach(MunicipalityPortabilityIT::stripVersions);
  }

  /**
   * SEC3-02, AP3-02 (ADR-0020): cada exportación e importación, boa ou fallida, deixa unha fila en
   * {@code audit_event} con quen, que concello, resultado e filas por sección.
   */
  @Test
  void exportAndImportLeaveAnAuditTrail() {
    String slug = uniqueSlug("porta-audit");
    createMunicipality(slug);
    createField(slug, textField("note", "PUBLIC"));
    JsonNode file = export(slug, TestTokens.manager(slug)).json();
    UUID id = UUID.fromString(file.get("municipality").get("id").asString());

    var exported =
        jdbc.sql(
                """
                select actor_subject, outcome, municipality_slug, request_id,
                       details -> 'rows' ->> 'customfields' as definitions
                from audit_event where action = 'municipality.export' and municipality_id = :id
                """)
            .param("id", id)
            .query()
            .singleRow();
    assertThat(exported.get("actor_subject").toString()).startsWith("test-");
    assertThat(exported.get("outcome")).isEqualTo("SUCCESS");
    assertThat(exported.get("municipality_slug")).isEqualTo(slug);
    assertThat(exported.get("request_id")).isNotNull();
    assertThat(exported.get("definitions")).isEqualTo("1");

    Api.Response conflict = importFile(file, TestTokens.platformAdmin());
    assertThat(conflict.status()).isEqualTo(409);
    assertThat(
            jdbc.sql(
                    """
                    select details ->> 'code' from audit_event
                    where action = 'municipality.import' and municipality_id = :id
                      and outcome = 'FAILURE'
                    """)
                .param("id", id)
                .query(String.class)
                .single())
        .isEqualTo("portability.conflict");
  }

  private Api.Response export(String slug, String token) {
    Api.Call call = api().get("/api/v1/municipalities/" + slug + "/export");
    return (token == null ? call : call.token(token)).send();
  }

  private Api.Response importFile(JsonNode file, String token) {
    return api().post("/api/v1/municipality-imports", file).token(token).send();
  }

  /** O ficheiro v1 gardado: catro definicións dun só destino (unha arquivada) e os seus valores. */
  private static ObjectNode savedVersion1File() throws java.io.IOException {
    try (InputStream in =
        MunicipalityPortabilityIT.class.getResourceAsStream("/portability/furar-export-v1.json")) {
      return (ObjectNode) Api.JSON.readTree(in);
    }
  }

  /** Un nulo e un campo ausente son o mesmo dato. */
  private static JsonNode value(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value;
  }

  private static JsonNode byCode(JsonNode definitions, String code) {
    for (JsonNode definition : definitions) {
      if (definition.get("code").asString().equals(code)) {
        return definition;
      }
    }
    throw new AssertionError("Sen definición " + code + ": " + definitions);
  }

  /** Sen {@code exportedAt} nin as versións (bloqueo optimista desta instalación, non datos). */
  private static JsonNode comparable(JsonNode file) {
    ObjectNode copy = ((ObjectNode) file).deepCopy();
    copy.remove("exportedAt");
    copy.get("sections").forEach(rows -> rows.forEach(row -> ((ObjectNode) row).remove("version")));
    return copy;
  }

  /** Como unha instalación limpa: os datos do concello desaparecen da base (superusuario). */
  private static void deleteAsAdministrator(UUID municipalityId) {
    try (Connection admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      for (String sql :
          List.of(
              "delete from block where municipality_id = ?",
              "delete from schedule_exception where municipality_id = ?",
              "delete from season_hours where municipality_id = ?",
              "delete from season where municipality_id = ?",
              "delete from facility_use_period where municipality_id = ?",
              "delete from facility_use_part where municipality_id = ?",
              "delete from facility_use where municipality_id = ?",
              "delete from facility_image where municipality_id = ?",
              "delete from library_photo_content where municipality_id = ?",
              "delete from library_photo where municipality_id = ?",
              "delete from facility_amenity where municipality_id = ?",
              "delete from facility_sport where municipality_id = ?",
              "delete from custom_field_value where municipality_id = ?",
              "delete from facility where municipality_id = ?",
              "delete from custom_sport where municipality_id = ?",
              "delete from custom_field_usage where municipality_id = ?",
              "delete from custom_field_option where municipality_id = ?",
              "delete from custom_field where municipality_id = ?",
              // O persoal non viaxa no ficheiro (ADR-0018): as pertenzas dos tokens de proba.
              "delete from staff_invitation where municipality_id = ?",
              "delete from staff_member where municipality_id = ?",
              "delete from municipality where id = ?")) {
        try (PreparedStatement statement = admin.prepareStatement(sql)) {
          statement.setObject(1, municipalityId);
          statement.executeUpdate();
        }
      }
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
