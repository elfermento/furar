// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
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
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { BASE, BOOKINGS_URL, NOTES, NOTES_URL, WAITS_URL, booking, waitFor } from '../reserve/reserve.fixture';
import { MyBookingsPage } from './my-bookings.page';

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

describe('As miñas reservas: o detalle, as esperas e o aviso dun problema', () => {
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

  /** Preme a tarxeta (o seu único botón) e espera polo detalle. */
  async function openDetail(target: HTMLElement, settle: () => Promise<void>) {
    target.querySelector<HTMLButtonElement>('.f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-my-booking-detail]')).toBeTruthy();
    });
    return dialog()!;
  }

  const escape = (target: HTMLElement) => {
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
    Object.defineProperty(event, 'keyCode', { get: () => 27 });
    target.dispatchEvent(event);
  };

  it('sen prezo nin pago: o detalle sen fila de estado', async () => {
    const { el, settle } = await render([booking({ paymentStatus: 'NONE', price: { kind: 'NONE' } })]);
    expect(card(el, 'b1').querySelector('[data-card-chips] > *')).toBeNull();
    const detail = await openDetail(card(el, 'b1'), settle);
    expect(detail.querySelector('[data-value="state"]')).toBeNull();
  });

  it('para quen e as prazas: na tarxeta e no detalle', async () => {
    const participants: MyBookingResponse['participants'] = [
      { kind: 'SELF', name: 'Marta Vilar' },
      { kind: 'DEPENDENT', name: 'Uxía Vilar' },
    ];
    const { el, settle } = await render([booking({ participants, seats: 2 })]);
    expect(text(card(el, 'b1').querySelector('[data-line="meta"]'))).toBe(
      'Fútbol 7 · Metade sur · Marta Vilar e Uxía Vilar · 2 prazas',
    );
    const detail = await openDetail(card(el, 'b1'), settle);
    const rows = [...detail.querySelectorAll('.f-value-list__row')].map((row) => [
      row.getAttribute('data-value'),
      text(row.querySelector('dd')),
    ]);
    expect(rows.slice(0, 2)).toEqual([
      ['who', 'Marta Vilar · Uxía Vilar'],
      ['use', 'Fútbol 7 · 2 prazas'],
    ]);
  });

  it('«Avisar dun problema» pecha o detalle e abre o aviso coa instalación e a reserva', async () => {
    const { el, settle } = await render();
    el.querySelector<HTMLButtonElement>('[data-mine-tab="history"]')!.click();
    (await expectRequest(isList('HISTORY'))).flush(page(HISTORY));
    await vi.waitFor(async () => {
      await settle();
      expect(card(el, 'h1')).not.toBeNull();
    });
    const detail = await openDetail(card(el, 'h1'), settle);
    detail.querySelector<HTMLButtonElement>('[data-report-open]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-my-booking-detail]')).toBeFalsy();
      expect(text(dialog()?.querySelector('.f-dialog__subheading'))).toBe(
        'Campo de fútbol 1 de O Alto · 17/10, 11:00–12:00',
      );
    });
    expect(document.querySelectorAll('furar-dialog-container')).toHaveLength(1);
  });

  it('unha espera en «Próximas», mesturada por data: chip «En espera» coa nota e, no detalle, só «Deixar de esperar»; axe', async () => {
    // A espera é do 28 ás 20:00 (Madrid): despois da reserva do 28 ás 18:00 e antes da do 29.
    const { el, settle } = await render(UPCOMING.slice(0, 2), 2, [waitFor()]);
    const cards = [...el.querySelectorAll<HTMLElement>('furar-booking-card')];
    expect(cards.map((c) => c.getAttribute('data-booking') ?? c.getAttribute('data-wait'))).toEqual(['b1', 'w1', 'b2']);
    const wait = el.querySelector<HTMLElement>('[data-wait="w1"]')!;
    expect(wait.querySelector('h2')?.textContent?.trim()).toBe('Campo de fútbol 1 de O Alto');
    expect(text(wait.querySelector('[data-line="when"]'))).toBe('mércores, 28 de outubro · 20:00–21:00');
    expect(text(wait.querySelector('[data-line="meta"]'))).toBe('Fútbol 7');
    expect(wait.querySelector('[data-card-amount]')).toBeNull();
    const chip = wait.querySelector('furar-tag')!;
    expect(chip.textContent?.trim()).toBe('En espera');
    // Gris: distinto do azul de «Buscando xente» (ADR-0040).
    expect(chip.getAttribute('data-tone')).toBe('muted');
    expect(chip.getAttribute('data-look')).toBe('solid');
    expect(text(wait.querySelector('.f-status-card__note'))).toBe('avisámoste se queda libre');
    expect(wait.querySelectorAll('button, a')).toHaveLength(1);
    await expectNoAxeViolations(el);
    const detail = await openDetail(wait, settle);
    expect(detail.querySelector('[data-detail-qr]')).toBeNull();
    expect(text(detail.querySelector('[data-value="state"]'))).toBe('EstadoEn espera');
    const actions = [...detail.querySelectorAll('furar-action-item')];
    expect(actions.map(text)).toEqual(['Deixar de esperar']);
    expect(actions[0].getAttribute('data-tone')).toBe('danger');
  });

  it('«Deixar de esperar» non pregunta e a tarxeta desaparece; tamén se a API responde 404', async () => {
    const { el, settle } = await render([], 0, [waitFor(), waitFor({ id: 'w2', start: '2026-10-30T19:00:00Z' })]);
    const leave = async (id: string) => {
      const detail = await openDetail(el.querySelector<HTMLElement>(`[data-wait="${id}"]`)!, settle);
      detail.querySelector<HTMLButtonElement>('[data-wait-leave]')!.click();
    };
    await leave('w1');
    (await expectRequest((r) => r.url === `${WAITS_URL}/w1` && r.method === 'DELETE')).flush(null);
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
      expect(el.querySelector('[data-wait="w1"]')).toBeNull();
    });
    // A tarxeta que tiña o foco xa non está: anúnciase e o foco non queda perdido no body.
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'booking.mine.wait.removed' });
    await vi.waitFor(() => expect(document.activeElement).not.toBe(document.body));
    await leave('w2');
    flushProblem(
      await expectRequest((r) => r.url === `${WAITS_URL}/w2` && r.method === 'DELETE'),
      404,
      'booking.wait-not-found',
    );
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('furar-booking-card')).toBeNull();
    });
    expect(el.querySelector('[data-mine-error]')).toBeNull();
    expect(el.querySelector('furar-status-state')?.getAttribute('data-mode')).toBe('empty');
  });

  it('se «Deixar de esperar» falla, o aviso co texto da API; pechar o detalle dunha espera non fai nada', async () => {
    const { el, settle } = await render([], 0, [waitFor()]);
    let detail = await openDetail(el.querySelector<HTMLElement>('[data-wait="w1"]')!, settle);
    escape(detail);
    await vi.waitFor(() => expect(dialog()).toBeNull());
    backend().expectNone((r) => r.method === 'DELETE');
    detail = await openDetail(el.querySelector<HTMLElement>('[data-wait="w1"]')!, settle);
    detail.querySelector<HTMLButtonElement>('[data-wait-leave]')!.click();
    flushProblem(
      await expectRequest((r) => r.url === `${WAITS_URL}/w1` && r.method === 'DELETE'),
      500,
      'common.unexpected',
    );
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-mine-error]')).not.toBeNull();
    });
    expect(el.querySelector('[data-wait="w1"]')).not.toBeNull();
  });

  it('Cambiar pecha o detalle e leva á páxina de reservar en modo cambio', async () => {
    const { harness, el, settle } = await render();
    const detail = await openDetail(card(el, 'b1'), settle);
    detail.querySelector<HTMLAnchorElement>('[data-booking-change]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
    });
    expect(TestBed.inject(Router).url).toBe('/naron/instalacions/f1/reservar?cambiar=b1');
    expect(harness.routeNativeElement).not.toBeNull();
  });

  it('fóra de prazo: o detalle sen Cambiar nin Cancelar e co teléfono do servizo de deportes', async () => {
    const { el, settle } = await render();
    const detail = await openDetail(card(el, 'b3'), settle);
    expect(detail.querySelector('[data-booking-change], [data-booking-cancel], ul[furarActionList]')).toBeNull();
    expect(detail.querySelector('[data-detail-qr]')).not.toBeNull();
    expect(text(detail.querySelector('[data-booking-late]'))).toBe(
      'Xa pasou o prazo para cancelar. Chama ao 981 000 000',
    );
    expect(detail.querySelector('[data-booking-late] a')?.getAttribute('href')).toBe('tel:981000000');
    // Gratis: sen importe, a fila «Estado» coa etiqueta azul.
    const state = detail.querySelector('[data-value="state"]')!;
    expect(text(state.querySelector('dt'))).toBe('Estado');
    expect(state.querySelector('[data-detail-amount]')).toBeNull();
    expect(state.querySelector('furar-tag')?.getAttribute('data-tone')).toBe('info');
  });

  it('pechar o detalle sen escoller non fai nada', async () => {
    const { el, settle } = await render();
    const detail = await openDetail(card(el, 'b1'), settle);
    detail.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
    });
    backend().expectNone(`${BOOKINGS_URL}/b1/cancel`);
  });
});
