// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../api/api';
import { hideReviewComment } from '../../api/fn/bookings/hide-review-comment';
import { listManagedReviews } from '../../api/fn/bookings/list-managed-reviews';
import type { ManagedReviewResponse } from '../../api/models/managed-review-response';
import type { ManagedReviewsResponse } from '../../api/models/managed-reviews-response';

export type { ManagedReviewResponse, ManagedReviewsResponse };

/** O que se pide: o período do panel e os seus filtros de instalación e uso. */
export interface ReviewsQuery {
  readonly from: string;
  readonly to: string;
  readonly facilityId: string | null;
  readonly useIds: readonly string[];
}

/** Valoracións por páxina na táboa. */
export const REVIEWS_PAGE = 10;

/**
 * A táboa «Valoracións» dos indicadores (ADR-0037): as do período e os filtros do panel, coa nota
 * escollida na cabeceira, por páxinas («Ver máis»); ocultar ou mostrar un comentario cambia a
 * fila no sitio. A petición máis nova manda: as anteriores en voo non pisan nada. Unha por panel.
 */
@Injectable()
export class IndicatorReviewsStore {
  private readonly api = inject(Api);

  readonly page = signal<ManagedReviewsResponse | null>(null);
  readonly items = signal<readonly ManagedReviewResponse[]>([]);
  readonly stars = signal<readonly number[]>([]);
  readonly state = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly loadingMore = signal(false);
  private request = 0;
  private current = 0;

  async load(slug: string, query: ReviewsQuery, stars: readonly number[] = this.stars()): Promise<void> {
    const request = ++this.request;
    this.stars.set(stars);
    this.state.set('loading');
    try {
      const page = await this.api.invoke(listManagedReviews, this.params(slug, query, 0));
      if (request !== this.request) return;
      this.current = 0;
      this.page.set(page);
      this.items.set(page.items);
      this.state.set('ready');
    } catch {
      if (request === this.request) this.state.set('error');
    }
  }

  async more(slug: string, query: ReviewsQuery): Promise<void> {
    const page = this.page();
    if (!page || this.loadingMore() || this.items().length >= page.totalItems) return;
    const request = this.request;
    this.loadingMore.set(true);
    try {
      const next = await this.api.invoke(listManagedReviews, this.params(slug, query, this.current + 1));
      if (request !== this.request) return;
      const seen = new Set(this.items().map((r) => r.id));
      this.items.update((list) => [...list, ...next.items.filter((r) => !seen.has(r.id))]);
      this.page.set({ ...next, items: this.items() as ManagedReviewResponse[] });
      this.current += 1;
    } catch {
      // Queda «Ver máis» para tentalo outra vez.
    } finally {
      this.loadingMore.set(false);
    }
  }

  /** Oculta ou volve mostrar o comentario; a fila pasa a ser a resposta. Os erros, a quen chama. */
  async hide(slug: string, review: ManagedReviewResponse, hidden: boolean): Promise<ManagedReviewResponse> {
    const changed = await this.api.invoke(hideReviewComment, { slug, id: review.id, body: { hidden } });
    this.items.update((list) => list.map((r) => (r.id === changed.id ? changed : r)));
    return changed;
  }

  private params(slug: string, query: ReviewsQuery, page: number) {
    return {
      slug,
      from: query.from,
      to: query.to,
      facilityId: query.facilityId ?? undefined,
      useIds: query.useIds.length ? [...query.useIds] : undefined,
      stars: this.stars().length ? [...this.stars()] : undefined,
      page,
      size: REVIEWS_PAGE,
    };
  }
}
