// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static gal.furar.booking.domain.Bookings.NOW;
import static gal.furar.booking.domain.Bookings.assertFails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Cada norma tal como se avalía (plan do dominio 2, §2.3): baleira non se aplica; os días, semanas
 * e meses en hora de Galicia, cambio de hora incluído.
 */
class BookingRulesTest {

  static final BookingRules NONE = BookingRules.none();

  static BookingRules minAdvance(int hours) {
    return new BookingRules(hours, null, null, null, null, null);
  }

  static BookingRules maxAdvance(int days) {
    return new BookingRules(null, days, null, null, null, null);
  }

  static BookingRules maxSlots(int slots) {
    return new BookingRules(null, null, slots, null, null, null);
  }

  static BookingRules cap(int count, CapPeriod period) {
    return new BookingRules(null, null, null, count, period, null);
  }

  // --- Sempre ---

  @Test
  void aSlotThatAlreadyStartedIsNeverBooked() {
    assertFails(BookingError.TOO_SOON, () -> NONE.checkSlot(NOW, 1, NOW));
    assertFails(BookingError.TOO_SOON, () -> NONE.checkSlot(NOW.minusSeconds(60), 1, NOW));
    assertThatCode(() -> NONE.checkSlot(NOW.plusSeconds(60), 16, NOW)).doesNotThrowAnyException();
  }

  // --- Antelación mínima: start ≥ now + N h ---

  @Test
  void minimumAdvance() {
    BookingRules rules = minAdvance(2);
    assertFails(
        BookingError.TOO_SOON, () -> rules.checkSlot(NOW.plus(Duration.ofMinutes(119)), 1, NOW));
    assertThatCode(() -> rules.checkSlot(NOW.plus(Duration.ofHours(2)), 1, NOW))
        .doesNotThrowAnyException();
  }

  // --- Antelación máxima: data do oco ≤ hoxe + N (Europe/Madrid) ---

  @Test
  void maximumAdvanceCountsCalendarDaysInGalicia() {
    BookingRules rules = maxAdvance(7);
    // Agora: martes 3 de novembro, 10:00 en Galicia. Último día: martes 10, ata as 23:59.
    Instant lastMinute = Instant.parse("2026-11-10T22:59:00Z");
    Instant nextDay = Instant.parse("2026-11-10T23:00:00Z");
    assertThatCode(() -> rules.checkSlot(lastMinute, 1, NOW)).doesNotThrowAnyException();
    assertFails(BookingError.TOO_FAR, () -> rules.checkSlot(nextDay, 1, NOW));
  }

  @Test
  void maximumAdvanceUsesTheDateInGaliciaNotInUtc() {
    // 23:30 do luns 2 en UTC xa é martes 3 en Galicia: hoxe é o 3, non o 2.
    Instant now = Instant.parse("2026-11-02T23:30:00Z");
    BookingRules rules = maxAdvance(1);
    assertThatCode(() -> rules.checkSlot(Instant.parse("2026-11-04T20:00:00Z"), 1, now))
        .doesNotThrowAnyException();
    assertFails(
        BookingError.TOO_FAR, () -> rules.checkSlot(Instant.parse("2026-11-04T23:00:00Z"), 1, now));
  }

  // --- Duración máxima: slots ≤ N ---

  @Test
  void maximumLength() {
    BookingRules rules = maxSlots(2);
    Instant start = NOW.plus(Duration.ofHours(5));
    assertThatCode(() -> rules.checkSlot(start, 2, NOW)).doesNotThrowAnyException();
    assertFails(BookingError.TOO_LONG, () -> rules.checkSlot(start, 3, NOW));
  }

  // --- Límite por persoa: confirmadas no período natural < N ---

  @Test
  void capPerPerson() {
    BookingRules rules = cap(2, CapPeriod.WEEK);
    assertThatCode(() -> rules.checkCap(1)).doesNotThrowAnyException();
    assertFails(BookingError.CAP_REACHED, () -> rules.checkCap(2));
    assertThatCode(() -> NONE.checkCap(500)).doesNotThrowAnyException();
    assertThat(NONE.capWindow(NOW)).isEmpty();
  }

  @Test
  void theCapCountsNaturalPeriodsInGalicia() {
    Instant tuesday = Instant.parse("2026-11-03T18:00:00Z");
    assertThat(cap(1, CapPeriod.DAY).capWindow(tuesday))
        .contains(
            new TimeWindow(
                Instant.parse("2026-11-02T23:00:00Z"), Instant.parse("2026-11-03T23:00:00Z")));
    // Semana de luns a domingo.
    assertThat(cap(1, CapPeriod.WEEK).capWindow(tuesday))
        .contains(
            new TimeWindow(
                Instant.parse("2026-11-01T23:00:00Z"), Instant.parse("2026-11-08T23:00:00Z")));
    assertThat(cap(1, CapPeriod.MONTH).capWindow(tuesday))
        .contains(
            new TimeWindow(
                Instant.parse("2026-10-31T23:00:00Z"), Instant.parse("2026-11-30T23:00:00Z")));
  }

  @Test
  void theCapWindowCrossesTheChangeOfTime() {
    // Domingo 25 de outubro de 2026: as 3:00 volven ser as 2:00 (UTC+2 → UTC+1).
    Instant sunday = Instant.parse("2026-10-25T10:00:00Z");
    assertThat(cap(1, CapPeriod.DAY).capWindow(sunday))
        .contains(
            new TimeWindow(
                Instant.parse("2026-10-24T22:00:00Z"), Instant.parse("2026-10-25T23:00:00Z")));
    assertThat(cap(1, CapPeriod.WEEK).capWindow(sunday))
        .contains(
            new TimeWindow(
                Instant.parse("2026-10-18T22:00:00Z"), Instant.parse("2026-10-25T23:00:00Z")));
  }

  // --- Cancelar sen custo: cancel_until = start − N h; sen norma, start ---

  @Test
  void cancelUntil() {
    Instant start = NOW.plus(Duration.ofDays(3));
    assertThat(new BookingRules(null, null, null, null, null, 24).cancelUntil(start))
        .isEqualTo(start.minus(Duration.ofHours(24)));
    assertThat(new BookingRules(null, null, null, null, null, 0).cancelUntil(start))
        .isEqualTo(start);
    assertThat(NONE.cancelUntil(start)).isEqualTo(start);
  }

  // --- Rangos, como no uso ---

  @Test
  void rulesOutOfRangeAreAProgrammingError() {
    assertThatThrownBy(() -> minAdvance(721)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> maxAdvance(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> maxSlots(17)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cap(51, CapPeriod.DAY)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BookingRules(null, null, null, 2, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BookingRules(null, null, null, null, CapPeriod.DAY, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BookingRules(null, null, null, null, null, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
