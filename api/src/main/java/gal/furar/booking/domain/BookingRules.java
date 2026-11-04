// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * As catro normas dun uso, tal como se avalían ao reservar, cambiar e cancelar (plan do dominio 2,
 * §2.3; ADR-0025, punto 1). Baleira = non se aplica. Viven no uso ({@code facility} valídaas e
 * publícaas) e a reserva garda as aplicadas ({@code rule_*}): cambiar as do uso non toca as
 * reservas xa feitas. Dominio puro: o reloxo e a conta do límite por persoa veñen de fóra.
 *
 * @param minAdvanceHours antelación mínima, en horas (0–720)
 * @param maxAdvanceDays antelación máxima, en días naturais (1–365)
 * @param maxSlots duración máxima, en franxas seguidas (1–16)
 * @param capCount límite por persoa: reservas confirmadas por período (1–50)
 * @param capPeriod período natural do límite, con {@code capCount}
 * @param cancelHours cancelar sen custo ata estas horas antes do inicio (0–720)
 */
public record BookingRules(
    @Nullable Integer minAdvanceHours,
    @Nullable Integer maxAdvanceDays,
    @Nullable Integer maxSlots,
    @Nullable Integer capCount,
    @Nullable CapPeriod capPeriod,
    @Nullable Integer cancelHours) {

  /** Días, semanas e meses das normas: hora de Galicia (§1.2.7). */
  public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  public BookingRules {
    range("minAdvanceHours", minAdvanceHours, 0, 720);
    range("maxAdvanceDays", maxAdvanceDays, 1, 365);
    range("maxSlots", maxSlots, 1, 16);
    range("capCount", capCount, 1, 50);
    range("cancelHours", cancelHours, 0, 720);
    if ((capCount == null) != (capPeriod == null)) {
      throw new IllegalArgumentException("The cap needs both a count and a period");
    }
  }

  /** Sen ningunha norma. */
  public static BookingRules none() {
    return new BookingRules(null, null, null, null, null, null);
  }

  /**
   * Antelación e duración dun oco de {@code slots} franxas que empeza en {@code start}. Unha franxa
   * xa empezada nunca se reserva, haxa norma ou non.
   *
   * @throws DomainException {@code booking.too-soon}, {@code booking.too-far} ou {@code
   *     booking.too-long}
   */
  public void checkSlot(Instant start, int slots, Instant now) {
    if (!start.isAfter(now)) {
      throw new DomainException(BookingError.TOO_SOON);
    }
    if (minAdvanceHours != null && start.isBefore(now.plus(Duration.ofHours(minAdvanceHours)))) {
      throw new DomainException(BookingError.TOO_SOON, Map.of("hours", minAdvanceHours));
    }
    if (maxAdvanceDays != null) {
      LocalDate last = now.atZone(ZONE).toLocalDate().plusDays(maxAdvanceDays);
      if (start.atZone(ZONE).toLocalDate().isAfter(last)) {
        throw new DomainException(BookingError.TOO_FAR, Map.of("days", maxAdvanceDays));
      }
    }
    if (maxSlots != null && slots > maxSlots) {
      throw new DomainException(BookingError.TOO_LONG, Map.of("max", maxSlots));
    }
  }

  /**
   * Se a antelación deixa reservar unha franxa que empeza en {@code start}: como {@link #checkSlot}
   * sen a duración e sen erro. Para pintar só os ocos que se poden reservar (plan do dominio 2,
   * §2.3: sen sesión non se mostran ocos que a antelación non permite).
   */
  public boolean allows(Instant start, Instant now) {
    if (!start.isAfter(now)) {
      return false;
    }
    if (minAdvanceHours != null && start.isBefore(now.plus(Duration.ofHours(minAdvanceHours)))) {
      return false;
    }
    return reaches(start.atZone(ZONE).toLocalDate(), now);
  }

  /** Se o día queda dentro da antelación máxima (sen norma, sempre). */
  public boolean reaches(LocalDate date, Instant now) {
    return maxAdvanceDays == null
        || !date.isAfter(now.atZone(ZONE).toLocalDate().plusDays(maxAdvanceDays));
  }

  /** As franxas seguidas que se poden reservar dun golpe se hai {@code free} libres seguidas. */
  public int slotsUpTo(int free) {
    return maxSlots == null ? free : Math.min(free, maxSlots);
  }

  /**
   * O período natural no que se contan as reservas da persoa para o límite (P4): o que contén o
   * inicio do oco. Baleiro sen límite.
   */
  public Optional<TimeWindow> capWindow(Instant start) {
    return capPeriod == null ? Optional.empty() : Optional.of(capPeriod.around(start, ZONE));
  }

  /**
   * Límite por persoa: {@code confirmed} son as reservas confirmadas da persoa nese uso con inicio
   * no período (sen contar a que se cambia).
   *
   * @throws DomainException {@code booking.cap-reached}
   */
  public void checkCap(int confirmed) {
    if (capCount != null && confirmed >= capCount) {
      throw new DomainException(
          BookingError.CAP_REACHED, Map.of("count", capCount, "period", capPeriod.name()));
    }
  }

  /** Ata cando se cancela sen custo: {@code start − N h}; sen norma, ata o inicio. */
  public Instant cancelUntil(Instant start) {
    return cancelHours == null ? start : start.minus(Duration.ofHours(cancelHours));
  }

  private static void range(String name, @Nullable Integer value, int min, int max) {
    if (value != null && (value < min || value > max)) {
      throw new IllegalArgumentException(name + " out of range: " + value);
    }
  }
}
