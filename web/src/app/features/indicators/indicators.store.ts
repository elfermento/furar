// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Api } from '../../api/api';
import { getIndicatorFilters } from '../../api/fn/indicators/get-indicator-filters';
import { getIndicators } from '../../api/fn/indicators/get-indicators';
import type { IndicatorFiltersResponse } from '../../api/models/indicator-filters-response';
import type { IndicatorsResponse } from '../../api/models/indicators-response';
import { AbortableApi } from '../../core/abortable-api';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import type { IndicatorFilters } from './indicator-filters';
import type { KpiId } from './indicator-kpis';
import { panelParams, useIdsFor } from './indicator-filters';

export type { IndicatorFiltersResponse, IndicatorsResponse };
export type { GetIndicators$Params } from '../../api/fn/indicators/get-indicators';
export type { IndicatorBucketOccupancyResponse } from '../../api/models/indicator-bucket-occupancy-response';
export type { IndicatorCancellationsResponse } from '../../api/models/indicator-cancellations-response';
export type { IndicatorOccupancyResponse } from '../../api/models/indicator-occupancy-response';
export type { IndicatorPeopleResponse } from '../../api/models/indicator-people-response';
export type { IndicatorPeriodResponse } from '../../api/models/indicator-period-response';
export type { IndicatorRevenueResponse } from '../../api/models/indicator-revenue-response';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Indicadores do concello (D3-08): a única porta ao cliente xerado desta feature. Pide as opcións
 * dos filtros (instalacións e usos) e o panel dos filtros do URL. Cada petición do panel cancela
 * a anterior (cambiar os filtros deprisa non deixa respostas superadas en voo) e a que se ve é
 * sempre a última. Un por páxina; ao saír, a que estea en voo cancélase.
 */
@Injectable()
export class IndicatorsStore {
  private readonly api = inject(Api);
  private readonly abortable = inject(AbortableApi);
  private current: AbortController | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.current?.abort());
  }

  readonly options = signal<IndicatorFiltersResponse | null>(null);
  readonly panel = signal<IndicatorsResponse | null>(null);
  /** Os filtros cos que se pediu o panel que se ve. */
  readonly loaded = signal<IndicatorFilters | null>(null);
  readonly state = signal<LoadState>('idle');
  readonly problem = signal<ApiProblem | null>(null);
  /** A cifra que se pediu abrir (D3-09: o modal coa súa evolución). */
  readonly selectedKpi = signal<KpiId | null>(null);

  /** As opcións dos filtros; se fallan, os despregables quedan só con «Todas» e «Todos». */
  async loadOptions(slug: string): Promise<void> {
    try {
      this.options.set(await this.api.invoke(getIndicatorFilters, { slug }));
    } catch {
      this.options.set(null);
    }
  }

  /** Pide o panel; mentres chega, o anterior segue á vista (o estado di que se está a cargar). */
  async load(slug: string, filters: IndicatorFilters): Promise<void> {
    this.current?.abort();
    const request = new AbortController();
    this.current = request;
    this.state.set('loading');
    this.problem.set(null);
    try {
      const params = panelParams(slug, filters, useIdsFor(filters, this.options()));
      const panel = await this.abortable.invoke(getIndicators, params, request.signal);
      if (request.signal.aborted) return;
      this.panel.set(panel);
      this.loaded.set(filters);
      this.state.set('ready');
    } catch (error) {
      if (request.signal.aborted) return;
      this.problem.set(asProblem(error));
      this.state.set('error');
    }
  }
}
