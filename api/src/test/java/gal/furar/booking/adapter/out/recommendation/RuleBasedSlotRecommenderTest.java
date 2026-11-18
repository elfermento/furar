// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.SimilarSlot;
import gal.furar.booking.domain.SimilarSlot.Reason;
import gal.furar.booking.domain.SlotCandidate;
import gal.furar.booking.domain.SlotTarget;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * As regras dos «ocos parecidos» (ADR-0033, punto 9): cada unha, a orde, o empate e sen repetir.
 */
class RuleBasedSlotRecommenderTest {

  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  /** Mércores 11 de novembro de 2026. */
  static final LocalDate DAY = LocalDate.of(2026, 11, 11);

  static final UUID HERE = UUID.fromString("019a0b6e-0000-7000-8000-0000000000f1");
  static final UUID USE = UUID.fromString("019a0b6e-0000-7000-8000-0000000000a1");
  static final UUID COURT_2 = UUID.fromString("019a0b6e-0000-7000-8000-0000000000f2");
  static final UUID USE_2 = UUID.fromString("019a0b6e-0000-7000-8000-0000000000a2");
  static final UUID COURT_3 = UUID.fromString("019a0b6e-0000-7000-8000-0000000000f3");
  static final UUID USE_3 = UUID.fromString("019a0b6e-0000-7000-8000-0000000000a3");

  final RuleBasedSlotRecommender recommender = new RuleBasedSlotRecommender();

  static SlotCandidate slot(UUID facility, String name, UUID use, LocalDate date, int hour) {
    return slot(facility, name, use, date, hour, 0);
  }

  static SlotCandidate slot(
      UUID facility, String name, UUID use, LocalDate date, int hour, int minute) {
    var start = date.atTime(hour, minute).atZone(ZONE);
    return new SlotCandidate(
        facility,
        name,
        use,
        date,
        start.toInstant(),
        start.plusHours(1).toInstant(),
        hour * 60 + minute);
  }

  static SlotCandidate here(LocalDate date, int hour) {
    return slot(HERE, "Pista 1", USE, date, hour);
  }

  static SlotTarget target(LocalDate date, int hour) {
    return SlotTarget.of(HERE, USE, date.atTime(hour, 0).atZone(ZONE).toInstant(), 60);
  }

  List<String> rows(SlotTarget target, List<SlotCandidate> candidates) {
    List<String> rows = new ArrayList<>();
    for (SimilarSlot s : recommender.recommend(target, candidates, 4)) {
      rows.add(
          s.reason()
              + " "
              + s.slot().facilityName()
              + " "
              + s.slot().date().getDayOfMonth()
              + " "
              + s.slot().startMinute() / 60
              + ":"
              + String.format("%02d", s.slot().startMinute() % 60));
    }
    return rows;
  }

  /** As catro filas: 18:00 aquí, a outra pista ás 19:00, 21:00 aquí e o mércores seguinte. */
  @Test
  void oneRowPerRuleInOrder() {
    List<SlotCandidate> candidates =
        List.of(
            here(DAY, 16),
            here(DAY, 18),
            here(DAY, 21),
            here(DAY, 22),
            slot(COURT_2, "Pista 2", USE_2, DAY, 19),
            slot(COURT_2, "Pista 2", USE_2, DAY, 20),
            here(DAY.plusDays(7), 18),
            here(DAY.plusDays(7), 19));

    assertThat(rows(target(DAY, 19), candidates))
        .containsExactly(
            "SAME_FACILITY_NEAR Pista 1 11 18:00",
            "OTHER_FACILITY_SAME_TIME Pista 2 11 19:00",
            "LATER_SAME_DAY Pista 1 11 21:00",
            "NEXT_WEEK Pista 1 18 19:00");
  }

  /** A franxa anterior ou a seguinte; coas dúas libres (18:00 e 20:00), a anterior. */
  @Test
  void thePreviousOrTheNextSlotAndThePreviousWhenBothAreFree() {
    assertThat(rows(target(DAY, 19), List.of(here(DAY, 18), here(DAY, 20))))
        .containsExactly("SAME_FACILITY_NEAR Pista 1 11 18:00", "LATER_SAME_DAY Pista 1 11 20:00");
    // Só a seguinte libre.
    assertThat(rows(target(DAY, 19), List.of(here(DAY, 17), here(DAY, 20))).getFirst())
        .isEqualTo("SAME_FACILITY_NEAR Pista 1 11 20:00");
    // A dúas franxas xa non é a anterior nin a seguinte: só queda como «máis tarde».
    assertThat(rows(target(DAY, 19), List.of(here(DAY, 16), here(DAY, 21))))
        .containsExactly("LATER_SAME_DAY Pista 1 11 21:00");
  }

