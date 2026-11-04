// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { BASE, BOOKINGS_URL, NOTES, NOTES_URL, booking } from '../reserve/reserve.fixture';
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

const page = (items: unknown[], totalItems = items.length) => ({ items, page: 0, size: 20, totalItems });
const isList = (scope: string) => (r: { url: string; params: { get(name: string): string | null } }) =>
  r.url === BOOKINGS_URL && r.params.get('scope') === scope;

describe('As miñas reservas', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(upcoming: unknown[] = UPCOMING, total = upcoming.length) {
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
    (await expectRequest(isList('UPCOMING'))).flush(page(upcoming, total));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('furar-booking-card, furar-status-state:not([data-mode="skeleton"])')).not.toBeNull();
    });
    return { harness, el, settle };
  }

  const card = (el: HTMLElement, id: string) => el.querySelector<HTMLElement>(`[data-booking="${id}"]`)!;
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');
  const accept = () => dialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!;
  async function openCancel(el: HTMLElement, id: string, settle: () => Promise<void>) {
    card(el, id).querySelector<HTMLButtonElement>('[data-booking-cancel]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).not.toBeNull();
    });
  }

  it('Próximas: tarxeta con nome, data, uso e parte, prezo coa etiqueta e as tres accións; axe', async () => {
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('As miñas reservas');
    const tabs = [...el.querySelectorAll('[role="tab"]')];
    expect(tabs.map((tab) => [tab.textContent?.trim(), tab.getAttribute('aria-selected')])).toEqual([
      ['Próximas', 'true'],
      ['Historial', 'false'],
    ]);
    const first = card(el, 'b1');
    expect(first.querySelector('h2')?.textContent?.trim()).toBe('Campo de fútbol 1 de O Alto');
    expect(text(first.querySelector('[data-booking-when]'))).toBe('mércores, 28 de outubro · 18:00–19:00');
    expect(first.querySelector('[data-booking-when] furar-icon')).not.toBeNull();
    expect(text(first.querySelector('[data-booking-meta]'))).toBe('Fútbol 7 · Metade sur');
    expect(first.querySelector('[data-booking-money] b')?.textContent?.trim()).toBe('30,00 €');
    const chip = first.querySelector('[data-booking-money] furar-tag')!;
    expect([chip.textContent?.trim(), chip.getAttribute('data-tone'), chip.getAttribute('data-look')]).toEqual([
      'Pendente de pago',
      'warn',
      'solid',
    ]);
    const change = first.querySelector<HTMLAnchorElement>('[data-booking-change]')!;
    expect(change.getAttribute('href')).toBe('/naron/instalacions/f1/reservar?cambiar=b1');
    expect(change.getAttribute('data-variant')).toBe('quiet');
    expect(first.querySelector('[data-booking-cancel]')?.getAttribute('data-variant')).toBe('danger');
    expect(first.querySelector('[data-booking-qr]')?.getAttribute('data-variant')).toBe('secondary');
    // As accións repetidas levan o nome da instalación como descrición.
    expect(change.getAttribute('aria-describedby')).toBe(first.querySelector('h2')?.id);
    for (const action of first.querySelectorAll('.f-mybooking__acts .f-button')) {
      expect(action.querySelector('furar-icon svg path')?.getAttribute('d')).toBeTruthy();
    }
    expect(text(card(el, 'b2').querySelector('[data-booking-money] furar-tag'))).toBe('Pagada');
    // A miniatura da instalación (decorativa), se a ten.
    const cover = card(el, 'b2').querySelector<HTMLImageElement>('[data-booking-cover]')!;
    expect([cover.getAttribute('src'), cover.getAttribute('alt')]).toEqual([
      '/api/v1/municipalities/naron/photos/p1/thumb',
      '',
    ]);
    expect(first.querySelector('[data-booking-cover]')).toBeNull();
    expect(card(el, 'b2').querySelector('[data-booking-money] furar-tag')?.getAttribute('data-tone')).toBe('success');
    await expectNoAxeViolations(el);
  });

  it('fóra de prazo (P1): sen Cambiar nin Cancelar e co teléfono do servizo de deportes', async () => {
    const { el } = await render();
    const late = card(el, 'b3');
    expect(late.querySelector('[data-booking-change]')).toBeNull();
    expect(late.querySelector('[data-booking-cancel]')).toBeNull();
    expect(late.querySelector('[data-booking-qr]')).not.toBeNull();
    expect(text(late.querySelector('[data-booking-late]'))).toBe(
      'Xa pasou o prazo para cancelar. Chama ao 981 000 000',
    );
    expect(late.querySelector('[data-booking-late] a')?.getAttribute('href')).toBe('tel:981000000');
    // Gratis: só a etiqueta azul, sen importe.
    expect(late.querySelector('[data-booking-money] b')).toBeNull();
    expect(late.querySelector('[data-booking-money] furar-tag')?.getAttribute('data-tone')).toBe('info');
  });

  it('cancelar sen pagar: estilo A con «Sen custo» e o prazo; a tarxeta cambia sen recargar', async () => {
    const { el, settle } = await render();
    await openCancel(el, 'b1', settle);
    expect(dialog()!.getAttribute('role')).toBe('alertdialog');
    const lines = [...dialog()!.querySelectorAll('[data-confirm-detail]')].map(text);
    expect(lines).toEqual([
      'Campo de fútbol 1 de O Alto mércores, 28 de outubro · 18:00–19:00',
      'Sen custo. Estás dentro do prazo, que acaba o luns, 26 de outubro ás 18:00.',
    ]);
    expect(dialog()!.querySelector('[data-confirm-cancel]')?.textContent?.trim()).toBe('Manter');
    await expectNoAxeViolations(dialog()!);
    accept().click();
    const request = await expectRequest(`${BOOKINGS_URL}/b1/cancel`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('If-Match')).toBe('"0"');
    request.flush(booking({ status: 'CANCELLED_BY_PERSON', canCancel: false, version: 1 }));
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
      expect(card(el, 'b1').querySelector('[data-booking-cancel]')).toBeNull();
    });
    const cancelled = card(el, 'b1');
    expect(text(cancelled.querySelector('[data-booking-meta]'))).toBe('Fútbol 7 · Metade sur · cancelada por ti');
    expect(text(cancelled.querySelector('[data-booking-money] furar-tag'))).toBe('Cancelada');
    expect(cancelled.querySelector('[data-booking-money] furar-tag')?.getAttribute('data-tone')).toBe('danger');
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
      expect(text(card(el, 'b2').querySelector('[data-booking-money] furar-tag'))).toBe('A devolver');
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
    expect(card(el, 'b1').querySelector('[data-booking-cancel]')).not.toBeNull();
    el.querySelector<HTMLButtonElement>('[data-mine-reload]')!.click();
    (await expectRequest(isList('UPCOMING'))).flush(page([booking({ version: 1, canCancel: false })]));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-mine-stale]')).toBeNull();
      expect(card(el, 'b1').querySelector('[data-booking-cancel]')).toBeNull();
    });
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

  it('Ver QR: furar-dialog co QR e o código; Esc pecha', async () => {
    const { el, settle } = await render();
    card(el, 'b1').querySelector<HTMLButtonElement>('[data-booking-qr]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).not.toBeNull();
    });
    const qr = dialog()!;
    expect(qr.getAttribute('role')).toBe('dialog');
    expect(qr.querySelector('furar-qr svg')?.getAttribute('aria-label')).toContain('FUR-7K2Q');
    expect(text(qr.querySelector('[data-qr-code]'))).toBe('FUR-7K2Q');
    expect(qr.textContent).toContain('Campo de fútbol 1 de O Alto');
    await expectNoAxeViolations(qr);
    const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
    Object.defineProperty(escape, 'keyCode', { get: () => 27 });
    qr.dispatchEvent(escape);
    await vi.waitFor(() => expect(dialog()).toBeNull());
  });

  it('Historial: Realizada (contorno), Devolta con quen cancelou e «Reservar de novo»; axe', async () => {
    const { el, settle } = await render();
    el.querySelector<HTMLButtonElement>('[data-mine-tab="history"]')!.click();
    (await expectRequest(isList('HISTORY'))).flush(page(HISTORY));
    await vi.waitFor(async () => {
      await settle();
      expect(card(el, 'h1')).not.toBeNull();
    });
    const done = card(el, 'h1');
    const doneChip = done.querySelector('[data-booking-money] furar-tag')!;
    expect([
      doneChip.textContent?.trim(),
      doneChip.getAttribute('data-tone'),
      doneChip.getAttribute('data-look'),
    ]).toEqual(['Realizada', 'neutral', 'outline']);
    expect(done.querySelector('[data-booking-again]')?.getAttribute('href')).toBe(
      '/naron/instalacions/f1/reservar?uso=u1&parte=p2',
    );
    expect(done.querySelector('[data-booking-qr], [data-booking-cancel], [data-booking-change]')).toBeNull();
    const refunded = card(el, 'h2');
    expect(text(refunded.querySelector('[data-booking-meta]'))).toBe('Fútbol 7 · cancelada polo concello');
    expect(refunded.querySelector('[data-booking-money] furar-tag')?.getAttribute('data-tone')).toBe('muted');
    expect(text(refunded.querySelector('[data-booking-money] furar-tag'))).toBe('Devolta');
    expect(refunded.querySelector('[data-booking-again]')?.getAttribute('href')).toBe(
      '/naron/instalacions/f1/reservar?uso=u1',
    );
    await expectNoAxeViolations(el);
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
});
