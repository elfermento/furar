// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../api/api';
import { listMunicipalities } from '../../api/fn/municipalities/list-municipalities';
import type { MunicipalitySummaryResponse } from '../../api/models/municipality-summary-response';

/** O máximo que acepta a API por páxina. */
const PAGE_SIZE = 100;

/**
 * Os concellos da instalación (`GET /api/v1/municipalities`, público): a raíz, a entrada da
 * plataforma (sen ningún, á alta) e «Concellos ›» no seu menú. Garda a última lista lida; a alta
 * dun concello invalídaa.
 */
@Injectable({ providedIn: 'root' })
export class MunicipalityDirectory {
  private readonly api = inject(Api);
  private readonly loaded = signal<readonly MunicipalitySummaryResponse[] | null>(null);
  private pending: Promise<MunicipalitySummaryResponse[]> | null = null;

  /** A última lista lida; `null` mentres non se leu ou despois de invalidala. */
  readonly municipalities = this.loaded.asReadonly();

  /** A gardada ou, se non hai, a da API. */
  list(): Promise<readonly MunicipalitySummaryResponse[]> {
    const loaded = this.loaded();
    if (loaded) return Promise.resolve(loaded);
    return this.pending ?? this.fetch();
  }

  /** Sempre da API (páxinas de 100); queda gardada. */
  fetch(): Promise<MunicipalitySummaryResponse[]> {
    const promise = this.fetchAll();
    const settled = promise.then(
      (items) => {
        if (this.pending === settled) {
          this.pending = null;
          this.loaded.set(items);
        }
        return items;
      },
      (error: unknown) => {
        if (this.pending === settled) this.pending = null;
        throw error;
      },
    );
    this.pending = settled;
    return settled;
  }

  /** Despois de dar de alta un concello: a seguinte lectura vai á API. */
  invalidate(): void {
    this.pending = null;
    this.loaded.set(null);
  }

  private async fetchAll(): Promise<MunicipalitySummaryResponse[]> {
    const items: MunicipalitySummaryResponse[] = [];
    for (let page = 0; ; page++) {
      const result = await this.api.invoke(listMunicipalities, { page, size: PAGE_SIZE });
      items.push(...result.items);
      if (items.length >= result.totalItems || result.items.length === 0) return items;
    }
  }
}
