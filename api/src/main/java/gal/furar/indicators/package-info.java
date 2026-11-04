// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Indicadores (docs/glosario.md; plan do dominio 3, §1.2): as cifras e os gráficos do panel de
 * xestión, sempre agregados. Sen táboas nin entidades: le de forma síncrona a API pública de {@code
 * booking} (agregados xa filtrados polo concello) e de {@code facility} (horas abertas e usos). Sen
 * API pública para outros módulos.
 */
@ApplicationModule(
    displayName = "Indicators",
    allowedDependencies = {"shared", "booking", "facility"})
package gal.furar.indicators;

import org.springframework.modulith.ApplicationModule;
