// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { InjectionToken } from '@angular/core';
import type { ChartOption } from './chart-options';

/** O que furar-chart usa dunha instancia de ECharts (os specs dan un motor falso). */
export interface ChartInstance {
  setOption(option: ChartOption, settings: { notMerge: boolean }): void;
  resize(): void;
  dispose(): void;
  getDataURL(settings: { type: 'png'; pixelRatio: number; backgroundColor: string }): string;
}

export interface ChartEngine {
  /** Crea o gráfico en `element`, sempre co renderizador canvas (CSP, ADR-0026). */
  init(element: HTMLElement): ChartInstance;
}

export type ChartEngineLoader = () => Promise<ChartEngine>;

let loading: Promise<ChartEngine> | undefined;

/** Carga ECharts unha soa vez por páxina, cando aparece o primeiro gráfico (chunk diferido). */
export function loadEcharts(): Promise<ChartEngine> {
  loading ??= import('./echarts-setup').then((module) => module.ECHARTS_ENGINE);
  return loading;
}

/** Motor dos gráficos: ECharts por defecto; os specs e o catálogo poden dar outro. */
export const CHART_ENGINE = new InjectionToken<ChartEngineLoader>('CHART_ENGINE', {
  providedIn: 'root',
  factory: () => loadEcharts,
});
