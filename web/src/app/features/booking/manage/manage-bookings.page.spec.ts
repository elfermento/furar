// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Router } from '@angular/router';
import { expectRequest, fakeSession, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import {
  BASE,
  OVERVIEW,
  SUMMARY_URL,
  detail,
  dialog,
  openDetail,
  renderManage,
  rowsOf,
  text,
  useMonday,
} from '../../../../testing/manage-bookings';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { TestBed } from '@angular/core/testing';

describe('Reservas do concello (D2-15): portada e detalle', () => {
  useMonday();

  it('portada: últimas reservas e cobros con contas, etiquetas coa paleta e Calendario; axe', async () => {
    const { el, settle } = await renderManage();
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Reservas');
    expect(el.querySelector('[data-manage-calendar]')?.getAttribute('href')).toBe('/naron/xestion/calendario');
    expect(el.querySelector('[data-filter-count]')).toBeNull();

    const [ana, iago, free] = rowsOf(el, 'latest');
    expect(text(ana.querySelector('.f-mrow__name'))).toBe('Ana Rey');
    expect(text(ana.querySelector('.f-mrow__meta'))).toBe('Pista de pádel 1 · Pista completa');
    expect(text(ana.querySelector('.f-mrow__when'))).toBe('hoxe, lun 26 · 19:00–20:00');
    expect(text(ana.querySelector('[data-row-made]'))).toMatch(/^Reservada /);
    expect(text(ana.querySelector('.f-mrow__amount'))).toBe('8,00 €');
    const chip = (button: Element) => button.querySelector('furar-tag')!;
    expect(
      [chip(ana), chip(iago), chip(free)].map((c) => [c.getAttribute('data-tone'), c.getAttribute('data-look')]),
    ).toEqual([
      ['warn', 'solid'],
      ['success', 'solid'],
      ['info', 'solid'],
    ]);
    expect(free.querySelector('.f-mrow__amount')).toBeNull();
    expect(ana.getAttribute('aria-pressed')).toBe('false');

    const tabs = [...el.querySelectorAll('[data-section="payments"] [role="tab"]')];
    expect(tabs.map((t) => [text(t), t.getAttribute('aria-selected')])).toEqual([
      ['Pendentes de pago 5', 'true'],
      ['A devolver 1', 'false'],
      ['Canceladas 2', 'false'],
    ]);
    expect(rowsOf(el, 'payments').map((r) => text(r.querySelector('.f-mrow__name')))).toEqual(['Ana Rey']);
    expect(rowsOf(el, 'payments')[0].querySelector('[data-row-made]')).toBeNull();
    (tabs[1] as HTMLButtonElement).click();
    await settle();
    expect(rowsOf(el, 'payments').map((r) => chip(r).getAttribute('data-tone'))).toEqual(['danger']);
    await expectNoAxeViolations(el);
  });

  it('picar nunha reserva: a súa sección queda soa e o detalle ao lado; picar de novo, Esc ou fóra pecha', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle);
    expect(el.querySelector('[data-section="payments"]')).toBeNull();
    expect(el.querySelector('.f-manage__home')?.getAttribute('data-detail')).toBe('latest');
    expect(rowsOf(el, 'latest')[0].getAttribute('aria-pressed')).toBe('true');
    expect(text(card.querySelector('[data-detail-facility] b'))).toBe('Pista de pádel 1');
    expect(text(card.querySelector('[data-detail-facility] span'))).toBe('Rúa Buxo, 16 · Narón');
    const values = [...card.querySelectorAll('[data-value]')].map((r) => [
      r.getAttribute('data-value'),
      text(r.querySelector('dd')),
    ]);
    expect(values).toEqual([
      ['person', 'Ana Rey'],
      ['use', 'Pista completa'],
      ['day', 'luns, 26 de outubro'],
      ['time', '19:00–20:00'],
      ['code', 'FUR-7K2Q'],
    ]);
    expect(card.querySelector('[data-detail-email]')?.getAttribute('href')).toBe('mailto:ana@correo.example');
    expect(text(card.querySelector('[data-detail-total]'))).toBe('Total 8,00 € Pendente de pago');
    expect(text(card.querySelector('[data-payment="PAID"]'))).toBe('Marcar como pagada');
    expect(card.querySelector('[data-detail-cancel]')).not.toBeNull();
    await expectNoAxeViolations(el);

    // Picar de novo na mesma fila pecha.
    rowsOf(el, 'latest')[0].click();
    await settle();
    expect(el.querySelector('[data-detail-card]')).toBeNull();
    expect(el.querySelector('[data-section="payments"]')).not.toBeNull();

    // Esc pecha, tamén co foco na fila que o abriu (regresión D2-17).
    await openDetail(el, settle);
    rowsOf(el, 'latest')[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(el.querySelector('[data-detail-card]')).toBeNull();

    // Picar fóra (no título) pecha; dentro do detalle, non.
    const again = await openDetail(el, settle);
    again.querySelector('dl')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();
    expect(el.querySelector('[data-detail-card]')).not.toBeNull();
    el.querySelector('h1')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();
    expect(el.querySelector('[data-detail-card]')).toBeNull();
  });

  it('marcar como pagada coa versión; anúnciase, cambia a etiqueta e a portada pídese de novo', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle);
    card.querySelector<HTMLButtonElement>('[data-payment="PAID"]')!.click();
    const put = await expectRequest(`${BASE}/bookings/b1/payment`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({ status: 'PAID' });
    put.flush(detail({ paymentStatus: 'PAID', paymentTargets: ['PENDING'], version: 4 }));
    (await expectRequest((r) => r.url === SUMMARY_URL)).flush(OVERVIEW);
    await vi.waitFor(async () => {
      await settle();
      expect(TestBed.inject(ToastService).current()?.key).toBe('booking.manage.detail.done.markPaid');
    });
    expect(card.querySelector('[data-detail-total] furar-tag')?.getAttribute('data-tone')).toBe('success');
    expect(text(card.querySelector('[data-payment="PENDING"]'))).toBe('Desfacer: volver a pendente');
  });

  it('unha transición que xa non vale (422) queda nun aviso; 412, aviso con «Recargar»', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle);
    card.querySelector<HTMLButtonElement>('[data-payment="PAID"]')!.click();
    flushProblem(await expectRequest(`${BASE}/bookings/b1/payment`), 422, 'booking.payment-transition-invalid');
    await vi.waitFor(async () => {
      await settle();
      expect(text(card.querySelector('[data-detail-error]'))).toContain(
        'O pago desta reserva non pode pasar a ese estado.',
      );
    });
    card.querySelector<HTMLButtonElement>('[data-payment="PAID"]')!.click();
    flushProblem(await expectRequest(`${BASE}/bookings/b1/payment`), 412, 'common.precondition-failed');
    await vi.waitFor(async () => {
      await settle();
      expect(card.querySelector('[data-detail-stale]')).not.toBeNull();
    });
    expect(card.querySelector('[data-detail-error]')).toBeNull();
    card.querySelector<HTMLButtonElement>('[data-detail-reload]')!.click();
    (await expectRequest(`${BASE}/bookings/b1`)).flush(detail({ paymentStatus: 'PAID', paymentTargets: ['PENDING'] }));
    await vi.waitFor(async () => {
      await settle();
      expect(card.querySelector('[data-payment="PENDING"]')).not.toBeNull();
    });
  });

  it('o persoal do mostrador (STAFF) marca cobros pero non ve «Cancelar reserva»', async () => {
    const { el, settle } = await renderManage(fakeSession({ staff: 'naron' }));
    const card = await openDetail(el, settle);
    expect(card.querySelector('[data-payment="PAID"]')).not.toBeNull();
    expect(card.querySelector('[data-detail-cancel]')).toBeNull();
  });

  it('cancelar pide as canles (correo marcado) e manda a versión; xa pagada, avisa de que queda a devolver', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, detail({ paymentStatus: 'PAID', paymentTargets: ['PENDING'] }));
    card.querySelector<HTMLButtonElement>('[data-detail-cancel]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-cancel-confirm]')).toBeTruthy();
    });
    const box = (channel: string) => dialog()!.querySelector<HTMLInputElement>(`[data-channel="${channel}"]`)!;
    expect([box('EMAIL').checked, box('WEB').checked]).toEqual([true, false]);
    expect(dialog()!.querySelector('[data-channel="PHONE"]')).toBeNull();
    expect(dialog()!.querySelector('[data-cancel-paid]')).not.toBeNull();
    await expectNoAxeViolations(dialog()!);
    box('WEB').click();
    dialog()!.querySelector<HTMLButtonElement>('[data-cancel-confirm]')!.click();
    const post = await expectRequest(`${BASE}/bookings/b1/cancel`);
    expect(post.request.headers.get('If-Match')).toBe('"3"');
    expect(post.request.body).toEqual({ channels: ['EMAIL', 'WEB'] });
    post.flush(
      detail({
        status: 'CANCELLED_BY_MUNICIPALITY',
        paymentStatus: 'TO_REFUND',
        cancellable: false,
        paymentTargets: ['REFUNDED'],
      }),
    );
    (await expectRequest((r) => r.url === SUMMARY_URL)).flush(OVERVIEW);
    await vi.waitFor(async () => {
      await settle();
      expect(TestBed.inject(ToastService).current()?.key).toBe('booking.manage.detail.done.cancelled');
    });
    expect(card.querySelector('[data-detail-cancel]')).toBeNull();
    expect(text(card.querySelector('[data-payment="REFUNDED"]'))).toBe('Marcar como devolta');
  });

  it('a ligazón do rexistro (?reserva=) abre o detalle ao lado das últimas e sae do URL', async () => {
    const { harness, el, settle } = await renderManage(undefined, '/naron/xestion/reservas?reserva=b1');
    (await expectRequest(`${BASE}/bookings/b1`)).flush(detail());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-detail-card] furar-value-list')).not.toBeNull();
    });
    expect(el.querySelector('.f-manage__home')?.getAttribute('data-detail')).toBe('latest');
    expect(el.querySelector('[data-section="payments"]')).toBeNull();
    expect(text(el.querySelector('[data-detail-card] [data-value="code"] dd'))).toBe('FUR-7K2Q');
    expect(harness.routeDebugElement?.injector.get(Router).url).toBe('/naron/xestion/reservas');
  });

  it('no móbil o detalle abre nun diálogo; pechalo deixa a portada como estaba', async () => {
    const original = Object.getOwnPropertyDescriptor(window, 'matchMedia');
    Object.defineProperty(window, 'matchMedia', { configurable: true, value: () => ({ matches: false }) });
    try {
      const { el, settle } = await renderManage();
      rowsOf(el, 'latest')[0].click();
      (await expectRequest(`${BASE}/bookings/b1`)).flush(detail());
      await vi.waitFor(async () => {
        await settle();
        expect(dialog()?.querySelector('furar-value-list')).toBeTruthy();
      });
      expect(el.querySelector('[data-detail-card]')).toBeNull();
      expect(el.querySelector('[data-section="payments"]')).not.toBeNull();
      expect(dialog()!.querySelector('h2')?.textContent?.trim()).toBe('Reserva');
      await expectNoAxeViolations(dialog()!);
      dialog()!.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
      await vi.waitFor(async () => {
        await settle();
        expect(dialog()).toBeNull();
      });
      expect(rowsOf(el, 'latest')[0].getAttribute('aria-pressed')).toBe('false');
    } finally {
      if (original) Object.defineProperty(window, 'matchMedia', original);
      else Reflect.deleteProperty(window, 'matchMedia');
    }
  });

  it('detalle: miniatura da instalación, a parte e, gratis, sen importe; se non carga, «Tentar de novo»', async () => {
    const { el, settle } = await renderManage();
    rowsOf(el, 'latest')[0].click();
    (await expectRequest(`${BASE}/bookings/b1`)).flush(null, { status: 500, statusText: 'x' });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-detail-card] furar-status-state')?.getAttribute('data-mode')).toBe('error');
    });
    el.querySelector<HTMLButtonElement>('[data-detail-card] furar-status-state button')!.click();
    (await expectRequest(`${BASE}/bookings/b1`)).flush(
      detail({
        partName: 'Metade sur',
        paymentStatus: 'NONE',
        price: { kind: 'FREE' },
        paymentTargets: [],
        cancellable: false,
      }),
    );
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-detail-card] furar-value-list')).not.toBeNull();
    });
    const card = el.querySelector<HTMLElement>('[data-detail-card]')!;
    expect(card.querySelector('[data-detail-facility] img')?.getAttribute('src')).toBe('/fotos/padel.jpg');
    expect(text(card.querySelector('[data-value="part"] dd'))).toBe('Metade sur');
    expect(text(card.querySelector('[data-detail-total]'))).toBe('Total Gratis');
    expect(card.querySelector('.f-mdetail__acts')).toBeNull();
    card.querySelector<HTMLButtonElement>('[data-detail-close]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-detail-card]')).toBeNull();
    });
  });

  it('se cancelar falla (cambiou noutro sitio), a capa pecha e o detalle avisa con «Recargar»; «Manter» non fai nada', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle);
    const openCancel = async () => {
      card.querySelector<HTMLButtonElement>('[data-detail-cancel]')!.click();
      await vi.waitFor(async () => {
        await settle();
        expect(dialog()?.querySelector('[data-cancel-confirm]')).toBeTruthy();
      });
    };
    await openCancel();
    dialog()!.querySelector<HTMLButtonElement>('[data-cancel-keep]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
    });
    expect(TestBed.inject(ToastService).current()).toBeNull();
    await openCancel();
    dialog()!.querySelector<HTMLButtonElement>('[data-cancel-confirm]')!.click();
    flushProblem(await expectRequest(`${BASE}/bookings/b1/cancel`), 412, 'common.precondition-failed');
    await vi.waitFor(async () => {
      await settle();
      expect(card.querySelector('[data-detail-stale]')).not.toBeNull();
    });
  });
});
