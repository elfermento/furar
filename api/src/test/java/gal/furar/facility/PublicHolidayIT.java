// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Festivos de Galicia (D1-15a; plan §1.2.12): a lista sementada é exactamente a publicada, a API
 * non a pode cambiar e lese sen sesión.
 */
class PublicHolidayIT extends AbstractIT {

  /**
   * 2026, copiado a man das fontes oficiais: Resolución do 17 de outubro de 2025 da Dirección Xeral
   * de Traballo (BOE núm. 259, do 28/10/2025, BOE-A-2025-21667), columna Galicia, e Decreto
   * 46/2025, do 9 de xuño (DOG núm. 117, do 20/06/2025), artigo 1. As dúas din o mesmo.
   */
  static final List<String> GALICIA_2026 =
      List.of(
          "2026-01-01", // Año Nuevo
          "2026-01-06", // Epifanía del Señor
          "2026-03-19", // San José
          "2026-04-02", // Jueves Santo
          "2026-04-03", // Viernes Santo
          "2026-05-01", // Fiesta del Trabajo
          "2026-06-24", // San Juan
          "2026-07-25", // Santiago Apóstol / Día Nacional de Galicia
          "2026-08-15", // Asunción de la Virgen
          "2026-10-12", // Fiesta Nacional de España
          "2026-12-08", // Inmaculada Concepción
          "2026-12-25"); // Natividad del Señor

  /**
   * 2027: Resolución do 8 de outubro de 2026 (BOE núm. 254, do 13/10/2026, BOE-A-2026-21254),
   * columna Galicia, e Decreto 68/2026, do 15 de xuño (DOG do 02/07/2026), artigo 1.
   */
  static final List<String> GALICIA_2027 =
      List.of(
          "2027-01-01",
          "2027-01-06",
          "2027-03-19",
          "2027-03-25",
          "2027-03-26",
          "2027-05-01",
          "2027-05-17",
          "2027-10-12",
          "2027-11-01",
          "2027-12-06",
          "2027-12-08",
          "2027-12-25");

  static List<String> dates(JsonNode list) {
    List<String> result = new ArrayList<>();
    list.forEach(h -> result.add(h.get("date").asString()));
    return result;
  }

  @Test
  void seededListMatchesTheBoeAndTheDog() {
    Api.Response y2026 = api().get("/api/v1/holidays?year=2026").send();
    assertThat(y2026.status()).as(y2026.toString()).isEqualTo(200);
    assertThat(dates(y2026.json())).containsExactlyElementsOf(GALICIA_2026);
    JsonNode stJames = y2026.json().get(7);
    assertThat(stJames.get("name").get("gl").asString()).isEqualTo("Día Nacional de Galicia");
    assertThat(stJames.get("source").asString()).contains("BOE-A-2025-21667", "Decreto 46/2025");

    assertThat(dates(api().get("/api/v1/holidays?year=2027").send().json()))
        .containsExactlyElementsOf(GALICIA_2027);
    // P11: un ano sen publicar non ten festivos.
    assertThat(api().get("/api/v1/holidays?year=2028").send().json().size()).isZero();
    assertThat(api().get("/api/v1/holidays?year=1999").send().status()).isEqualTo(422);
    assertThat(
            jdbc.sql("select count(*) from public_holiday where source = ''")
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void scheduleCarriesTheHolidaysOfTheYear() {
    String slug = uniqueSlug("fes");
    createMunicipality(slug);
    Api.Response created =
        api()
            .post(
                "/api/v1/municipalities/" + slug + "/facilities",
                java.util.Map.of("name", "Piscina", "address", FacilityIT.address()))
            .token(gal.furar.testsupport.TestTokens.manager(slug))
            .send();
    Api.Response schedule =
        api()
            .get(
                "/api/v1/municipalities/"
                    + slug
                    + "/facilities/"
                    + id(created.json())
                    + "/schedule?year=2026")
            .token(gal.furar.testsupport.TestTokens.staff(slug))
            .send();
    assertThat(schedule.status()).as(schedule.toString()).isEqualTo(200);
    assertThat(dates(schedule.json().get("holidays"))).containsExactlyElementsOf(GALICIA_2026);
    assertThat(schedule.json().get("seasons").size()).isZero();
    assertThat(schedule.json().hasNonNull("currentSeasonId")).isFalse();
  }

  /** ADR-0006: o catálogo é da plataforma e a API só o le. */
  @Test
  void apiRoleCannotWriteTheHolidays() {
    assertThat(appJdbc.sql("select count(*) from public_holiday").query(Long.class).single())
        .isGreaterThanOrEqualTo(12);
    for (String sql :
        List.of(
            "insert into public_holiday (date, name, source) values ('2026-11-02',"
                + " '{\"gl\":\"x\",\"es\":\"x\"}', 'x')",
            "update public_holiday set source = 'x'",
            "delete from public_holiday",
            "truncate public_holiday")) {
      assertThatThrownBy(() -> appJdbc.sql(sql).update())
          .as(sql)
          .rootCause()
          .hasMessageContaining("permission denied");
    }
  }
}
