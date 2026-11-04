// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.FieldViolation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regras do uso (especificación, bloque 1, «Usos»; plan D1-12), sen Spring. */
class FacilityUseTest {

  static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
  static final Instant LATER = Instant.parse("2026-10-26T10:00:00Z");
  static final FacilityId FACILITY = FacilityId.newId();

  static LocalDate d(String iso) {
    return LocalDate.parse(iso);
  }

  static UseSpec whole(String name) {
    return new UseSpec(
        name, RentalMode.WHOLE, null, 60, UseAvailability.ALWAYS, false, List.of(), List.of());
  }

  static UseSpec parts(String name, UseSpec.PartSpec... parts) {
    return new UseSpec(
        name, RentalMode.PARTS, null, 90, UseAvailability.ALWAYS, false, List.of(parts), List.of());
  }

  static UseSpec seats(Integer capacity) {
    return new UseSpec(
        "Natación libre",
        RentalMode.SEATS,
        capacity,
        60,
        UseAvailability.ALWAYS,
        false,
        List.of(),
        List.of());
  }

  static UseSpec dates(boolean blocks, UsePeriod... periods) {
    return new UseSpec(
        "Xornadas de Samaín",
        RentalMode.WHOLE,
        null,
        120,
        UseAvailability.DATES,
        blocks,
        List.of(),
        List.of(periods));
  }

  static UseSpec.PartSpec part(String name) {
    return new UseSpec.PartSpec(null, name);
  }

  static FacilityUse create(UseSpec spec) {
    return FacilityUse.create(UseId.newId(), FACILITY, spec, NOW);
  }

  /** Coma se viñese da base: con versión. */
  static FacilityUse stored(UseSpec spec) {
    FacilityUse u = create(spec);
    return FacilityUse.rehydrate(
        u.id(),
        u.facilityId(),
        u.name(),
        u.rentalMode(),
        u.capacity(),
        u.slotMinutes(),
        u.availability(),
        u.blocksOthers(),
        u.parts(),
        u.periods(),
        u.weekdays(),
        u.price(),
        u.rules(),
        u.archivedAt(),
        3L,
        u.createdAt(),
        u.updatedAt());
  }

