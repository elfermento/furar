// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { IndicatorReviewsComponent } from './indicator-reviews.component';
import type { ManagedReviewResponse } from './indicator-reviews.store';

const URL = '/api/v1/municipalities/naron/booking-reviews';
const QUERY = { from: '2026-11-01', to: '2026-11-30', facilityId: null, useIds: [] };

const row = (id: string, stars: number, comment?: string, hidden = false): ManagedReviewResponse => ({
  id,
  facilityId: 'f1',
  facilityName: 'Pavillón da Gándara',
  useId: 'u1',
  useName: 'Baloncesto',
  stars,
  comment,
  hidden,
  createdAt: '2026-11-09T18:00:00Z',
});

const byStars = (counts: number[]) => counts.map((count, i) => ({ stars: 5 - i, count }));

describe('Indicadores: tarxeta «Valoracións» (ADR-0037)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(items: ManagedReviewResponse[], total = items.length) {
    TestBed.configureTestingModule({ providers: provideTestApi(fakeSession()) });
    const fixture = TestBed.createComponent(IndicatorReviewsComponent);
    fixture.componentRef.setInput('slug', 'naron');
    fixture.componentRef.setInput('query', QUERY);
    const el = fixture.nativeElement as HTMLElement;
    const settle = async () => {
      await fixture.whenStable();
      fixture.detectChanges();
    };
    await settle();
    const first = await expectRequest((r) => r.url === URL);
    first.flush({ items, page: 0, size: 10, totalItems: total, withComment: 2, byStars: byStars([1, 1, 0, 1, 0]) });
    await vi.waitFor(async () => {
      await settle();
      expect(el.hidden).toBe(total === 0);
    });
    return { el, settle, first };
  }

  const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');

  it('sen valoracións no período, non sae', async () => {
    const { first } = await render([]);
    expect(first.request.params.get('from')).toBe('2026-11-01');
    expect(first.request.params.get('to')).toBe('2026-11-30');
    expect(first.request.params.has('stars')).toBe(false);
  });

  it('cantas, a táboa co chip «Oculto» e «Sen comentario», e o filtro «Nota»; axe', async () => {
    const { el, settle } = await render(
      [row('a', 5, 'Pista moi ben coidada.'), row('b', 1, 'Comentario feo.', true), row('c', 4)],
      3,
    );
    expect(el.querySelector('[data-reviews-summary]')?.textContent?.trim()).toBe('3 valoracións, 2 con comentario.');
    const rows = [...el.querySelectorAll('[data-review]')];
    expect(rows).toHaveLength(3);
    expect(rows[0]!.querySelector('furar-stars')?.getAttribute('aria-label')).toBe('5 de 5 estrelas');
    expect(rows[1]!.querySelector('[data-review-hidden]')?.textContent?.trim()).toBe('Oculto');
    expect(rows[2]!.textContent).toContain('Sen comentario');
    await expectNoAxeViolations(el);

    const header = el.querySelector<HTMLElement>('.f-ireviews__pills furar-column-filter')!;
    header.querySelector<HTMLButtonElement>('button')!.click();
    await settle();
    const option = document.querySelector<HTMLButtonElement>('[data-column-filter] [data-option="1"]')!;
    expect(option.querySelector('furar-stars')).not.toBeNull();
    expect(option.textContent).toContain('1');
    option.click();
    const filtered = await expectRequest((r) => r.url === URL && r.params.get('stars') === '1');
    filtered.flush({
      items: [row('b', 1, 'Comentario feo.', true)],
      page: 0,
      size: 10,
      totalItems: 1,
      withComment: 1,
      byStars: byStars([1, 1, 0, 1, 0]),
    });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('[data-review]')).toHaveLength(1);
    });
    expect(el.querySelector('[data-reviews-summary]')?.textContent?.trim()).toBe('1 valoración, 1 con comentario.');
  });

  it('a fila abre o detalle e «Ocultar comentario» oculta (a táboa leva o chip) e avisa', async () => {
    const { el, settle } = await render([row('a', 2, 'O céspede ten zonas levantadas.')]);
    el.querySelector<HTMLButtonElement>('[data-open="a"]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).not.toBeNull();
    });
    const detail = dialog()!;
    expect(detail.querySelector('h2')?.textContent?.trim()).toBe('Valoración');
    expect(detail.textContent).toContain('Baloncesto');
    expect(detail.textContent).not.toContain('@');
    await expectNoAxeViolations(detail);
    const hide = detail.querySelector<HTMLButtonElement>('[data-review-hide]')!;
    expect(hide.textContent?.trim()).toBe('Ocultar comentario');
    hide.click();
    const put = await expectRequest((r) => r.url === `${URL}/a/hidden` && r.method === 'PUT');
    expect(put.request.body).toEqual({ hidden: true });
    put.flush(row('a', 2, 'O céspede ten zonas levantadas.', true));
    await vi.waitFor(async () => {
      await settle();
      expect(detail.querySelector('[data-review-hide]')?.textContent?.trim()).toBe('Mostrar comentario');
    });
    expect(detail.querySelector('[data-review-hidden]')).not.toBeNull();
    expect(el.querySelector('[data-review] [data-review-hidden]')).not.toBeNull();
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'reviews.manage.hiddenDone' });

    detail.querySelector<HTMLButtonElement>('[data-review-hide]')!.click();
    flushProblem(await expectRequest((r) => r.url === `${URL}/a/hidden`), 403, 'auth.forbidden');
    await vi.waitFor(async () => {
      await settle();
      expect(detail.querySelector('furar-problem-alert')?.textContent?.trim()).not.toBe('');
    });
  });
});
