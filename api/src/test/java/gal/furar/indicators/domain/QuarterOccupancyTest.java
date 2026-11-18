// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A ocupación por tramos de 15 minutos (ADR-0034, punto 4). */
class QuarterOccupancyTest {

  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  static final LocalDate DAY = LocalDate.of(2026, 10, 15);
  static final UUID WHOLE = UUID.randomUUID();
  static final UUID PARTS = UUID.randomUUID();
  static final UUID SEATS = UUID.randomUUID();

  static Instant at(LocalDate day, int hour, int minute) {
    return day.atTime(hour, minute).atZone(ZONE).toInstant();
  }

  static QuarterOccupancy.Open open(int fromHour, int toHour) {
    return new QuarterOccupancy.Open(at(DAY, fromHour, 0), at(DAY, toHour, 0));
  }

  static int index(int hour, int minute) {
    return hour * 4 + minute / 15;
  }

  @Test
  void aWholeBookingOf90MinutesFillsSixQuartersAndNothingElse() {
    QuarterOccupancy.Day day =
        QuarterOccupancy.of(
            DAY,
            ZONE,
            List.of(open(9, 22)),
            List.of(new QuarterOccupancy.Booking(WHOLE, null, at(DAY, 17, 0), at(DAY, 18, 30), 1)),
            Map.of(WHOLE, new QuarterOccupancy.Use(WHOLE, "WHOLE", 0, null, 90)),
            List.of());
    assertThat(day.quarters()).hasSize(96);
    for (int i = index(17, 0); i < index(18, 30); i++) {
      assertThat(day.quarters().get(i)).isEqualTo(100);
      assertThat(day.quarterUses().get(i)).isEqualTo(1);
    }
    assertThat(day.quarters().get(index(18, 30))).isZero();
    assertThat(day.quarterUses().get(index(18, 30))).isNull();
    assertThat(day.quarters().get(index(8, 45))).isNull();
    assertThat(day.quarters().get(index(22, 0))).isNull();
    assertThat(day.uses()).containsExactly(WHOLE);
  }

  @Test
  void partsCountTheLivePartsOfThatDay() {
    List<QuarterOccupancy.Booking> one =
        List.of(
            new QuarterOccupancy.Booking(
                PARTS, UUID.randomUUID(), at(DAY, 10, 0), at(DAY, 11, 0), 1));
    assertThat(value(one, new QuarterOccupancy.Use(PARTS, "PARTS", 2, null, 60))).isEqualTo(50);
    assertThat(value(one, new QuarterOccupancy.Use(PARTS, "PARTS", 3, null, 60))).isEqualTo(33);
    // Unha parte retirada despois dese día aínda contaba ese día.
    int live = QuarterOccupancy.livePartsOn(DAY, ZONE, 1, List.of(at(DAY.plusDays(3), 9, 0)));
    assertThat(live).isEqualTo(2);
    assertThat(QuarterOccupancy.livePartsOn(DAY, ZONE, 1, List.of(at(DAY.minusDays(1), 9, 0))))
        .isEqualTo(1);
  }

  @Test
  void seatsUseTheFrozenCapacityOfTheSlot() {
    List<QuarterOccupancy.Booking> bookings =
        List.of(
            new QuarterOccupancy.Booking(SEATS, null, at(DAY, 10, 0), at(DAY, 11, 0), 3),
            new QuarterOccupancy.Booking(SEATS, null, at(DAY, 10, 0), at(DAY, 11, 0), 2));
    QuarterOccupancy.Use use = new QuarterOccupancy.Use(SEATS, "SEATS", 0, 20, 60);
    QuarterOccupancy.Day day =
        QuarterOccupancy.of(
            DAY,
            ZONE,
            List.of(open(9, 12)),
            bookings,
            Map.of(SEATS, use),
            List.of(new QuarterOccupancy.SeatSlot(SEATS, at(DAY, 10, 0), 10)));
    // 5 de 10 (o aforo da franxa), non de 20 (o de hoxe).
    assertThat(day.quarters().get(index(10, 30))).isEqualTo(50);
  }

  @Test
  void roundsHalfUpAndCapsAt100() {
    List<QuarterOccupancy.Booking> two =
        List.of(
            new QuarterOccupancy.Booking(
                PARTS, UUID.randomUUID(), at(DAY, 10, 0), at(DAY, 11, 0), 1),
            new QuarterOccupancy.Booking(
                PARTS, UUID.randomUUID(), at(DAY, 10, 0), at(DAY, 11, 0), 1));
    assertThat(value(two, new QuarterOccupancy.Use(PARTS, "PARTS", 3, null, 60))).isEqualTo(67);
    assertThat(value(two, new QuarterOccupancy.Use(PARTS, "PARTS", 1, null, 60))).isEqualTo(100);
  }

  @Test
  void dstDaysHave92And100Quarters() {
    LocalDate march = LocalDate.of(2026, 3, 29);
    LocalDate october = LocalDate.of(2026, 10, 25);
    assertThat(
            QuarterOccupancy.of(march, ZONE, List.of(), List.of(), Map.of(), List.of()).quarters())
        .hasSize(92);
    assertThat(
            QuarterOccupancy.of(october, ZONE, List.of(), List.of(), Map.of(), List.of())
                .quarters())
        .hasSize(100);
  }

  @Test
  void aClosedDayHasNoValues() {
    QuarterOccupancy.Day day =
        QuarterOccupancy.of(DAY, ZONE, List.of(), List.of(), Map.of(), List.of());
    assertThat(day.opened()).isFalse();
  }

  private static int value(List<QuarterOccupancy.Booking> bookings, QuarterOccupancy.Use use) {
    QuarterOccupancy.Day day =
        QuarterOccupancy.of(
            DAY, ZONE, List.of(open(9, 12)), bookings, Map.of(use.id(), use), List.of());
    return day.quarters().get(index(10, 0));
  }
}
