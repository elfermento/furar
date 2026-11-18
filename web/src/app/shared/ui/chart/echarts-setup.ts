// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// O único ficheiro que importa ECharts en tempo de execución (ADR-0026): chega só por
// `import()` desde chart-engine.ts, así que vai nun chunk diferido e non na carga inicial.
// Rexístrase só o que usan os construtores de chart-options.ts (do `visualMap`, só o continuo).
import { BarChart, HeatmapChart, LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, VisualMapContinuousComponent } from 'echarts/components';
import { init, use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import type { ChartEngine } from './chart-engine';

use([LineChart, BarChart, HeatmapChart, GridComponent, TooltipComponent, VisualMapContinuousComponent, CanvasRenderer]);

export const ECHARTS_ENGINE: ChartEngine = {
  init: (element) => init(element, null, { renderer: 'canvas' }),
};
