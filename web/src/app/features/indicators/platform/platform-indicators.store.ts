// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { getPlatformIndicators } from '../../../api/fn/indicators/get-platform-indicators';
import { AbortableApi } from '../../../core/abortable-api';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import type { IndicatorFilters } from '../indicator-filters';
import { periodParams } from '../indicator-filters';
import type { PlatformIndicatorsResponse } from '../../../api/models/platform-indicators-response';
import type { PlatformMunicipalityIndicatorsResponse } from '../../../api/models/platform-municipality-indicators-response';

export type { PlatformIndicatorsResponse, PlatformMunicipalityIndicatorsResponse };

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Indicadores da plataforma (`GET /api/v1/indicators`): a única porta ao cliente xerado da páxina.
 * Cada petición cancela a anterior e a que se ve é sempre a última. Un por páxina.
 */
@Injectable()
export class PlatformIndicatorsStore {
  private readonly abortable = inject(AbortableApi);
  private current: AbortController | null = null;

  readonly data = signal<PlatformIndicatorsResponse | null>(null);
  readonly state = signal<LoadState>('idle');
  readonly problem = signal<ApiProblem | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.current?.abort());
  }

  /** Pide o período; mentres chega, o anterior segue á vista. */
  async load(filters: Pick<IndicatorFilters, 'period' | 'from' | 'to'>): Promise<void> {
    this.current?.abort();
    const request = new AbortController();
    this.current = request;
    this.state.set('loading');
    this.problem.set(null);
    try {
      const data = await this.abortable.invoke(getPlatformIndicators, periodParams(filters), request.signal);
      if (request.signal.aborted) return;
      this.data.set(data);
      this.state.set('ready');
    } catch (error) {
      if (request.signal.aborted) return;
      this.problem.set(asProblem(error));
      this.state.set('error');
    }
  }
}
