// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.port.in;

import gal.furar.shared.domain.DomainException;

/**
 * O panel de indicadores do concello (ADR-0026; plan do dominio 3, §2.3): todo nunha lectura,
 * sempre agregado. Permiso {@code indicators.view}.
 */
public interface ReadIndicatorsUseCase {

  /**
   * @throws DomainException {@code indicators.period-invalid} se o período non é válido (rango
   *     libre sen datas, ao revés ou de máis de 366 días, ou datas fóra de 2000 … hoxe + 2 anos);
   *     {@code indicators.filter-invalid} se a instalación ou algún uso non son do concello, se un
   *     uso non é da instalación escollida, ou se hai usos repetidos ou máis de 50
   */
  IndicatorsPanel read(IndicatorsQuery query);
}
