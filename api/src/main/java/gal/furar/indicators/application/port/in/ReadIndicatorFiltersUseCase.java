// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.port.in;

/**
 * As instalacións e os usos que se poden escoller no panel de indicadores. Permiso {@code
 * indicators.view}.
 */
public interface ReadIndicatorFiltersUseCase {

  IndicatorFilters filters();
}
