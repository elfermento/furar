// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Portada común de punta a punta (ADR-0030): sen sesión nin concello no URL, as instalacións e os
 * ocos de todos os concellos. Un concello sae só cos datos legais completos e algunha instalación
 * activa á vista, e deixa de saír cando deixa de cumprilo; cada concello trae só o seu.
 */
class CatalogIT extends AbstractBookingIT {

  static final String FACILITIES = "/api/v1/catalog/facilities";
  static final String AVAILABILITY = "/api/v1/catalog/availability";

  /** Todas as páxinas do catálogo: concello (slug) → a súa entrada. */
  Map<String, JsonNode> catalog() {
    Map<String, JsonNode> bySlug = new HashMap<>();
    for (int page = 0; ; page++) {
      Api.Response response = api().get(FACILITIES + "?size=50&page=" + page).send();
      assertThat(response.status()).as(response.toString()).isEqualTo(200);
      response.json().get("items").forEach(m -> bySlug.put(m.get("slug").asString(), m));
      if (!response.json().get("hasNext").asBoolean()) {
        return bySlug;
      }
    }
  }

  /** Todas as páxinas dos ocos dun día: instalación → os seus ocos. */
  Map<UUID, JsonNode> availability(LocalDate date) {
    Map<UUID, JsonNode> byFacility = new HashMap<>();
    for (int page = 0; ; page++) {
      Api.Response response =
          api().get(AVAILABILITY + "?size=50&date=" + date + "&page=" + page).send();
      assertThat(response.status()).as(response.toString()).isEqualTo(200);
      response
          .json()
          .get("items")
          .forEach(i -> byFacility.put(UUID.fromString(i.get("facilityId").asString()), i));
      if (!response.json().get("hasNext").asBoolean()) {
        return byFacility;
      }
    }
  }

  static List<UUID> ids(JsonNode municipality) {
    List<UUID> ids = new ArrayList<>();
    municipality.get("facilities").forEach(f -> ids.add(id(f)));
    return ids;
  }

  /** Os cinco datos que usan os textos legais por defecto, de golpe (como dono da base). */
  void completeLegalDetails(UUID municipality) {
    jdbc.sql(
            "update municipality set legal_entity_name = 'Concello de Proba', nif = 'P1505400J',"
                + " address = 'Praza de Galicia, 1', general_email = 'concello@exemplo.example.org',"
                + " dpo_email = 'dpd@exemplo.example.org' where id = :id")
        .param("id", municipality)
        .update();
  }

  void status(Facility f, String status) {
    Api.Response changed =
        api()
            .put(facilities(f.slug()) + "/" + f.id() + "/status", Map.of("status", status))
            .ifMatch(version(f.slug(), f.id()))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(changed.status()).as(changed.toString()).isEqualTo(200);
  }

  @Test
  void aMunicipalityIsListedOnlyWhileItHasItsLegalDetailsAndAnActiveVisibleFacility() {
    Facility hall = facility("portada-a", true);
    whole(hall, Map.of());
    LocalDate day = tomorrow();

    assertThat(catalog())
        .as("published facility but incomplete legal details: not listed")
        .doesNotContainKey(hall.slug());
    assertThat(availability(day)).doesNotContainKey(hall.id());

    completeLegalDetails(hall.municipality());

    JsonNode listed = catalog().get(hall.slug());
    assertThat(listed).as("legal details and a published facility: listed").isNotNull();
    assertThat(listed.get("name").asString()).isEqualTo("Concello " + hall.slug());
    assertThat(ids(listed)).containsExactly(hall.id());
    JsonNode card = listed.get("facilities").get(0);
    assertThat(card.get("name").asString()).isEqualTo("Pavillón do Alto");
    assertThat(card.get("address").get("street").asString()).isEqualTo("Rúa do Alto");
    assertThat(card.get("cover").get("thumb").asString())
        .as("the image route already carries its own municipality")
        .startsWith("/api/v1/municipalities/" + hall.slug() + "/");
    JsonNode free = availability(day).get(hall.id());
    assertThat(free.get("freeCount").asInt()).as("09:00 to 14:00 in one-hour slots").isEqualTo(5);
    assertThat(free.get("firstStarts").size()).isEqualTo(3);
    assertThat(free.get("firstStarts").get(0).get("startMinute").asInt()).isEqualTo(OPENS);

    status(hall, "TEMPORARILY_CLOSED");
    assertThat(catalog())
        .as("nothing active to see: it leaves the catalogue by itself")
        .doesNotContainKey(hall.slug());
    status(hall, "ACTIVE");
    assertThat(catalog()).containsKey(hall.slug());

    jdbc.sql("update municipality set dpo_email = null where id = :id")
        .param("id", hall.municipality())
        .update();
    assertThat(catalog())
        .as("a legal detail is removed: it leaves the catalogue by itself")
        .doesNotContainKey(hall.slug());
    assertThat(availability(day)).doesNotContainKey(hall.id());
  }

  @Test
  void legalDetailsAloneOrADraftAreNotEnough() {
    String slug = uniqueSlug("portada-b");
    UUID municipality = id(createMunicipality(slug));
    completeLegalDetails(municipality);

    assertThat(catalog()).as("no facility").doesNotContainKey(slug);

    Facility draft = facility(slug, municipality, "Pista sen usos", true);

    assertThat(catalog()).as("only a draft").doesNotContainKey(slug);
    assertThat(availability(tomorrow())).doesNotContainKey(draft.id());
  }

  @Test
  void eachMunicipalityBringsOnlyItsOwnFacilitiesAndNoSessionIsNeeded() {
    Facility first = facility("portada-c", true);
    whole(first, Map.of());
    Facility second = facility(first.slug(), first.municipality(), "Campo da Feira", false);
    whole(second, Map.of());
    Facility other = facility("portada-d", true);
    whole(other, Map.of());
    completeLegalDetails(first.municipality());
    completeLegalDetails(other.municipality());

    Map<String, JsonNode> catalog = catalog();
    Map<UUID, JsonNode> free = availability(tomorrow());

    assertThat(ids(catalog.get(first.slug())))
        .as("in the alphabetical order of its own list")
        .containsExactly(second.id(), first.id());
    assertThat(ids(catalog.get(other.slug()))).containsExactly(other.id());
    assertThat(free).containsKeys(first.id(), other.id());
    assertThat(free)
        .as("without online booking there are no slots to show")
        .doesNotContainKey(second.id());
    JsonNode ownList =
        api().get("/api/v1/municipalities/" + first.slug() + "/public/facilities").send().json();
    assertThat(catalog.get(first.slug()).get("facilities"))
        .as("exactly what its own public list gives")
        .isEqualTo(ownList);
  }

  @Test
  void pagesAreBounded() {
    assertThat(api().get(FACILITIES + "?size=51").send().status()).isEqualTo(400);
    assertThat(api().get(FACILITIES + "?page=-1").send().status()).isEqualTo(400);
    assertThat(api().get(AVAILABILITY + "?size=51").send().status()).isEqualTo(400);
    assertThat(api().get(AVAILABILITY + "?date=mañá").send().status()).isEqualTo(400);
  }
}
