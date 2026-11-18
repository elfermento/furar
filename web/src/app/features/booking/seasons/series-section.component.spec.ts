// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import type { ManagedBookingResponse } from './seasons.store';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { detail, row } from '../../../../testing/manage-bookings';
import { SLUG_BASE, series } from '../../../../testing/seasons';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { SeasonsStore } from './seasons.store';
import { SeriesSectionComponent } from './series-section.component';

const SERIES = `${SLUG_BASE}/booking-series`;
const sessionRow = (id: string, start: string, seriesId = 's1'): ManagedBookingResponse =>
  row({
    id,
    holderName: 'Club Baloncesto',
    start,
    end: new Date(new Date(start).getTime() + 90 * 60_000).toISOString(),
    organization: { id: 'o1', name: 'Club Baloncesto', seriesId },
  });

describe('«Tempadas» na portada de Reservas e o seu detalle (ADR-0039, punto 11)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-12T10:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const settle = async (fixture: { whenStable: () => Promise<unknown>; detectChanges: () => void }) => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const dialog = () => document.querySelector<HTMLElement>('[data-series-detail]');

  async function render(
    items = [series(), series({ id: 's2', organizationName: 'Escola', paymentStatus: 'PAID', partName: undefined })],
    session = fakeSession(),
  ) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(session), SeasonsStore] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const toast = vi.spyOn(TestBed.inject(ToastService), 'show');
    const fixture = TestBed.createComponent(SeriesSectionComponent);
    fixture.componentRef.setInput('slug', 'naron');
    fixture.detectChanges();
    if (session.roles.manager) {
      const list = await expectRequest((r) => r.url === SERIES);
      expect(list.request.params.get('status')).toBe('ACTIVE');
      list.flush(items);
    }
    await settle(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement, toast };
  }

  /** Abre o detalle da primeira e responde coas súas sesións. */
  async function open(fixture: Awaited<ReturnType<typeof render>>['fixture'], el: HTMLElement) {
    el.querySelector<HTMLButtonElement>('[data-series="s1"]')!.click();
    const sessions = await expectRequest((r) => r.url === `${SLUG_BASE}/bookings`);
    expect(sessions.request.params.get('q')).toBe('Club Baloncesto');
    expect(sessions.request.params.getAll('facilityId')).toEqual(['f1']);
    expect(sessions.request.params.get('from')).toBe('2026-11-12');
    sessions.flush({
      items: [sessionRow('b1', '2026-11-16T18:00:00Z'), sessionRow('b2', '2026-11-18T18:00:00Z', 'outra')],
      page: 0,
      size: 50,
      totalItems: 2,
    });
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-series-sessions]')).toBeTruthy();
    });
    return dialog()!;
  }

  it('unha fila por tempada: entidade, instalación e parte, días e horas, e o chip do pago; axe', async () => {
    const { el } = await render();
    expect(text(el.querySelector('[data-series-count]'))).toBe('2');
    const first = el.querySelector('[data-series="s1"]')!;
    expect(text(first.querySelector('.f-mrow__name'))).toBe('Club Baloncesto');
    expect(text(first.querySelector('.f-mrow__meta'))).toBe('Pavillón · Pista central');
    expect(text(first.querySelector('.f-mrow__when'))).toBe('luns e mércores · 19:00–20:30');
    expect(text(first.querySelector('[data-chip]'))).toBe('Pendente de pago');
    expect(text(el.querySelector('[data-series="s2"] [data-chip]'))).toBe('Pagada');
    await expectNoAxeViolations(el);
  });

  it('as tres primeiras e «Ver máis»; con `hide` ou sen tempadas, nada', async () => {
    const many = ['a', 'b', 'c', 'd'].map((id) => series({ id }));
    const { fixture, el } = await render(many);
    expect(el.querySelectorAll('[data-series]')).toHaveLength(3);
    el.querySelector<HTMLButtonElement>('[data-series-more]')!.click();
    await settle(fixture);
    expect(el.querySelectorAll('[data-series]')).toHaveLength(4);
    fixture.componentRef.setInput('hide', true);
    await settle(fixture);
    expect(el.querySelector('[data-section="series"]')).toBeNull();
  });

  it('Recepción non as ve nin as pide', async () => {
    const { el } = await render([], fakeSession({ staff: 'naron' }));
    expect(el.querySelector('[data-section="series"]')).toBeNull();
  });

  it('o detalle: os datos, o pago no chip-menú e as sesións que quedan desta tempada; axe', async () => {
    const { fixture, el } = await render();
    const d = await open(fixture, el);
    expect(text(d.querySelector('[data-value="when"] dd'))).toBe('luns e mércores · 19:00–20:30');
    expect(text(d.querySelector('[data-value="sessions"] dd'))).toBe('57');
    expect(d.querySelectorAll('[data-session]')).toHaveLength(1);
    expect(text(d.querySelector('[data-series-sessions] button'))).toContain('Próximas sesións (1)');
    await expectNoAxeViolations(document.body);

    d.querySelector<HTMLButtonElement>('[data-series-payment] .f-menu-trigger__button')!.click();
    await settle(fixture);
    const paid = [...document.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]')].find(
      (item) => text(item) === 'Pagada',
    )!;
    paid.click();
    const put = await expectRequest({ method: 'PUT', url: `${SERIES}/s1/payment` });
    expect(put.request.headers.get('If-Match')).toBe('"1"');
    expect(put.request.body).toEqual({ status: 'PAID' });
    put.flush(series({ paymentStatus: 'PAID', version: 2 }));
    await settle(fixture);
    expect(text(el.querySelector('[data-series="s1"] [data-chip]'))).toBe('Pagada');
  });

  it('con máis sesións das que caben nunha páxina da busca, pídense todas', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('[data-series="s1"]')!.click();
    const first = await expectRequest((r) => r.url === `${SLUG_BASE}/bookings` && r.params.get('page') === '0');
    first.flush({ items: [sessionRow('b1', '2026-11-16T18:00:00Z')], page: 0, size: 50, totalItems: 51 });
    const second = await expectRequest((r) => r.url === `${SLUG_BASE}/bookings` && r.params.get('page') === '1');
    second.flush({ items: [sessionRow('b51', '2027-05-10T17:00:00Z')], page: 1, size: 50, totalItems: 51 });
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(text(dialog()?.querySelector('[data-series-sessions] button'))).toContain('Próximas sesións (2)');
    });
  });

  it('se o pago falla (412), o erro no detalle', async () => {
    const { fixture, el } = await render();
    const d = await open(fixture, el);
    d.querySelector<HTMLButtonElement>('[data-series-payment] .f-menu-trigger__button')!.click();
    await settle(fixture);
    [...document.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]')]
      .find((i) => text(i) === 'Gratis')!
      .click();
    flushProblem(await expectRequest({ method: 'PUT', url: `${SERIES}/s1/payment` }), 412, 'common.stale-version');
    await settle(fixture);
    expect(d.querySelector('furar-problem-alert')?.textContent?.trim()).toBeTruthy();
  });

  it('«Cancelar a sesión»: o diálogo de sempre, a sesión sae e as contas da tempada cambian', async () => {
    const { fixture, el, toast } = await render();
    const d = await open(fixture, el);
    d.querySelector<HTMLButtonElement>('[data-session="b1"] [data-session-cancel]')!.click();
    (await expectRequest(`${SLUG_BASE}/bookings/b1`)).flush(
      detail({
        id: 'b1',
        cancelChannels: ['EMAIL'],
        organization: { id: 'o1', name: 'Club Baloncesto', seriesId: 's1' },
      }),
    );
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-cancel-confirm]')).toBeTruthy();
    });
    // Unha sesión, non unha reserva: «Cancelar a sesión?», co día e a hora na liña da sesión.
    const ask = document.querySelector<HTMLElement>('furar-cancel-booking-dialog')!;
    expect(text(ask.querySelector('h2'))).toBe('Cancelar a sesión?');
    expect(text(ask.querySelector('[data-cancel-confirm]'))).toBe('Cancelar a sesión');
    expect(text(ask.querySelector('.f-confirm__detail[data-tone="primary"]'))).toContain(
      'luns, 26 de outubro · 19:00–20:00',
    );
    expect(document.querySelectorAll('[data-channel]')).toHaveLength(1);
    document.querySelector<HTMLButtonElement>('[data-cancel-confirm]')!.click();
    (await expectRequest({ method: 'POST', url: `${SLUG_BASE}/bookings/b1/cancel` })).flush(
      detail({ id: 'b1', status: 'CANCELLED_BY_MUNICIPALITY' }),
    );
    (await expectRequest(`${SERIES}/s1`)).flush(series({ sessions: 56, cancelled: 1 }));
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(d.querySelector('[data-session]')).toBeNull();
    });
    expect(toast).toHaveBeenCalledWith('booking.seasons.sessionCancelled');
    expect(text(d.querySelector('[data-value="sessions"] dd'))).toBe('56 · 1 canceladas');
  });

  it('«Rematar a tempada…»: desde que día (hoxe por defecto), e a tempada sae da lista', async () => {
    const { fixture, el, toast } = await render();
    const d = await open(fixture, el);
    d.querySelector<HTMLButtonElement>('[data-series-end]')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-end-confirm]')).toBeTruthy();
    });
    expect(text(document.querySelector('furar-end-series-dialog'))).toContain(
      'Cancélase a sesión que queda desde ese día e a hora queda libre para a veciñanza.',
    );
    document.querySelector<HTMLButtonElement>('[data-end-confirm]')!.click();
    const end = await expectRequest({ method: 'POST', url: `${SERIES}/s1/end` });
    expect(end.request.body).toEqual({ from: '2026-11-16' });
    flushProblem(end, 422, 'booking.series-end-invalid');
    await settle(fixture);
    expect(text(document.querySelector('furar-end-series-dialog'))).toContain('Escolle un día desde hoxe');
    document.querySelector<HTMLButtonElement>('[data-end-confirm]')!.click();
    (await expectRequest({ method: 'POST', url: `${SERIES}/s1/end` })).flush(
      series({ endedFrom: '2026-11-16', cancelled: 57, sessions: 0 }),
    );
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(d.querySelector('[data-value="ended"]')).toBeTruthy();
    });
    expect(toast).toHaveBeenCalledWith('booking.seasons.ended', { count: 57 });
    expect(d.querySelector('[data-series-end]')).toBeNull();
    expect(el.querySelector('[data-series="s1"]')).toBeNull();
  });

  it('se as sesións non chegan, dío dentro do plegable', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('[data-series="s1"]')!.click();
    flushProblem(await expectRequest((r) => r.url === `${SLUG_BASE}/bookings`), 500, 'common.unexpected');
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-series-sessions]')).toBeTruthy();
    });
    expect(text(dialog()!.querySelector('[data-series-sessions] button'))).toContain('Próximas sesións');
  });
});
