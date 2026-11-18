// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import {
  backend,
  expectRequest,
  fakeSession,
  flushMatching,
  flushProblem,
  provideTestApi,
} from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { BASE, BOOKINGS_URL, NOTES, NOTES_URL, WAITS_URL, booking } from '../reserve/reserve.fixture';
import { MyBookingsPage } from './my-bookings.page';
import { ToastService } from '../../../shared/ui/toast/toast.service';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

const CONTACT_URL = `${BASE}/sports-contact`;
const CONTACT = { name: 'Servizo de Deportes', phone: '981 000 000', email: 'deportes@example.org' };

/** Unha pagada para o xoves, outra fóra de prazo e outra pendente (a de por defecto). */
const UPCOMING = [
  booking(),
  booking({
    id: 'b2',
    code: 'FUR-PAD1',
    facilityId: 'f2',
    facilityName: 'Pista de pádel 1 de O Alto',
    useId: 'u9',
    useName: 'Pádel',
    partId: undefined,
    partName: undefined,
    rentalMode: 'WHOLE',
    paymentStatus: 'PAID',
    price: { kind: 'PAID', amountCents: 800 },
    start: '2026-10-29T19:30:00Z',
    end: '2026-10-29T20:30:00Z',
    cancelUntil: '2026-10-28T19:30:00Z',
    version: 3,
    cover: {
      id: 'p1',
      kind: 'PHOTO',
      alt: { gl: 'Pista', es: 'Pista' },
      width: 1600,
      height: 1067,
      large: '/api/v1/municipalities/naron/photos/p1/large',
      thumb: '/api/v1/municipalities/naron/photos/p1/thumb',
    },
  }),
  booking({ id: 'b3', code: 'FUR-LATE', canCancel: false, paymentStatus: 'NONE', price: { kind: 'FREE' } }),
];

const HISTORY = [
  booking({ id: 'h1', done: true, paymentStatus: 'PAID', start: '2026-10-17T09:00:00Z', end: '2026-10-17T10:00:00Z' }),
  booking({
    id: 'h2',
    status: 'CANCELLED_BY_MUNICIPALITY',
    canCancel: false,
    paymentStatus: 'REFUNDED',
    partId: undefined,
    partName: undefined,
  }),
];

const REMINDERS_URL = '/api/v1/municipalities/naron/me/reminders';

const page = (items: unknown[], totalItems = items.length) => ({ items, page: 0, size: 20, totalItems });
const isList = (scope: string) => (r: { url: string; params: { get(name: string): string | null } }) =>
  r.url === BOOKINGS_URL && r.params.get('scope') === scope;

