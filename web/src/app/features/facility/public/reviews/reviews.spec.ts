// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, provideTestApi } from '../../../../../testing/api';
import { expectNoAxeViolations } from '../../../../../testing/axe';
import { pickOption, selectText } from '../../../../../testing/select';
import { FacilityRatingRowComponent } from './rating-row.component';
import { reviewDate } from './review-date';

const BASE = '/api/v1/municipalities/naron/public/facilities/f1/reviews';

const review = (n: number, stars: number, comment?: string) => ({
  id: `r${n}`,
  stars,
  comment,
  createdAt: `2026-11-${String(20 - n).padStart(2, '0')}T10:00:00Z`,
});

describe('Valoracións na ficha pública (ADR-0037)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(summary: { reviews: number; average?: number }) {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const fixture = TestBed.createComponent(FacilityRatingRowComponent);
    fixture.componentRef.setInput('slug', 'naron');
    fixture.componentRef.setInput('facilityId', 'f1');
    fixture.componentRef.setInput('facilityName', 'Pavillón da Gándara');
    const el = fixture.nativeElement as HTMLElement;
    const settle = async () => {
      await fixture.whenStable();
      fixture.detectChanges();
    };
    (await expectRequest(`${BASE}/summary`)).flush(summary);
    await vi.waitFor(async () => {
      await settle();
      expect(el.hidden).toBe(!summary.reviews);
    });
    return { el, settle };
  }

  const layer = () => document.querySelector<HTMLElement>('furar-dialog-container');
  const stars = () =>
    [...layer()!.querySelectorAll('[data-review] furar-stars')].map((s) => s.getAttribute('data-stars'));

  it('sen valoracións, a fila non sae', async () => {
    const { el } = await render({ reviews: 0 });
    expect(el.hidden).toBe(true);
  });

  it('a fila di a media e cantas e abre a capa con «Ordenar por», a lista e «Ver máis»; axe', async () => {
    const { el, settle } = await render({ reviews: 12, average: 4.583 });
    expect(el.hidden).toBe(false);
    expect(el.querySelector('h2')?.textContent?.trim()).toBe('Valoración');
    const open = el.querySelector<HTMLButtonElement>('[data-public-rating-open]')!;
    expect(open.textContent?.trim()).toBe('4,6 de 5 · 12 valoracións');
    expect(open.getAttribute('aria-haspopup')).toBe('dialog');

    open.click();
    const first = await expectRequest((r) => r.url === BASE && r.params.get('sort') === 'NEWEST');
    expect(first.request.params.get('page')).toBe('0');
    first.flush({
      items: Array.from({ length: 10 }, (_, i) =>
        review(i, 5 - (i % 5), i === 0 ? 'Pista moi ben coidada.' : undefined),
      ),
      page: 0,
      size: 10,
      totalItems: 12,
    });
    await vi.waitFor(async () => {
      await settle();
      expect(layer()?.querySelector('[data-review]')).not.toBeNull();
    });
    const dialog = layer()!;
    expect(dialog.querySelector('h2')?.textContent?.trim()).toBe('Valoracións');
    expect(dialog.querySelector('[data-reviews-average]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      '4,6 de 5 12 valoracións',
    );
    // «Ordenar por» vese diante do despregable e nomeao.
    const label = dialog.querySelector<HTMLLabelElement>('.f-reviews__sort label')!;
    expect(label.textContent?.trim()).toBe('Ordenar por');
    expect(selectText(dialog.querySelector('[data-reviews-sort]')!)).toBe('Máis recentes');
    const firstItem = dialog.querySelector('[data-review="r0"]')!;
    expect(firstItem.querySelector('time')?.textContent?.trim()).toMatch(/^20 de novembro/);
    expect(firstItem.querySelector('[data-review-text]')?.textContent?.trim()).toBe('Pista moi ben coidada.');
    expect(dialog.querySelector('[data-review="r1"] [data-review-text]')).toBeNull();
    await expectNoAxeViolations(dialog);

    dialog.querySelector<HTMLButtonElement>('[data-reviews-more]')!.click();
    const more = await expectRequest((r) => r.url === BASE && r.params.get('page') === '1');
    more.flush({ items: [review(10, 1), review(11, 2)], page: 1, size: 10, totalItems: 12 });
    await vi.waitFor(async () => {
      await settle();
      expect(dialog.querySelectorAll('[data-review]')).toHaveLength(12);
    });
    expect(dialog.querySelector('[data-reviews-more]')).toBeNull();

    // Outra orde volve á primeira páxina.
    await pickOption(dialog.querySelector('[data-reviews-sort]')!, 'Nota máis baixa', settle);
    const lowest = await expectRequest((r) => r.url === BASE && r.params.get('sort') === 'LOWEST');
    expect(lowest.request.params.get('page')).toBe('0');
    lowest.flush({ items: [review(1, 1), review(2, 2)], page: 0, size: 10, totalItems: 12 });
    await vi.waitFor(async () => {
      await settle();
      expect(stars()).toEqual(['1', '2']);
    });
  });
});

describe('A data dunha valoración', () => {
  it('a data: sen ano no ano en curso; con el, doutro ano', () => {
    expect(reviewDate('2026-11-10T10:00:00Z', 'gl', '2026-11-11')).toBe('10 de novembro');
    expect(reviewDate('2025-12-31T23:30:00Z', 'gl', '2026-11-11')).toBe('1 de xaneiro');
    expect(reviewDate('2025-06-01T10:00:00Z', 'es', '2026-11-11')).toBe('1 de junio de 2025');
  });
});
