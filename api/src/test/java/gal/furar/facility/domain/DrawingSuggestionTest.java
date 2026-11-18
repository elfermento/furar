// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static gal.furar.facility.domain.DrawingSuggestion.suggest;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Debuxo suxerido: nome (gl e es), deportes, tipo e xenérico, por esa orde. */
class DrawingSuggestionTest {

  static List<SportRef> sports(String... codes) {
    return Arrays.stream(codes).map(SportRef::catalog).toList();
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        // galego
        "Pavillón de Piñeiros | sports_hall",
        "Polideportivo municipal | sports_hall",
        "Piscina climatizada da Malata | pool",
        "Pista de pádel 2 de O Alto | padel_court",
        "Pistas de tenis do Couto | tennis_court",
        "Sala de tenis de mesa | table_tennis",
        "Campo de fútbol 1 de O Alto | football_field",
        "Campo de rugby de Xuvia | rugby_field",
        "Pista de atletismo da Malata | athletics_track",
        "Frontón de Pedroso | fronton",
        "Pista de squash | squash_court",
        "Ximnasio do pavillón | sports_hall",
        "Ximnasio municipal | gym",
        "Rocódromo | climbing_wall",
        "Velódromo | velodrome",
        "Skatepark de Narón | roller_rink",
        "Pista polideportiva de San Mateo | outdoor_court",
        "Sala multiusos | multipurpose_room",
        "Embarcadoiro do Río Grande | jetty",
        "Campo de hóckey herba | field_hockey",
        // castellano
        "Pabellón de deportes | sports_hall",
        "Piscina cubierta | pool",
        "Gimnasio | gym",
        "Sala de musculación | gym",
        "Escalada en bloque | climbing_wall",
        "Pista de bádminton | badminton_court",
        "Campo de fútbol de hierba | football_field",
        "Embarcadero | jetty",
        "Vóley playa | beach_volleyball",
        "PISCINA DE VERÁN | pool",
      })
  void theNameDecidesFirst(String name, String drawing) {
    assertThat(suggest(name, sports("athletics"), FacilityType.OTHER, true)).isEqualTo(drawing);
  }

  @Test
  void wordsInsideOtherWordsDoNotCount() {
    assertThat(suggest("Extremo norte", List.of(), null, null)).isEqualTo("generic");
    assertThat(suggest("Área deportiva", List.of(), null, null)).isEqualTo("generic");
  }

  @Test
  void futsalIsNotTheFootballField() {
    assertThat(suggest("Pista de fútbol sala", sports("futsal"), null, true))
        .isEqualTo("sports_hall");
    assertThat(suggest("Pista de fútbol sala", sports("futsal"), null, false))
        .isEqualTo("outdoor_court");
  }

  @Test
  void oneSportGivesItsDrawing() {
    assertThat(suggest("Instalación A", sports("swimming"), null, null)).isEqualTo("pool");
    assertThat(suggest("Instalación A", sports("tennis"), null, null)).isEqualTo("tennis_court");
    assertThat(suggest("Instalación A", sports("football", "football_7"), null, null))
        .as("varios que levan ao mesmo debuxo")
        .isEqualTo("football_field");
    assertThat(suggest("Instalación A", sports("basketball"), null, false))
        .isEqualTo("basketball_court");
    assertThat(suggest("Instalación A", sports("basketball"), null, true)).isEqualTo("sports_hall");
  }

  @Test
  void severalCourtSportsGiveTheHallOrTheOutdoorCourt() {
    List<SportRef> court = sports("basketball", "futsal", "handball");
    assertThat(suggest("Instalación A", court, null, true)).isEqualTo("sports_hall");
    assertThat(suggest("Instalación A", court, null, false)).isEqualTo("outdoor_court");
    assertThat(suggest("Instalación A", court, null, null)).isEqualTo("outdoor_court");
  }

  @Test
  void mixedSportsFallBackToTheTypeAndThenToTheGenericOne() {
    List<SportRef> mixed = sports("swimming", "tennis");
    assertThat(suggest("Instalación A", mixed, FacilityType.POOL, null)).isEqualTo("pool");
    assertThat(suggest("Instalación A", mixed, FacilityType.FIELD, null))
        .isEqualTo("football_field");
    assertThat(suggest("Instalación A", mixed, FacilityType.OTHER, null)).isEqualTo("generic");
    assertThat(suggest("Instalación A", List.of(), null, null)).isEqualTo("generic");
  }

  @Test
  void customSportsDoNotCount() {
    assertThat(
            suggest(
                "Instalación A",
                List.of(SportRef.custom(UUID.randomUUID())),
                FacilityType.SPORTS_HALL,
                null))
        .isEqualTo("sports_hall");
  }

  @Test
  void everySuggestionIsInTheCatalog() {
    for (FacilityType type : FacilityType.values()) {
      assertThat(DrawingSuggestion.CODES).contains(suggest("x", List.of(), type, null));
    }
    for (String sport :
        List.of(
            "athletics",
            "badminton",
            "boxing",
            "canoeing",
            "climbing",
            "cycling",
            "dance",
            "fitness",
            "football",
            "football_7",
            "fronton",
            "gymnastics",
            "field_hockey",
            "roller_hockey",
            "judo",
            "karate",
            "martial_arts",
            "padel",
            "pilates",
            "rowing",
            "rugby",
            "skating",
            "squash",
            "swimming",
            "table_tennis",
            "tennis",
            "volleyball",
            "beach_volleyball",
            "water_polo",
            "wrestling",
            "yoga",
            "basketball",
            "futsal",
            "handball")) {
      assertThat(DrawingSuggestion.CODES)
          .as(sport)
          .contains(suggest("x", sports(sport), null, null));
    }
  }
}