describe('As miñas reservas', () => {
  afterEach(() => {
    // Os eventos da persoa (ADR-0038): «Próximas» pídeos á parte.
    flushMatching((r) => r.url.endsWith('/events/mine'), []);
    // As partidas ás que se uniu (ADR-0040): tamén á parte.
    flushMatching((r) => r.url.endsWith('/me/open-matches'), []);
    // As entidades das que é responsable (ADR-0039): «Eu · ‹Entidade›» e «Reservar como».
    flushMatching((r) => r.url.endsWith('/me/organizations'), []);
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(upcoming: unknown[] = UPCOMING, total = upcoming.length, waits: unknown[] = []) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({})),
        provideRouter([
          { path: 'naron/reservas', component: MyBookingsPage },
          { path: 'naron/instalacions', component: StubPage },
          { path: 'naron/instalacions/:id/reservar', component: StubPage },
        ]),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create('/naron/reservas');
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    (await expectRequest(NOTES_URL)).flush(NOTES);
    (await expectRequest(CONTACT_URL)).flush(CONTACT);
    (await expectRequest((r) => r.url === WAITS_URL && r.method === 'GET')).flush(waits);
    (await expectRequest(isList('UPCOMING'))).flush(page(upcoming, total));
    (await expectRequest((r) => r.url === REMINDERS_URL && r.method === 'GET')).flush({ enabled: true });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('furar-booking-card, furar-status-state:not([data-mode="skeleton"])')).not.toBeNull();
    });
    return { harness, el, settle };
  }

  const card = (el: HTMLElement, id: string) => el.querySelector<HTMLElement>(`[data-booking="${id}"]`)!;
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  /** A capa de máis arriba (o detalle ou, despois, a confirmación). */
  const dialog = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')].at(-1) ?? null;
  const accept = () => dialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!;
  const chips = (node: Element) => [...node.querySelectorAll('[data-card-chips] furar-tag')].map(text);

  /** Preme a tarxeta (o seu único botón) e espera polo detalle. */
  async function openDetail(target: HTMLElement, settle: () => Promise<void>) {
    target.querySelector<HTMLButtonElement>('.f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-my-booking-detail]')).toBeTruthy();
    });
    return dialog()!;
  }
  async function openCancel(el: HTMLElement, id: string, settle: () => Promise<void>) {
    const detail = await openDetail(card(el, id), settle);
    detail.querySelector<HTMLButtonElement>('[data-booking-cancel]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.getAttribute('role')).toBe('alertdialog');
    });
  }
  const escape = (target: HTMLElement) => {
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
    Object.defineProperty(event, 'keyCode', { get: () => 27 });
    target.dispatchEvent(event);
  };

  it('Próximas: tarxeta pulsable enteira, sen botóns, co importe, a data, o uso e a fila de chips; axe', async () => {
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('As miñas reservas');
    const tabs = [...el.querySelectorAll('[role="tab"]')];
    expect(tabs.map((tab) => [tab.textContent?.trim(), tab.getAttribute('aria-selected')])).toEqual([
      ['Próximas', 'true'],
      ['Historial', 'false'],
    ]);
    const first = card(el, 'b1');
    // Un só elemento interactivo: o nome, que abre o detalle.
    const controls = [...first.querySelectorAll('button, a')];
    expect(controls).toHaveLength(1);
    expect(first.querySelector('h2 > button')?.textContent?.trim()).toBe('Campo de fútbol 1 de O Alto');
    expect(first.querySelector('.f-status-card__chevron')).not.toBeNull();
    expect(text(first.querySelector('[data-line="when"]'))).toBe('mércores, 28 de outubro · 18:00–19:00');
    expect([...first.querySelectorAll('[data-line="when"] .f-status-card__part')].map(text)).toEqual([
      'mércores, 28 de outubro',
      '18:00–19:00',
    ]);
    expect(first.querySelector('[data-line="when"] furar-icon')).not.toBeNull();
    expect(text(first.querySelector('[data-line="meta"]'))).toBe('Fútbol 7 · Metade sur');
    expect(first.querySelector('[data-line="meta"]')?.hasAttribute('data-soft')).toBe(true);
    expect(text(first.querySelector('[data-card-amount]'))).toBe('30,00 €');
    const chip = first.querySelector('[data-card-chips] furar-tag')!;
    expect([chip.textContent?.trim(), chip.getAttribute('data-tone'), chip.getAttribute('data-look')]).toEqual([
      'Pendente de pago',
      'warn',
      'solid',
    ]);
    // O botón leva de descrición o importe, os datos e os chips.
    const describedBy = controls[0].getAttribute('aria-describedby')!.split(' ');
    expect(describedBy.map((id) => text(document.getElementById(id)))).toEqual([
      '30,00 €',
      'mércores, 28 de outubro · 18:00–19:00Fútbol 7 · Metade surPendente de pago',
    ]);
    expect(chips(card(el, 'b2'))).toEqual(['Pagada']);
    // A miniatura da instalación (decorativa), se a ten.
    const cover = card(el, 'b2').querySelector<HTMLImageElement>('[data-card-cover]')!;
    expect([cover.getAttribute('src'), cover.getAttribute('alt')]).toEqual([
      '/api/v1/municipalities/naron/photos/p1/thumb',
      '',
    ]);
    expect(first.querySelector('[data-card-cover]')).toBeNull();
    expect(card(el, 'b2').querySelector('[data-card-chips] furar-tag')?.getAttribute('data-tone')).toBe('success');
    // Gratis: só a etiqueta azul, sen importe.
    expect(card(el, 'b3').querySelector('[data-card-amount]')).toBeNull();
    expect(card(el, 'b3').querySelector('[data-card-chips] furar-tag')?.getAttribute('data-tone')).toBe('info');
    await expectNoAxeViolations(el);
  });

  it('o detalle: QR arriba, os datos co importe e o estado debaixo, e as accións en lista coa destrutiva ao final; Esc pecha', async () => {
    const { el, settle } = await render();
    const detail = await openDetail(card(el, 'b1'), settle);
    expect(detail.getAttribute('role')).toBe('dialog');
    expect(text(detail.querySelector('h2'))).toBe('Campo de fútbol 1 de O Alto');
    expect(text(detail.querySelector('.f-dialog__subheading'))).toBe('mércores, 28 de outubro · 18:00–19:00');
    expect(detail.querySelector('[data-detail-qr] furar-qr svg')?.getAttribute('aria-label')).toContain('FUR-7K2Q');
    expect(text(detail.querySelector('[data-qr-code]'))).toBe('FUR-7K2Q');
    expect(text(detail.querySelector('[data-detail-qr] p'))).toBe('Ensínao ao chegar');
    const rows = [...detail.querySelectorAll('.f-value-list__row')].map((row) => [
      row.getAttribute('data-value'),
      text(row.querySelector('dt')),
      text(row.querySelector('dd')),
    ]);
    expect(rows).toEqual([
      ['use', 'Uso', 'Fútbol 7'],
      ['part', 'Parte', 'Metade sur'],
      ['state', 'Importe', '30,00 €Pendente de pago'],
    ]);
    const actions = [...detail.querySelectorAll('furar-action-item')];
    expect(actions.map(text)).toEqual(['Cambiar', 'Cancelar']);
    expect(actions[0].querySelector('a')?.getAttribute('href')).toBe('/naron/instalacions/f1/reservar?cambiar=b1');
    expect(actions[1].getAttribute('data-tone')).toBe('danger');
    expect(detail.querySelector('ul[furarActionList]')?.getAttribute('aria-label')).toBe('Accións');
    expect(detail.querySelector('[data-booking-late], [data-booking-review]')).toBeNull();
    await expectNoAxeViolations(detail);
    escape(detail);
    await vi.waitFor(() => expect(dialog()).toBeNull());
    // Pechar non cambia de vista.
    expect(card(el, 'b1')).not.toBeNull();
  });

  it('cancelar sen pagar: desde o detalle, estilo A con «Sen custo» e o prazo; a tarxeta cambia sen recargar', async () => {
    const { el, settle } = await render();
    await openCancel(el, 'b1', settle);
    expect(document.querySelectorAll('furar-dialog-container')).toHaveLength(1);
    const lines = [...dialog()!.querySelectorAll('[data-confirm-detail]')].map(text);
    expect(lines).toEqual([
      'Campo de fútbol 1 de O Alto mércores, 28 de outubro · 18:00–19:00',
      'Sen custo. Estás dentro do prazo, que acaba o luns, 26 de outubro ás 18:00.',
    ]);
    expect(dialog()!.querySelector('[data-confirm-cancel]')?.textContent?.trim()).toBe('Manter');
    expect(dialog()!.querySelector('h2')?.textContent?.trim()).toBe('Cancelar a reserva?');
    expect(accept().textContent?.trim()).toBe('Cancelar a reserva');
    await expectNoAxeViolations(dialog()!);
    accept().click();
    const request = await expectRequest(`${BOOKINGS_URL}/b1/cancel`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('If-Match')).toBe('"0"');
    request.flush(booking({ status: 'CANCELLED_BY_PERSON', canCancel: false, version: 1 }));
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
      expect(chips(card(el, 'b1'))).toEqual(['Cancelada']);
    });
    const cancelled = card(el, 'b1');
    expect(text(cancelled.querySelector('[data-line="meta"]'))).toBe('Fútbol 7 · Metade sur · cancelada por ti');
    expect(cancelled.querySelector('[data-card-chips] furar-tag')?.getAttribute('data-tone')).toBe('danger');
    expect(TestBed.inject(ToastService).current()).toMatchObject({
      key: 'booking.mine.cancel.done',
      params: { name: 'Campo de fútbol 1 de O Alto' },
    });
    // Sen recargar a lista.
    backend().expectNone(isList('UPCOMING'));
  });

  it('cancelar xa pagada: «Xa está pagada» coa nota de devolución do concello (da API)', async () => {
    const { el, settle } = await render();
    await openCancel(el, 'b2', settle);
    const lines = [...dialog()!.querySelectorAll('[data-confirm-detail]')].map(text);
    expect(lines[1]).toBe(`Xa está pagada. Quedará pendente de devolución. ${NOTES.refund.gl}`);
    accept().click();
    const request = await expectRequest(`${BOOKINGS_URL}/b2/cancel`);
    expect(request.request.headers.get('If-Match')).toBe('"3"');
    request.flush({ ...UPCOMING[1], status: 'CANCELLED_BY_PERSON', paymentStatus: 'TO_REFUND', version: 4 });
    await vi.waitFor(async () => {
      await settle();
      expect(chips(card(el, 'b2'))).toEqual(['A devolver']);
    });
  });

  it('412: aviso con «Recargar», que pide de novo as próximas', async () => {
    const { el, settle } = await render();
    await openCancel(el, 'b1', settle);
    accept().click();
    flushProblem(await expectRequest(`${BOOKINGS_URL}/b1/cancel`), 412, 'common.stale-version');
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-mine-stale]')).not.toBeNull();
    });
    expect(el.querySelector('[data-mine-stale]')?.closest('[role="status"]')).not.toBeNull();
    // A tarxeta queda como estaba.
    expect(chips(card(el, 'b1'))).toEqual(['Pendente de pago']);
    el.querySelector<HTMLButtonElement>('[data-mine-reload]')!.click();
    (await expectRequest(isList('UPCOMING'))).flush(page([booking({ version: 1, canCancel: false })]));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-mine-stale]')).toBeNull();
      expect(card(el, 'b1')).not.toBeNull();
    });
    const detail = await openDetail(card(el, 'b1'), settle);
    expect(detail.querySelector('[data-booking-cancel]')).toBeNull();
  });

  it('outro erro ao cancelar (fóra de prazo): o texto da API nun aviso', async () => {
    const { el, settle } = await render();
    await openCancel(el, 'b1', settle);
    accept().click();
    flushProblem(await expectRequest(`${BOOKINGS_URL}/b1/cancel`), 422, 'booking.cancel-deadline-passed');
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-mine-error]')).not.toBeNull();
    });
    expect(el.querySelector('[data-mine-error]')?.getAttribute('role')).toBe('alert');
  });

  it('Historial: a mesma tarxeta (pasada co seu pago, Devolta con quen cancelou) e no detalle «Reservar de novo»; axe', async () => {
    const { el, settle } = await render();
    el.querySelector<HTMLButtonElement>('[data-mine-tab="history"]')!.click();
    (await expectRequest(isList('HISTORY'))).flush(page(HISTORY));
    await vi.waitFor(async () => {
      await settle();
      expect(card(el, 'h1')).not.toBeNull();
    });
    const done = card(el, 'h1');
    expect(done.getAttribute('data-past')).toBe('true');
    expect(done.querySelector('.f-status-card')?.hasAttribute('data-past')).toBe(true);
    expect(done.querySelectorAll('button, a')).toHaveLength(1);
    const doneChip = done.querySelector('[data-card-chips] furar-tag')!;
    expect([
      doneChip.textContent?.trim(),
      doneChip.getAttribute('data-tone'),
      doneChip.getAttribute('data-look'),
    ]).toEqual(['Pagada', 'success', 'solid']);
    const refunded = card(el, 'h2');
    expect(text(refunded.querySelector('[data-line="meta"]'))).toBe('Fútbol 7 · cancelada polo concello');
    expect(refunded.querySelector('[data-card-chips] furar-tag')?.getAttribute('data-tone')).toBe('muted');
    expect(chips(refunded)).toEqual(['Devolta']);
    await expectNoAxeViolations(el);

    // Pasada: sen QR; «Reservar de novo» e «Avisar dun problema».
    let detail = await openDetail(done, settle);
    expect(detail.querySelector('[data-detail-qr], [data-booking-cancel], [data-booking-change]')).toBeNull();
    expect([...detail.querySelectorAll('furar-action-item')].map(text)).toEqual([
      'Reservar de novo',
      'Avisar dun problema',
    ]);
    expect(detail.querySelector('[data-booking-again]')?.getAttribute('href')).toBe(
      '/naron/instalacions/f1/reservar?uso=u1&parte=p2',
    );
    await expectNoAxeViolations(detail);
    escape(detail);
    await vi.waitFor(() => expect(dialog()).toBeNull());

    // Cancelada: só «Reservar de novo», co mesmo uso.
    detail = await openDetail(refunded, settle);
    expect([...detail.querySelectorAll('furar-action-item')].map(text)).toEqual(['Reservar de novo']);
    expect(detail.querySelector('[data-booking-again]')?.getAttribute('href')).toBe(
      '/naron/instalacions/f1/reservar?uso=u1',
    );
  });

  it('«Ver máis» pide a páxina seguinte e engádea', async () => {
    const { el, settle } = await render([UPCOMING[0]], 2);
    el.querySelector<HTMLButtonElement>('[data-mine-more]')!.click();
    const next = await expectRequest(isList('UPCOMING'));
    expect(next.request.params.get('page')).toBe('1');
    next.flush({ ...page([UPCOMING[1]], 2), page: 1 });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('furar-booking-card')).toHaveLength(2);
    });
    expect(el.querySelector('[data-mine-more]')).toBeNull();
  });

  it('sen próximas: estado baleiro coa ligazón ás instalacións', async () => {
    const { el } = await render([]);
    expect(el.textContent).toContain('Non tes reservas próximas');
    expect(el.querySelector('[data-mine-browse]')?.getAttribute('href')).toBe('/naron/instalacions');
  });
  it('«Recordatorios por correo» ao final: nome á esquerda, interruptor á dereita; apagalo garda e, se falla, volve', async () => {
    const { el, settle } = await render();
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-mine-reminders] [role="switch"]')).not.toBeNull();
    });
    const control = el.querySelector<HTMLButtonElement>('[data-mine-reminders] [role="switch"]')!;
    expect(control.getAttribute('aria-checked')).toBe('true');
    expect(control.getAttribute('aria-label')).toBe('Recordatorios por correo');
    control.click();
    const put = await expectRequest((r) => r.url === REMINDERS_URL && r.method === 'PUT');
    expect(put.request.body).toEqual({ enabled: false });
    put.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(control.getAttribute('aria-checked')).toBe('false');
    control.click();
    flushProblem(await expectRequest((r) => r.url === REMINDERS_URL && r.method === 'PUT'), 500, 'common.unexpected');
    await vi.waitFor(async () => {
      await settle();
      expect(control.getAttribute('aria-checked')).toBe('false');
    });
    expect(TestBed.inject(ToastService).current()).toMatchObject({
      key: 'booking.mine.reminders.error',
      tone: 'danger',
    });
  });
});