  static void fails(Runnable action, ErrorCode code, String... fields) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(code);
              if (fields.length > 0) {
                assertThat(e.violations()).extracting(FieldViolation::field).contains(fields);
              }
            });
  }

  @Test
  void wholeUseWithTrimmedName() {
    FacilityUse use = create(whole("  Fútbol 11  "));
    assertThat(use.name()).isEqualTo("Fútbol 11");
    assertThat(use.rentalMode()).isEqualTo(RentalMode.WHOLE);
    assertThat(use.capacity()).isNull();
    assertThat(use.parts()).isEmpty();
    assertThat(use.version()).isNull();
    assertThat(use.isArchived()).isFalse();
  }

  @Test
  void nameIsRequiredAndShort() {
    fails(() -> create(whole(" ")), UseError.INVALID, "name");
    fails(() -> create(whole("x".repeat(101))), UseError.INVALID, "name");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 15, 45, 59, 180})
  void slotIsOneOfTheFourDurations(int minutes) {
    UseSpec spec =
        new UseSpec(
            "Pádel",
            RentalMode.WHOLE,
            null,
            minutes,
            UseAvailability.ALWAYS,
            false,
            List.of(),
            List.of());
    fails(() -> create(spec), UseError.INVALID, "slotMinutes");
  }

  @ParameterizedTest
  @ValueSource(ints = {30, 60, 90, 120})
  void theFourDurationsAreValid(int minutes) {
    UseSpec spec =
        new UseSpec(
            "Pádel",
            RentalMode.WHOLE,
            null,
            minutes,
            UseAvailability.ALWAYS,
            false,
            List.of(),
            List.of());
    assertThat(create(spec).slotMinutes()).isEqualTo(minutes);
  }

  @Test
  void partsNeedAtLeastTwoNamedParts() {
    fails(() -> create(parts("Fútbol 7")), UseError.PARTS_REQUIRED, "parts");
    fails(() -> create(parts("Fútbol 7", part("Campo A"))), UseError.PARTS_REQUIRED);
    fails(
        () -> create(parts("Fútbol 7", part("Campo A"), part(" "))),
        UseError.INVALID,
        "parts[1].name");
    FacilityUse use = create(parts("Atletismo", part("Pista"), part("Lonxitude"), part("Céspede")));
    assertThat(use.parts())
        .extracting(UsePart::name)
        .containsExactly("Pista", "Lonxitude", "Céspede");
    assertThat(use.parts()).extracting(UsePart::id).doesNotContainNull().doesNotHaveDuplicates();
  }

  @Test
  void atMostTwentyParts() {
    UseSpec.PartSpec[] many = new UseSpec.PartSpec[21];
    for (int i = 0; i < many.length; i++) {
      many[i] = part("Parte " + i);
    }
    fails(() -> create(parts("Moitas", many)), UseError.INVALID, "parts");
  }

  @Test
  void newPartsCannotBringAnUnknownId() {
    fails(
        () -> create(parts("Fútbol 7", new UseSpec.PartSpec(UUID.randomUUID(), "A"), part("B"))),
        UseError.INVALID,
        "parts[0].id");
  }

  @Test
  void partsAndCapacityOnlyInTheirMode() {
    UseSpec wholeWithParts =
        new UseSpec(
            "Fútbol 11",
            RentalMode.WHOLE,
            null,
            60,
            UseAvailability.ALWAYS,
            false,
            List.of(part("A"), part("B")),
            List.of());
    fails(() -> create(wholeWithParts), UseError.INVALID, "parts");
    UseSpec wholeWithCapacity =
        new UseSpec(
            "Fútbol 11",
            RentalMode.WHOLE,
            10,
            60,
            UseAvailability.ALWAYS,
            false,
            List.of(),
            List.of());
    fails(() -> create(wholeWithCapacity), UseError.INVALID, "capacity");
  }

  @Test
  void seatsFromTwoToTenThousand() {
    fails(() -> create(seats(null)), UseError.CAPACITY_REQUIRED, "capacity");
    fails(() -> create(seats(1)), UseError.INVALID, "capacity");
    fails(() -> create(seats(10_001)), UseError.INVALID, "capacity");
    assertThat(create(seats(2)).capacity()).isEqualTo(2);
    assertThat(create(seats(10_000)).capacity()).isEqualTo(10_000);
  }

  @Test
  void onlySomeDatesNeedsAPeriodAndAlwaysHasNone() {
    fails(() -> create(dates(false)), UseError.DATES_REQUIRED, "periods");
    UseSpec alwaysWithDates =
        new UseSpec(
            "Fútbol 11",
            RentalMode.WHOLE,
            null,
            60,
            UseAvailability.ALWAYS,
            false,
            List.of(),
            List.of(new UsePeriod(d("2026-07-01"), d("2026-07-15"))));
    fails(() -> create(alwaysWithDates), UseError.INVALID, "periods");
  }

  @Test
  void periodsAreOrderedRangesWithoutOverlaps() {
    fails(
        () -> create(dates(false, new UsePeriod(d("2026-07-15"), d("2026-07-01")))),
        UseError.INVALID,
        "periods[0].to");
    fails(
        () ->
            create(
                dates(
                    false,
                    new UsePeriod(d("2026-07-01"), d("2026-07-15")),
                    new UsePeriod(d("2026-07-15"), d("2026-07-20")))),
        UseError.PERIOD_OVERLAP);
    FacilityUse use =
        create(
            dates(
                false,
                new UsePeriod(d("2026-10-31"), d("2026-10-31")),
                new UsePeriod(d("2026-07-01"), d("2026-07-15"))));
    assertThat(use.periods())
        .containsExactly(
            new UsePeriod(d("2026-07-01"), d("2026-07-15")),
            new UsePeriod(d("2026-10-31"), d("2026-10-31")));
  }

  @Test
  void blocksOthersOnlyWithDates() {
    UseSpec spec =
        new UseSpec(
            "Torneo",
            RentalMode.WHOLE,
            null,
            60,
            UseAvailability.ALWAYS,
            true,
            List.of(),
            List.of());
    fails(() -> create(spec), UseError.BLOCKS_OTHERS_NEEDS_DATES, "blocksOthers");
    assertThat(create(dates(true, new UsePeriod(d("2026-07-01"), d("2026-07-15")))).blocksOthers())
        .isTrue();
  }

  @Test
  void updateKeepsKnownPartsArchivesTheRestAndCreatesNewOnes() {
    FacilityUse before = stored(parts("Fútbol 7", part("Campo A"), part("Campo B")));
    UsePart a = before.parts().get(0);
    UsePart b = before.parts().get(1);
    FacilityUse after =
        before.update(
            parts(
                "Fútbol 7",
                new UseSpec.PartSpec(b.id(), "Campo A"),
                new UseSpec.PartSpec(a.id(), "Campo B"),
                part("Campo C")),
            LATER);
    assertThat(after.id()).isEqualTo(before.id());
    assertThat(after.version()).isEqualTo(3L);
    assertThat(after.updatedAt()).isEqualTo(LATER);
    assertThat(after.parts()).extracting(UsePart::id).startsWith(b.id(), a.id()).hasSize(3);
    assertThat(after.parts())
        .extracting(UsePart::name)
        .containsExactly("Campo A", "Campo B", "Campo C");
    assertThat(after.removedParts(before)).isEmpty();
    assertThat(after.changesBookingsOf(before)).isFalse();

    FacilityUse fewer =
        before.update(parts("Fútbol 7", new UseSpec.PartSpec(a.id(), "A"), part("Nova")), LATER);
    assertThat(fewer.removedParts(before)).containsExactly(b);
    assertThat(fewer.changesBookingsOf(before)).isTrue();
  }

  @Test
  void whatChangesBookingsAndWhatNot() {
    FacilityUse pool = stored(seats(30));
    assertThat(pool.update(seats(40), LATER).changesBookingsOf(pool)).isFalse();
    assertThat(pool.update(seats(20), LATER).changesBookingsOf(pool)).isTrue();
    FacilityUse football = stored(whole("Fútbol 11"));
    assertThat(football.update(whole("Fútbol once"), LATER).changesBookingsOf(football)).isFalse();
    UseSpec longer =
        new UseSpec(
            "Fútbol 11",
            RentalMode.WHOLE,
            null,
            120,
            UseAvailability.ALWAYS,
            false,
            List.of(),
            List.of());
    assertThat(football.update(longer, LATER).changesBookingsOf(football)).isTrue();
    assertThat(football.update(seats(30), LATER).changesBookingsOf(football)).isTrue();
  }

  @Test
  void archivedIsFinal() {
    FacilityUse archived = stored(whole("Fútbol 11")).archive(LATER);
    assertThat(archived.isArchived()).isTrue();
    assertThat(archived.archivedAt()).isEqualTo(LATER);
    assertThat(archived.archive(NOW)).isSameAs(archived);
    fails(() -> archived.update(whole("Outro"), LATER), UseError.ARCHIVED);
    assertThat(archived.newlyBlockedSince(null)).isEmpty();
  }

  @Test
  void newlyBlockedDaysAreOnlyTheOnesTheUseAdds() {
    UsePeriod july = new UsePeriod(d("2026-07-01"), d("2026-07-31"));
    FacilityUse blocking = stored(dates(true, july));
    assertThat(blocking.newlyBlockedSince(null)).containsExactly(july);

    FacilityUse wider =
        blocking.update(dates(true, new UsePeriod(d("2026-06-25"), d("2026-08-05"))), LATER);
    assertThat(wider.newlyBlockedSince(blocking))
        .containsExactly(
            new UsePeriod(d("2026-06-25"), d("2026-06-30")),
            new UsePeriod(d("2026-08-01"), d("2026-08-05")));

    FacilityUse narrower =
        blocking.update(dates(true, new UsePeriod(d("2026-07-10"), d("2026-07-20"))), LATER);
    assertThat(narrower.newlyBlockedSince(blocking)).isEmpty();

    FacilityUse notBlocking = stored(dates(false, july));
    assertThat(notBlocking.update(dates(true, july), LATER).newlyBlockedSince(notBlocking))
        .containsExactly(july);
    assertThat(notBlocking.newlyBlockedSince(null)).isEmpty();
  }

  @Test
  void twoBlockingUsesOverlapOnlyIfTheirDatesTouch() {
    FacilityUse summer = stored(dates(true, new UsePeriod(d("2026-07-01"), d("2026-07-31"))));
    FacilityUse samain = stored(dates(true, new UsePeriod(d("2026-07-31"), d("2026-08-02"))));
    FacilityUse autumn = stored(dates(true, new UsePeriod(d("2026-08-01"), d("2026-08-02"))));
    FacilityUse plain = stored(dates(false, new UsePeriod(d("2026-07-01"), d("2026-07-31"))));
    assertThat(summer.exclusiveOverlapWith(samain)).isTrue();
    assertThat(summer.exclusiveOverlapWith(autumn)).isFalse();
    assertThat(summer.exclusiveOverlapWith(plain)).isFalse();
    assertThat(summer.exclusiveOverlapWith(summer)).isFalse();
    assertThat(summer.exclusiveOverlapWith(samain.archive(LATER))).isFalse();
  }

  // Condicións do uso (D2-02): días da semana, prezo e normas.

  static UseSpec terms(int weekdays, UsePrice price, UseRules rules) {
    UseSpec w = whole("Fútbol 7");
    return new UseSpec(
        w.name(),
        w.rentalMode(),
        w.capacity(),
        w.slotMinutes(),
        w.availability(),
        w.blocksOthers(),
        w.parts(),
        w.periods(),
        weekdays,
        price,
        rules);
  }

  static UseRules rules(
      Integer minAdvance,
      Integer maxAdvance,
      Integer maxSlots,
      Integer capCount,
      UseCapPeriod capPeriod,
      Integer cancel) {
    return new UseRules(minAdvance, maxAdvance, maxSlots, capCount, capPeriod, cancel);
  }

  @Test
  void aNewUseIsEveryDayWithoutPriceNorRules() {
    FacilityUse use = create(whole("Partido"));

    assertThat(use.weekdays()).isEqualTo(Weekdays.ALL);
    assertThat(use.price()).isEqualTo(UsePrice.NONE);
    assertThat(use.rules()).isEqualTo(UseRules.NONE);
  }

  @Test
  void keepsWeekdaysPriceAndRules() {
    UseRules rules = rules(2, 7, 2, 2, UseCapPeriod.WEEK, 24);
    FacilityUse use = create(terms(96, new UsePrice(UsePriceKind.PAID, 3000), rules));

    assertThat(use.weekdays()).isEqualTo(96);
    assertThat(use.price()).isEqualTo(new UsePrice(UsePriceKind.PAID, 3000));
    assertThat(use.rules()).isEqualTo(rules);
    FacilityUse emptied = stored(terms(96, UsePrice.NONE, rules)).update(whole("Fútbol 7"), LATER);
    assertThat(emptied.rules()).isEqualTo(UseRules.NONE);
    assertThat(emptied.weekdays()).isEqualTo(Weekdays.ALL);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 128, -1})
  void weekdaysNeedAtLeastOneDayOfTheWeek(int mask) {
    fails(
        () -> create(terms(mask, UsePrice.NONE, UseRules.NONE)),
        UseError.WEEKDAYS_INVALID,
        "weekdays");
  }

  @Test
  void aPaidPriceNeedsAnAmountInRange() {
    fails(
        () -> create(terms(127, new UsePrice(UsePriceKind.PAID, null), UseRules.NONE)),
        UseError.PRICE_INVALID,
        "price.amountCents");
    fails(
        () -> create(terms(127, new UsePrice(UsePriceKind.PAID, 0), UseRules.NONE)),
        UseError.PRICE_INVALID,
        "price.amountCents");
    fails(
        () -> create(terms(127, new UsePrice(UsePriceKind.PAID, 100_001), UseRules.NONE)),
        UseError.PRICE_INVALID,
        "price.amountCents");
    fails(
        () -> create(terms(127, new UsePrice(UsePriceKind.FREE, 500), UseRules.NONE)),
        UseError.PRICE_INVALID,
        "price.amountCents");
    assertThat(create(terms(127, new UsePrice(UsePriceKind.PAID, 1), UseRules.NONE)).price())
        .isEqualTo(new UsePrice(UsePriceKind.PAID, 1));
    assertThat(create(terms(127, new UsePrice(UsePriceKind.PAID, 100_000), UseRules.NONE)).price())
        .isEqualTo(new UsePrice(UsePriceKind.PAID, 100_000));
    assertThat(create(terms(127, new UsePrice(UsePriceKind.FREE, null), UseRules.NONE)).price())
        .isEqualTo(new UsePrice(UsePriceKind.FREE, null));
  }

  @Test
  void eachRuleHasItsRange() {
    record Case(UseRules rules, String field) {}
    List<Case> invalid =
        List.of(
            new Case(rules(-1, null, null, null, null, null), "rules.minAdvanceHours"),
            new Case(rules(721, null, null, null, null, null), "rules.minAdvanceHours"),
            new Case(rules(null, 0, null, null, null, null), "rules.maxAdvanceDays"),
            new Case(rules(null, 366, null, null, null, null), "rules.maxAdvanceDays"),
            new Case(rules(null, null, 0, null, null, null), "rules.maxSlots"),
            new Case(rules(null, null, 17, null, null, null), "rules.maxSlots"),
            new Case(rules(null, null, null, 0, UseCapPeriod.DAY, null), "rules.capCount"),
            new Case(rules(null, null, null, 51, UseCapPeriod.DAY, null), "rules.capCount"),
            new Case(rules(null, null, null, 2, null, null), "rules.capPeriod"),
            new Case(rules(null, null, null, null, UseCapPeriod.MONTH, null), "rules.capCount"),
            new Case(rules(null, null, null, null, null, -1), "rules.cancelHours"),
            new Case(rules(null, null, null, null, null, 721), "rules.cancelHours"));
    for (Case c : invalid) {
      fails(() -> create(terms(127, UsePrice.NONE, c.rules())), UseError.RULE_INVALID, c.field());
    }
    UseRules lowest = rules(0, 1, 1, 1, UseCapPeriod.DAY, 0);
    UseRules highest = rules(720, 365, 16, 50, UseCapPeriod.MONTH, 720);
    assertThat(create(terms(127, UsePrice.NONE, lowest)).rules()).isEqualTo(lowest);
    assertThat(create(terms(127, UsePrice.NONE, highest)).rules()).isEqualTo(highest);
  }

  @Test
  void availableOnLooksAtDatesAndWeekdays() {
    int weekends =
        Weekdays.bit(java.time.DayOfWeek.SATURDAY) | Weekdays.bit(java.time.DayOfWeek.SUNDAY);
    UseSpec base = dates(false, new UsePeriod(d("2026-11-01"), d("2026-11-30")));
    FacilityUse use =
        create(
            new UseSpec(
                base.name(),
                base.rentalMode(),
                base.capacity(),
                base.slotMinutes(),
                base.availability(),
                base.blocksOthers(),
                base.parts(),
                base.periods(),
                weekends,
                UsePrice.NONE,
                UseRules.NONE));

    assertThat(use.availableOn(d("2026-11-07"))).isTrue(); // sábado
    assertThat(use.availableOn(d("2026-11-08"))).isTrue(); // domingo
    assertThat(use.availableOn(d("2026-11-10"))).isFalse(); // martes
    assertThat(use.availableOn(d("2026-12-05"))).isFalse(); // sábado, fóra das datas
    assertThat(use.archive(LATER).availableOn(d("2026-11-07"))).isFalse();
  }

  @Test
  void twoBlockingUsesOnDifferentWeekdaysDoNotOverlap() {
    int saturday = Weekdays.bit(java.time.DayOfWeek.SATURDAY);
    int tuesday = Weekdays.bit(java.time.DayOfWeek.TUESDAY);
    FacilityUse saturdays = blocking(saturday, d("2026-11-01"), d("2026-11-30"));
    FacilityUse tuesdays = blocking(tuesday, d("2026-11-01"), d("2026-11-30"));
    FacilityUse shortTuesdays = blocking(Weekdays.ALL, d("2026-11-03"), d("2026-11-03"));

    assertThat(saturdays.exclusiveOverlapWith(tuesdays)).isFalse();
    assertThat(saturdays.exclusiveOverlapWith(shortTuesdays)).isFalse();
    assertThat(tuesdays.exclusiveOverlapWith(shortTuesdays)).isTrue();
  }

  static FacilityUse blocking(int weekdays, LocalDate from, LocalDate to) {
    UseSpec base = dates(true, new UsePeriod(from, to));
    return create(
        new UseSpec(
            base.name(),
            base.rentalMode(),
            base.capacity(),
            base.slotMinutes(),
            base.availability(),
            true,
            base.parts(),
            base.periods(),
            weekdays,
            UsePrice.NONE,
            UseRules.NONE));
  }
}
