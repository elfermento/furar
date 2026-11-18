// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Indicadores (docs/glosario.md): as cifras e os gráficos do panel de xestión, sempre agregados.
 * Sen táboas nin entidades: le de forma síncrona a API pública de {@code booking} (agregados xa
 * filtrados polo concello) e de {@code facility} (horas abertas e usos). API pública: {@code
 * IndicatorsQueries}, a ocupación por tramos de 15 minutos para os datos abertos (ADR-0034). Os
 * indicadores da plataforma calculan o panel de cada concello no seu contexto e len os nomes de
 * {@code municipality} (ADR-0026, revisión do 2026-11-11).
 */
@ApplicationModule(
    displayName = "Indicators",
    allowedDependencies = {"shared", "booking", "facility", "municipality"})
package gal.furar.indicators;

import org.springframework.modulith.ApplicationModule;
