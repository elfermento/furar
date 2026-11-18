// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Que ve cada quen (ADR-0040, punto 9): sen sesión só as abertas, sen ocultar, sen empezar e coa
 * función acesa, co nome de quen organiza e ningún outro e sen indexar; dentro, os nomes; a xestión
 * oculta (404 en público, aviso dentro, rexistro de actividade sen textos) e ve os números no
 * detalle; a portada común lista os concellos coa función acesa.
 */
class OpenMatchVisibilityIT extends AbstractOpenMatchIT {

  record Ready(Facility f, String organizer, UUID booking, UUID match) {}

  Ready ready(String prefix) {
    Facility f = facility(prefix, true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID booking = booked(ana, f, use, at(later(), 600));
    return new Ready(f, ana, booking, opened(ana, f, booking, 2, 4, 10, false));
  }

  @Test
  void withoutSessionOnlyTheOrganizerNameAndNoIndexing() {
    Ready r = ready("partida-publica");
    String xan = "xan-" + UUID.randomUUID();
    assertThat(enter(person(xan), r.f(), r.match()).status()).isEqualTo(201);

    Api.Response list = api().get(publicMatches(r.f().slug())).send();
    Api.Response page = publicPage(r.f(), r.match());

    assertThat(list.header("X-Robots-Tag")).isEqualTo("noindex");
    assertThat(page.header("X-Robots-Tag")).isEqualTo("noindex");
    JsonNode card = list.json().get("items").get(0);
    assertThat(card.get("id").asString()).isEqualTo(r.match().toString());
    assertThat(card.get("total").asInt()).isEqualTo(3);
    assertThat(card.get("organizerName").asString()).startsWith("Persoa ");
    assertThat(card.has("players")).isFalse();
    assertThat(page.status()).isEqualTo(200);
    assertThat(page.json().get("description").asString()).isEqualTo("Ven xogar <b>con nós</b>");
    assertThat(list.body() + page.body()).doesNotContain(xan).doesNotContain("@example.org");
    // Por instalación.
    assertThat(
            api()
                .get(publicMatches(r.f().slug()) + "?facilityId=" + UUID.randomUUID())
                .send()
                .json()
                .get("items"))
        .isEmpty();

    // Dentro, os nomes; fóra, ningún.
    JsonNode inside = mine(person(xan), r.f(), r.match()).json();
    assertThat(inside.get("players")).hasSize(1);
    assertThat(inside.get("players").get(0).get("name").asString()).isEqualTo("Persoa " + xan);
    JsonNode outside = mine(person(), r.f(), r.match()).json();
    assertThat(outside.get("me").get("role").asString()).isEqualTo("NONE");
    assertThat(outside.get("players")).isEmpty();
    assertThat(outside.has("bookingId") && !outside.get("bookingId").isNull()).isFalse();
  }

  @Test
  void aFullMatchLeavesTheListsButItsPageStays() {
    Facility f = facility("partida-completa", true);
    openMatches(f, true, 12);
    UUID use = whole(f, Map.of());
    String ana = person();
    UUID booking = booked(ana, f, use, at(later(), 600));
    UUID match = opened(ana, f, booking, 2, 3, 3, false);
    assertThat(enter(person(), f, match).status()).isEqualTo(201);

    assertThat(publicList(f)).isEmpty();
    assertThat(publicPage(f, match).json().get("total").asInt()).isEqualTo(3);
  }

  @Test
  void theManagerHidesItWithoutTextsInTheActivityLog() {
    Ready r = ready("partida-oculta");
    String xan = person();
    assertThat(enter(xan, r.f(), r.match()).status()).isEqualTo(201);

    Api.Response hidden = hide(r.f(), r.booking(), true);

    assertThat(hidden.status()).as(hidden.toString()).isEqualTo(200);
    assertThat(hidden.json().get("hidden").asBoolean()).isTrue();
    assertThat(hidden.json().get("total").asInt()).isEqualTo(3);
    assertThat(publicPage(r.f(), r.match()).status()).isEqualTo(404);
    assertThat(publicList(r.f())).isEmpty();
    assertThat(mine(xan, r.f(), r.match()).json().get("hidden").asBoolean()).isTrue();
    assertThat(mine(r.organizer(), r.f(), r.match()).json().get("hidden").asBoolean()).isTrue();
    assertThat(hide(r.f(), r.booking(), true).status())
        .as("repetir non cambia nada")
        .isEqualTo(200);
    assertThat(audit(r.f().municipality(), "booking.open-match-hide")).containsExactly("SUCCESS ");
    String details =
        jdbc.sql(
                "select details::text from audit_event where municipality_id = :m and action ="
                    + " 'booking.open-match-hide'")
            .param("m", r.f().municipality())
            .query(String.class)
            .single();
    assertThat(details).contains(r.match().toString()).doesNotContain("Pachanga", "Persoa");

    // O detalle da reserva para o persoal leva os números, sen nomes.
    JsonNode detail = detail(TestTokens.staff(r.f().slug()), r.f().slug(), r.booking());
    assertThat(detail.get("openMatch").get("hidden").asBoolean()).isTrue();
    assertThat(detail.get("openMatch").get("total").asInt()).isEqualTo(3);
    // Ocultar é da xestión: Recepción non.
    assertThat(
            api()
                .put(
                    bookings(r.f().slug()) + "/" + r.booking() + "/open-match/hidden",
                    Map.of("hidden", false))
                .token(TestTokens.staff(r.f().slug()))
                .send()
                .status())
        .isEqualTo(403);

    assertThat(hide(r.f(), r.booking(), false).status()).isEqualTo(200);
    assertThat(publicPage(r.f(), r.match()).status()).isEqualTo(200);
    assertThat(audit(r.f().municipality(), "booking.open-match-show")).containsExactly("SUCCESS ");
  }

  @Test
  void withTheFunctionOffNothingIsSeenNorJoinedButNothingIsCancelled() {
    Ready r = ready("partida-sen-funcion");
    openMatches(r.f(), false, 12);

    assertThat(publicList(r.f())).isEmpty();
    assertThat(publicPage(r.f(), r.match()).status()).isEqualTo(404);
    assertThat(problem(enter(person(), r.f(), r.match()))).isEqualTo("404 booking.matches-off");
    assertThat(matchState(r.match())).isEqualTo("OPEN -");
    assertThat(mine(r.organizer(), r.f(), r.match()).status()).isEqualTo(200);
  }

  @Test
  void theCommonLandingListsTheMunicipalitiesWithTheFunctionOn() {
    Ready on = ready("partida-catalogo");
    Ready off = ready("partida-catalogo-off");
    for (Ready r : List.of(on, off)) {
      jdbc.sql(
              "update municipality set legal_entity_name = 'Concello de Proba', nif = 'P1505400J',"
                  + " address = 'Praza de Galicia, 1', general_email = 'concello@exemplo.example.org',"
                  + " dpo_email = 'dpd@exemplo.example.org' where id = :id")
          .param("id", r.f().municipality())
          .update();
    }
    openMatches(off.f(), false, 12);

    List<String> slugs = new ArrayList<>();
    for (int page = 0; ; page++) {
      Api.Response response = api().get("/api/v1/catalog/open-matches?size=50&page=" + page).send();
      assertThat(response.status()).as(response.toString()).isEqualTo(200);
      assertThat(response.header("X-Robots-Tag")).isEqualTo("noindex");
      response
          .json()
          .get("municipalities")
          .forEach(
              m -> {
                slugs.add(m.get("slug").asString());
                if (m.get("slug").asString().equals(on.f().slug())) {
                  assertThat(m.get("items").get(0).get("id").asString())
                      .isEqualTo(on.match().toString());
                }
              });
      if (!response.json().get("hasNext").asBoolean()) {
        break;
      }
    }
    assertThat(slugs).contains(on.f().slug()).doesNotContain(off.f().slug());
  }
}
