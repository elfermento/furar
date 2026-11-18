// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.domain;

import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * As datas dunha descarga da ocupación (ADR-0034, punto 10), días de Europe/Madrid cos dous
 * extremos incluídos: dentro dos últimos 12 meses (de hoxe − 12 meses a onte) e como moito {@code
 * maxMonths} meses. Sen datas, o último mes enteiro.
 */
public record OccupancyRange(LocalDate from, LocalDate to) {

  /** A zona dos días e dos instantes publicados. */
  public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  /** Meses que se gardan e que se poden pedir. */
  public static final int KEPT_MONTHS = 12;

  public OccupancyRange {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to before from");
    }
  }

  /** O primeiro día que se pode pedir. */
  public static LocalDate availableFrom(LocalDate today) {
    return today.minusMonths(KEPT_MONTHS);
  }

  /** O último día que se pode pedir: onte. */
  public static LocalDate availableTo(LocalDate today) {
    return today.minusDays(1);
  }

  /** O último mes enteiro (o 11 de novembro, do 1 ao 31 de outubro). */
  public static OccupancyRange lastFullMonth(LocalDate today) {
    LocalDate first = today.withDayOfMonth(1).minusMonths(1);
    return new OccupancyRange(first, first.plusMonths(1).minusDays(1));
  }

  /**
   * As datas pedidas ({@code AAAA-MM-DD}), as dúas ou ningunha.
   *
   * @throws DomainException {@code common.invalid-parameter} se falta unha, están mal escritas,
   *     caen fóra dos últimos 12 meses, {@code desde} vai despois de {@code ata} ou pasan de {@code
   *     maxMonths} meses
   */
  public static OccupancyRange of(
      @Nullable String from, @Nullable String to, LocalDate today, int maxMonths) {
    boolean noFrom = from == null || from.isBlank();
    boolean noTo = to == null || to.isBlank();
    if (noFrom && noTo) {
      return lastFullMonth(today);
    }
    if (noFrom || noTo) {
      throw invalid();
    }
    LocalDate start = parse(from);
    LocalDate end = parse(to);
    if (end.isBefore(start)
        || start.isBefore(availableFrom(today))
        || end.isAfter(availableTo(today))
        || !end.isBefore(start.plusMonths(maxMonths))) {
      throw invalid();
    }
    return new OccupancyRange(start, end);
  }

  /** Cantos días, os dous extremos incluídos. */
  public long days() {
    return ChronoUnit.DAYS.between(from, to) + 1;
  }

  private static LocalDate parse(String value) {
    try {
      return LocalDate.parse(value.strip());
    } catch (DateTimeParseException e) {
      throw invalid();
    }
  }

  private static DomainException invalid() {
    return new DomainException(CommonError.INVALID_PARAMETER, Map.of());
  }
}
