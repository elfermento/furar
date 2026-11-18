// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.UseSummary;
import gal.furar.facility.UseTerms;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Que usos conta o listado dos ocos (ADR-0025, revisión do 2026-11-18). */
class AvailabilityCountedTest {

  static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
  static final UUID FACILITY = UUID.randomUUID();

  static UseSummary use(String name, List<UseSummary.DateRange> periods, String... sports) {
    return new UseSummary(
        UUID.randomUUID(),
        FACILITY,
        name,
        "WHOLE",
        null,
        60,
        false,
        List.of(),
        periods,
        127,
        false,
        new UseTerms(
            new UseTerms.Price("FREE", null, "BOOKING"),
            new UseTerms.Rules(null, null, null, null, null, null)),
        List.of(sports));
  }

  final UseSummary lastSummer =
      use(
          "Campus de verán",
          List.of(new UseSummary.DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31))),
          "football");
  final UseSummary whole = use("Fútbol 11", List.of(), "football");
  final UseSummary half = use("Fútbol 7", List.of(), "football_7");
  final UseSummary otherHalf = use("Fútbol 7 (tarde)", List.of(), "football_7");
  final List<UseSummary> uses = List.of(lastSummer, whole, half, otherHalf);

  @Test
  void withoutSportsEveryUseThePageStillOffersInItsOrder() {
    assertThat(AvailabilityService.counted(uses, Set.of(), TODAY))
        .containsExactly(whole, half, otherHalf);
  }

  @Test
  void withSportsTheOfferedUsesWithAnyOfThemTheFirstOpensThePage() {
    assertThat(AvailabilityService.counted(uses, Set.of("football_7"), TODAY))
        .containsExactly(half, otherHalf);
    assertThat(AvailabilityService.counted(uses, Set.of("football"), TODAY))
        .as("o campus de verán xa non se ofrece")
        .containsExactly(whole);
    assertThat(AvailabilityService.counted(uses, Set.of("padel"), TODAY)).isEmpty();
  }
}
