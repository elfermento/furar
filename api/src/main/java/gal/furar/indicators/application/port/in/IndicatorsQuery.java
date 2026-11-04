// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.port.in;

import gal.furar.indicators.domain.PeriodKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que se pide ao panel de indicadores (plan do dominio 3, §2.3).
 *
 * @param kind a unidade natural ou o rango libre
 * @param date a data que ancora a unidade natural; sen ela, hoxe en Europe/Madrid
 * @param from primeiro día do rango libre (só con {@code CUSTOM})
 * @param to último día do rango libre (só con {@code CUSTOM})
 * @param facilityId só esta instalación; {@code null}, todas
 * @param useIds só estes usos, tal como chegan (sen quitar repetidos); baleira, todos
 */
public record IndicatorsQuery(
    PeriodKind kind,
    @Nullable LocalDate date,
    @Nullable LocalDate from,
    @Nullable LocalDate to,
    @Nullable UUID facilityId,
    List<UUID> useIds) {

  public IndicatorsQuery {
    Objects.requireNonNull(kind, "kind");
    useIds = List.copyOf(useIds);
  }
}
