// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As reservas que empezan en {@code [since, until)}, xa convertido a instantes, cos filtros dos
 * indicadores.
 *
 * @param facilityId só esta instalación; {@code null}, todas
 * @param useIds só estes usos; baleiro, todos
 */
public record StatsWindow(
    Instant since, Instant until, @Nullable UUID facilityId, Set<UUID> useIds) {

  public StatsWindow {
    Objects.requireNonNull(since, "since");
    Objects.requireNonNull(until, "until");
    useIds = Set.copyOf(useIds);
  }
}
