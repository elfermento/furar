// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Campos propios da reserva (ADR-0011, revisión do 2026-11-04): quen reserva enche os de «a persoa
 * que reserva e o persoal» antes de confirmar; o caso de uso valídaos na mesma transacción (un
 * obrigatorio que falta, 400 e sen reserva); quen reservou ve os seus e o persoal, todos.
 */
class BookingCustomFieldsIT extends AbstractBookingIT {

  @Test
  void theResidentFillsTheBookingFieldsAndStaffSeesThemAll() {
    Facility f = facility("reserva-campos", true);
    UUID use = whole(f, Map.of());
    String manager = TestTokens.manager(f.slug());
    createField(
        f.slug(),
        bookingField(
            "level",
            "SELECT",
            "OWNER",
            true,
            Map.of(
                "options",
                List.of(
                    Map.of(
                        "code", "start", "label", Map.of("gl", "Iniciación", "es", "Iniciación")),
                    Map.of("code", "mid", "label", Map.of("gl", "Medio", "es", "Medio"))))));
    createField(f.slug(), bookingField("federation_no", "TEXT", "OWNER", false, Map.of()));
    createField(f.slug(), bookingField("staff_note", "TEXT", "STAFF", true, Map.of()));

    // Quen reserva só ve os campos de titular.
    Api.Response visible =
        api()
            .get("/api/v1/municipalities/" + f.slug() + "/custom-fields?target=BOOKING")
            .token(person())
            .send();
    assertThat(visible.status()).isEqualTo(200);
    assertThat(codes(visible.json())).containsExactlyInAnyOrder("level", "federation_no");

    String ana = person();
    Instant nine = at(tomorrow(), OPENS);
    Api.Response missing = reserve(ana, f, use, nine, Map.of("federation_no", "GA-1"));
    assertThat(missing.status()).as(missing.toString()).isEqualTo(400);
    assertThat(missing.json().get("errors").get(0).get("field").asString())
        .isEqualTo("customFields.level");
    assertThat(count("select count(*) from booking where use_id = :k", use)).isZero();

    Api.Response staffOnly = reserve(ana, f, use, nine, Map.of("level", "mid", "staff_note", "x"));
    assertThat(staffOnly.status()).as("un campo do persoal non o escribe").isEqualTo(400);

    Api.Response created =
        reserve(ana, f, use, nine, Map.of("level", "mid", "federation_no", "GA-1"));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    assertThat(created.json().get("customFields").get("level").asString()).isEqualTo("mid");
    UUID booking = UUID.fromString(created.json().get("id").asString());

    JsonNode own = api().get(mine(f.slug()) + "/" + booking).token(ana).send().json();
    assertThat(own.get("customFields").get("federation_no").asString()).isEqualTo("GA-1");

    JsonNode detail =
        api()
            .get(ManageBookingsIT.bookings(f.slug()) + "/" + booking)
            .token(TestTokens.staff(f.slug()))
            .send()
            .json();
    assertThat(detail.get("customFields").get("level").asString()).isEqualTo("mid");
    assertThat(detail.get("customFields").get("federation_no").asString()).isEqualTo("GA-1");

    // O campo xa ten valores: non se elimina nin cambia de sitio.
    JsonNode level =
        api()
            .get(
                "/api/v1/municipalities/"
                    + f.slug()
                    + "/custom-fields?target=BOOKING&includeInactive=true")
            .token(manager)
            .send()
            .json();
    for (JsonNode d : level) {
      if ("level".equals(d.get("code").asString())) {
        assertThat(d.get("hasValues").asBoolean()).isTrue();
        Api.Response refused =
            api()
                .delete("/api/v1/municipalities/" + f.slug() + "/custom-fields/" + id(d))
                .token(manager)
                .ifMatch(d.get("version").asLong())
                .send();
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.json().get("code").asString()).isEqualTo("customfield.has-values");
      }
    }
  }

  private Api.Response reserve(
      String token, Facility f, UUID use, Instant start, Map<String, Object> customFields) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    body.put("customFields", customFields);
    return api()
        .post(mine(f.slug()), body)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .token(token)
        .send();
  }

  private static Map<String, Object> bookingField(
      String code, String type, String visibility, boolean required, Map<String, Object> extra) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("code", code);
    map.put("type", type);
    map.put("label", Map.of("gl", "Campo " + code, "es", "Campo " + code));
    map.put(
        "usages",
        List.of(Map.of("target", "BOOKING", "visibility", visibility, "required", required)));
    map.putAll(extra);
    return map;
  }

  private static List<String> codes(JsonNode list) {
    return java.util.stream.StreamSupport.stream(list.spliterator(), false)
        .map(d -> d.get("code").asString())
        .toList();
  }
}
