// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O filtro dos agregados de {@link BookingStatistics}: as reservas que empezan entre {@code from} e
 * {@code to}, ambos incluídos, en Europe/Madrid ({@code [from 00:00, to+1 00:00)}).
 *
 * <p>Tope defensivo de {@value #MAX_YEARS} anos e un día ({@code to ≤ from + 5 anos}): a evolución
 * anual dos indicadores son cinco anos e ningunha consulta debe percorrer o histórico enteiro. Quen
 * chama desde HTTP valida antes e responde 400; aquí un rango mal feito é un erro de programación.
 *
 * @param from primeiro día
 * @param to último día, igual ou posterior a {@code from} e como moito {@code from + 5 anos}
 * @param facilityId só esta instalación; {@code null}, todas
 * @param useIds só estes usos; baleiro, todos
 */
public record StatsQuery(
    LocalDate from, LocalDate to, @Nullable UUID facilityId, Set<UUID> useIds) {

  /** Anos como moito entre {@code from} e {@code to} (máis un día). */
  public static final int MAX_YEARS = 5;

  public StatsQuery {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to " + to + " is before from " + from);
    }
    if (to.isAfter(from.plusYears(MAX_YEARS))) {
      throw new IllegalArgumentException("The range " + from + ".." + to + " is too long");
    }
    useIds = Set.copyOf(useIds);
  }
}
