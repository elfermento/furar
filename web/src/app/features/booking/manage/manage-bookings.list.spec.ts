// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { TestRequest } from '@angular/common/http/testing';
import { expectRequest } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { REFUND, dialog, isList, page, renderManage, row, text, useMonday } from '../../../../testing/manage-bookings';

describe('Reservas do concello (D2-15): lista, filtros e busca', () => {
  useMonday();

  it('«Ver máis» de «A devolver» leva á lista cos seus filtros en chips; volver deixa a portada', async () => {
    const { el, settle } = await renderManage();
    (el.querySelectorAll('[data-section="payments"] [role="tab"]')[1] as HTMLButtonElement).click();
    await settle();
    el.querySelector<HTMLButtonElement>('[data-more="payments"]')!.click();
    const request = await expectRequest(isList);
    expect(request.request.params.getAll('payment')).toEqual(['TO_REFUND']);
    expect(request.request.params.get('from')).toBeNull();
    request.flush(page([REFUND], 1));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('.f-manage__list .f-mrow')).not.toBeNull();
    });
    expect(text(el.querySelector('.f-manage__list h2'))).toBe('A devolver');
    expect(text(el.querySelector('[data-manage-count]'))).toBe('1 reserva');
    expect([...el.querySelectorAll('[data-active]')].map((c) => text(c))).toEqual(['A devolver']);
    expect(text(el.querySelector('[data-filter-count]'))).toContain('1');
    expect(text(el.querySelector('[data-detail-card]'))).toBe('Escolle unha reserva para ver os seus datos.');
    expect(text(el.querySelector('.f-manage__list [data-row-made]'))).toContain('cancelada polo concello');
    await expectNoAxeViolations(el);

    // Quitar o chip pide de novo (sen filtros: as próximas desde hoxe, pero co título «Resultados»).
    el.querySelector<HTMLButtonElement>('[data-active] button')!.click();
    const again = await expectRequest(isList);
    expect(again.request.params.get('payment')).toBeNull();
    again.flush(page([]));
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('.f-manage__list h2'))).toBe('Resultados');
    });

    el.querySelector<HTMLButtonElement>('[data-manage-back]')!.click();
    await settle();
    expect(el.querySelector('[data-section="latest"]')).not.toBeNull();
    expect(el.querySelector('[data-filter-count]')).toBeNull();
  });

  it('filtros: sen marcar ao abrir, chips co ton da paleta, «Marcar todas» ao día, datas cun atallo; Ver reservas', async () => {
    const { el, settle } = await renderManage();
    el.querySelector<HTMLButtonElement>('[data-manage-filters]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-filters-apply]')).toBeTruthy();
    });
    const d = dialog()!;
    const chips = (group: string) => [
      ...d.querySelectorAll<HTMLButtonElement>(`[data-filter="${group}"] .f-filter-chip`),
    ];
    expect(
      [...chips('facility'), ...chips('payment'), ...chips('state')].every(
        (c) => c.getAttribute('aria-pressed') === 'false',
      ),
    ).toBe(true);
    expect(chips('payment').map((c) => [text(c), c.getAttribute('data-tone')])).toEqual([
      ['Pendente', 'warn'],
      ['Pagada', 'success'],
      ['A devolver', 'danger'],
      ['Devolta', 'muted'],
      ['Gratis', 'info'],
    ]);
    expect(chips('facility').every((c) => !c.hasAttribute('data-tone'))).toBe(true);
    expect(d.querySelector('[role="radio"][aria-checked="true"]')?.textContent?.trim()).toBe('Por día e hora');
    await expectNoAxeViolations(d);

    const all = d.querySelector<HTMLButtonElement>('[data-all="facility"]')!;
    expect(text(all)).toBe('Marcar todas');
    chips('facility').forEach((c) => c.click());
    await settle();
    expect(text(all)).toBe('Desmarcar todas');
    all.click();
    await settle();
    expect(chips('facility').map((c) => c.getAttribute('aria-pressed'))).toEqual(['false', 'false']);
    expect(text(all)).toBe('Marcar todas');
    chips('facility')[0].click();
    chips('payment')[0].click();
    await settle();
    expect(chips('payment')[0].getAttribute('aria-pressed')).toBe('true');

    // Datas: a ligazón abre o panel; «Hoxe» e «Aplicar» deixan un chip coa ✕.
    d.querySelector<HTMLButtonElement>('[data-date-add]')!.click();
    await settle();
    expect(d.querySelector('[data-date-add]')?.getAttribute('aria-expanded')).toBe('true');
    d.querySelector<HTMLButtonElement>('[data-preset="today"]')!.click();
    await settle();
    expect(d.querySelectorAll('.f-calendar__day[aria-selected="true"]').length).toBe(1);
    d.querySelector<HTMLButtonElement>('[data-date-apply]')!.click();
    await settle();
    expect(d.querySelector('[data-date-panel]')).toBeNull();
    expect(text(d.querySelector('[data-date-chip]'))).toBe('Hoxe');
    await expectNoAxeViolations(d);

    d.querySelector<HTMLButtonElement>('[data-filters-apply]')!.click();
    const request = await expectRequest(isList);
    const params = request.request.params;
    expect([params.get('from'), params.get('to'), params.getAll('facilityId'), params.getAll('payment')]).toEqual([
      '2026-10-26',
      '2026-10-26',
      ['f1'],
      ['PENDING'],
    ]);
    request.flush(page([row()]));
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('.f-manage__list h2'))).toBe('Resultados');
    });
    expect([...el.querySelectorAll('[data-active]')].map((c) => text(c))).toEqual([
      'Hoxe',
      'Pista de pádel 1',
      'Pendente',
    ]);
    expect(text(el.querySelector('[data-filter-count]'))).toContain('3');
  });

  it('no panel das datas, Esc pecha só o panel e un rango marca os días do medio; «Quitar filtros» baléirao todo', async () => {
    const { el, settle } = await renderManage();
    el.querySelector<HTMLButtonElement>('[data-manage-filters]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-date-add]')).toBeTruthy();
    });
    const d = dialog()!;
    d.querySelector<HTMLButtonElement>('[data-date-add]')!.click();
    await settle();
    const day = (iso: string) => d.querySelector<HTMLElement>(`[data-date="${iso}"]`)!;
    day('2026-10-27').click();
    await settle();
    day('2026-10-30').click();
    await settle();
    expect(day('2026-10-28').hasAttribute('data-in-range')).toBe(true);
    expect(day('2026-10-30').getAttribute('aria-selected')).toBe('true');
    d.querySelector('[data-date-panel]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(d.querySelector('[data-date-panel]')).toBeNull();
    expect(dialog()).not.toBeNull();
    expect(text(d.querySelector('[data-date-chip]'))).toBe('Do 27/10 ao 30/10');

    // Picar fóra do panel tamén o pecha.
    d.querySelector<HTMLButtonElement>('[data-date-chip] button')!.click();
    await settle();
    expect(d.querySelector('[data-date-panel]')).not.toBeNull();
    d.querySelector('.f-dialog__title')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();
    expect(d.querySelector('[data-date-panel]')).toBeNull();

    d.querySelector<HTMLButtonElement>('[data-filters-clear]')!.click();
    await settle();
    expect(d.querySelector('[data-date-chip]')).toBeNull();
    d.querySelector<HTMLButtonElement>('[data-filters-apply]')!.click();
    const request = await expectRequest(isList);
    expect(request.request.params.get('from')).toBe('2026-10-26');
    request.flush(page([]));
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('.f-manage__list h2'))).toBe('Próximas');
    });
  });

  it('a busca leva aos resultados co texto en `q`; baleira, volve á portada', async () => {
    const { el, settle } = await renderManage();
    const input = el.querySelector<HTMLInputElement>('[data-manage-search]')!;
    input.value = 'FUR-7K2Q';
    input.dispatchEvent(new Event('input'));
    const request: TestRequest = await expectRequest(isList);
    expect(request.request.params.get('q')).toBe('FUR-7K2Q');
    request.flush(page([row()]));
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('.f-manage__list h2'))).toBe('Resultados');
    });
    input.value = '';
    input.dispatchEvent(new Event('input'));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-section="latest"]')).not.toBeNull();
    });
  });

  it('filtros: «Marcar todos» no pago, a orde e pechar sen «Ver reservas» non cambia nada', async () => {
    const { el, settle } = await renderManage();
    el.querySelector<HTMLButtonElement>('[data-manage-filters]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-all="payment"]')).toBeTruthy();
    });
    const d = dialog()!;
    const all = d.querySelector<HTMLButtonElement>('[data-all="payment"]')!;
    all.click();
    await settle();
    expect([...d.querySelectorAll('[data-filter="payment"] .f-filter-chip[aria-pressed="true"]')]).toHaveLength(5);
    expect(text(all)).toBe('Desmarcar todos');
    d.querySelectorAll<HTMLButtonElement>('[data-filter="state"] .f-filter-chip')[1].click();
    d.querySelectorAll<HTMLButtonElement>('[role="radio"]')[0].click();
    await settle();
    expect(d.querySelector('[role="radio"][aria-checked="true"]')?.textContent?.trim()).toBe('Máis recentes primeiro');
    d.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
    });
    expect(el.querySelector('[data-section="latest"]')).not.toBeNull();
    expect(el.querySelector('[data-filter-count]')).toBeNull();

    el.querySelector<HTMLButtonElement>('[data-manage-filters]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-filters-apply]')).toBeTruthy();
    });
    dialog()!.querySelectorAll<HTMLButtonElement>('[data-filter="state"] .f-filter-chip')[1].click();
    dialog()!.querySelector<HTMLButtonElement>('[data-filters-apply]')!.click();
    const request = await expectRequest(isList);
    expect(request.request.params.getAll('status')).toEqual(['CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY']);
    request.flush(page([REFUND], 30));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-list-more]')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('[data-list-more]')!.click();
    const more = await expectRequest((r) => isList(r) && r.params.get('page') === '1');
    more.flush({ ...page([row({ id: 'b9' })], 30), page: 1 });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('.f-manage__list .f-mrow')).toHaveLength(2);
    });
  });
});
