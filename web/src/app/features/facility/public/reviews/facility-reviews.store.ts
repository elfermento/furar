// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../../api/api';
import { getFacilityReviewSummary } from '../../../../api/fn/bookings/get-facility-review-summary';
import { listFacilityReviews } from '../../../../api/fn/bookings/list-facility-reviews';
import type { PublicReviewResponse } from '../../../../api/models/public-review-response';
import type { ReviewSummaryResponse } from '../../../../api/models/review-summary-response';

export type { PublicReviewResponse, ReviewSummaryResponse };

/** Orde da lista: máis recentes (por defecto), máis antigas, nota máis alta e máis baixa. */
export type ReviewOrder = 'NEWEST' | 'OLDEST' | 'HIGHEST' | 'LOWEST';

export const REVIEW_ORDERS: readonly ReviewOrder[] = ['NEWEST', 'OLDEST', 'HIGHEST', 'LOWEST'];

/** Valoracións por páxina na capa. */
export const REVIEWS_PAGE = 10;

/**
 * As valoracións públicas dunha instalación (ADR-0037): a nota media (a fila da tarxeta, que non
 * sae sen valoracións; se non chega, tampouco) e a lista da capa, por páxinas e na orde escollida.
 * Cambiar a orde volve á primeira páxina. Un por ficha.
 */
@Injectable()
export class FacilityReviewsStore {
  private readonly api = inject(Api);

  readonly summary = signal<ReviewSummaryResponse | null>(null);
  readonly items = signal<readonly PublicReviewResponse[]>([]);
  readonly total = signal(0);
  readonly order = signal<ReviewOrder>('NEWEST');
  readonly state = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly loadingMore = signal(false);
  private page = 0;
  /** A petición que vale: unha orde nova deixa sen efecto as anteriores. */
  private request = 0;

  async loadSummary(slug: string, id: string): Promise<void> {
    try {
      this.summary.set(await this.api.invoke(getFacilityReviewSummary, { slug, id }));
    } catch {
      this.summary.set(null);
    }
  }

  /** A primeira páxina na orde `order`. */
  async load(slug: string, id: string, order: ReviewOrder = this.order()): Promise<void> {
    const request = ++this.request;
    this.order.set(order);
    this.state.set('loading');
    try {
      const page = await this.api.invoke(listFacilityReviews, { slug, id, sort: order, page: 0, size: REVIEWS_PAGE });
      if (request !== this.request) return;
      this.page = 0;
      this.items.set(page.items);
      this.total.set(page.totalItems);
      this.state.set('ready');
    } catch {
      if (request === this.request) this.state.set('error');
    }
  }

  /** «Ver máis»: a páxina seguinte, engadida ao final. */
  async more(slug: string, id: string): Promise<void> {
    if (this.state() !== 'ready' || this.loadingMore() || this.items().length >= this.total()) return;
    const request = this.request;
    this.loadingMore.set(true);
    try {
      const next = this.page + 1;
      const page = await this.api.invoke(listFacilityReviews, {
        slug,
        id,
        sort: this.order(),
        page: next,
        size: REVIEWS_PAGE,
      });
      if (request !== this.request) return;
      const seen = new Set(this.items().map((r) => r.id));
      this.items.update((list) => [...list, ...page.items.filter((r) => !seen.has(r.id))]);
      this.total.set(page.totalItems);
      this.page = next;
    } catch {
      // Queda «Ver máis» para tentalo outra vez.
    } finally {
      this.loadingMore.set(false);
    }
  }
}
