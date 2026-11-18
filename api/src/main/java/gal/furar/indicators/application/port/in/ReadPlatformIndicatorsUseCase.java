// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.port.in;

import gal.furar.indicators.domain.PeriodKind;
import gal.furar.shared.domain.DomainException;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Os indicadores da plataforma (ADR-0026, revisión do 2026-11-11): as cifras de todos os concellos
 * nun período, sen datos de ningunha persoa. Permiso {@code indicators.platform-view}.
 */
public interface ReadPlatformIndicatorsUseCase {

  /**
   * @param kind a unidade natural ou o rango libre
   * @param date a data que ancora a unidade natural; sen ela, hoxe en Europe/Madrid
   * @param from primeiro día do rango libre (só con {@code CUSTOM})
   * @param to último día do rango libre (só con {@code CUSTOM})
   * @throws DomainException {@code indicators.period-invalid} se o período non é válido, como no
   *     panel do concello; {@code indicators.too-many-municipalities} se hai máis concellos dos que
   *     se calculan nunha petición
   */
  PlatformIndicators read(
      PeriodKind kind, @Nullable LocalDate date, @Nullable LocalDate from, @Nullable LocalDate to);
}