  /** Dure o que dure a franxa: nun uso de 90 minutos, para as 18:30, as 17:00 ou as 20:00. */
  @Test
  void theNeighbourSlotFollowsTheLengthOfTheSlot() {
    SlotTarget halfPastSix =
        SlotTarget.of(HERE, USE, DAY.atTime(18, 30).atZone(ZONE).toInstant(), 90);

    assertThat(rows(halfPastSix, List.of(here(DAY, 17), here(DAY, 20))))
        .containsExactly("SAME_FACILITY_NEAR Pista 1 11 17:00", "LATER_SAME_DAY Pista 1 11 20:00");
    assertThat(rows(halfPastSix, List.of(here(DAY, 20), slot(HERE, "Pista 1", USE, DAY, 21, 30))))
        .containsExactly("SAME_FACILITY_NEAR Pista 1 11 20:00", "LATER_SAME_DAY Pista 1 11 21:30");
    // A unha hora xusta (17:30 ou 19:30) non hai franxa dese uso: non conta como veciña.
    assertThat(rows(halfPastSix, List.of(slot(HERE, "Pista 1", USE, DAY, 19, 30))))
        .containsExactly("LATER_SAME_DAY Pista 1 11 19:30");
  }

  /** Outra instalación á mesma hora: a primeira por nome. */
  @Test
  void anotherFacilityAtTheSameTimeByName() {
    List<SlotCandidate> candidates =
        List.of(
            slot(COURT_3, "Pista do Río", USE_3, DAY, 19),
            slot(COURT_2, "pista da Gándara", USE_2, DAY, 19),
            slot(COURT_2, "pista da Gándara", USE_2, DAY, 18));

    assertThat(rows(target(DAY, 19), candidates))
        .containsExactly("OTHER_FACILITY_SAME_TIME pista da Gándara 11 19:00");
  }

  /** Máis tarde: primeiro aquí; se non hai, noutra, pola hora máis temperá e despois por nome. */
  @Test
  void laterTheSameDayHereFirstAndThenElsewhere() {
    List<SlotCandidate> elsewhere =
        List.of(
            slot(COURT_3, "Pista 3", USE_3, DAY, 21),
            slot(COURT_2, "Pista 2", USE_2, DAY, 21),
            slot(COURT_3, "Pista 3", USE_3, DAY, 22));
    assertThat(rows(target(DAY, 19), elsewhere)).containsExactly("LATER_SAME_DAY Pista 2 11 21:00");

    List<SlotCandidate> withHere = new ArrayList<>(elsewhere);
    withHere.add(here(DAY, 22));
    assertThat(rows(target(DAY, 19), withHere)).containsExactly("LATER_SAME_DAY Pista 1 11 22:00");
  }

  /** O mesmo oco non sae dúas veces: se a próxima é a das 20:00, «máis tarde» é a seguinte. */
  @Test
  void theSameSlotIsNotProposedTwice() {
    assertThat(rows(target(DAY, 19), List.of(here(DAY, 20), here(DAY, 21))))
        .containsExactly("SAME_FACILITY_NEAR Pista 1 11 20:00", "LATER_SAME_DAY Pista 1 11 21:00");
    assertThat(rows(target(DAY, 19), List.of(here(DAY, 20))))
        .containsExactly("SAME_FACILITY_NEAR Pista 1 11 20:00");
  }

  /** A semana seguinte á mesma hora de reloxo, tamén co cambio de hora polo medio. */
  @Test
  void nextWeekAtTheSameWallClockTimeAcrossTheClockChange() {
    // O domingo 25 de outubro de 2026 atrásase a hora: o 21 é UTC+2 e o 28, UTC+1.
    LocalDate before = LocalDate.of(2026, 10, 21);
    List<SlotCandidate> candidates =
        List.of(here(before.plusDays(7), 18), here(before.plusDays(7), 19));

    assertThat(rows(target(before, 19), candidates)).containsExactly("NEXT_WEEK Pista 1 28 19:00");
    // Noutra instalación ou noutro día non é «a semana seguinte».
    assertThat(
            rows(
                target(before, 19),
                List.of(
                    slot(COURT_2, "Pista 2", USE_2, before.plusDays(7), 19),
                    here(before.plusDays(6), 19))))
        .isEmpty();
  }

  @Test
  void withoutCandidatesThereIsNothingAndTheMaximumCuts() {
    assertThat(recommender.recommend(target(DAY, 19), List.of(), 4)).isEmpty();
    assertThat(
            recommender.recommend(
                target(DAY, 19),
                List.of(here(DAY, 18), here(DAY, 20), here(DAY.plusDays(7), 19)),
                2))
        .extracting(SimilarSlot::reason)
        .containsExactly(Reason.SAME_FACILITY_NEAR, Reason.LATER_SAME_DAY);
  }
}
