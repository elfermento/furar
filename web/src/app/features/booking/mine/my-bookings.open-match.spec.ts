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
import { bookingMatch, myMatch } from '../../../../testing/open-matches';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { BASE, BOOKINGS_URL, NOTES, NOTES_URL, WAITS_URL, booking } from '../reserve/reserve.fixture';
import { MyBookingsPage } from './my-bookings.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

/** Unha reserva de pista enteira para dentro de moito, que aínda se pode cancelar de balde. */
const future = (overrides: Partial<MyBookingResponse> = {}) =>
  booking({
    rentalMode: 'WHOLE',
    partId: undefined,
    partName: undefined,
    useName: 'Fútbol sala',
    start: '2099-11-20T19:00:00Z',
    end: '2099-11-20T20:30:00Z',
    cancelUntil: '2099-11-19T19:00:00Z',
    paymentStatus: 'NONE',
    price: { kind: 'FREE' },
    ...overrides,
  });

const page = (items: unknown[]) => ({ items, page: 0, size: 20, totalItems: items.length });
const MATCH_URL = `${BOOKINGS_URL}/b1/open-match`;
const ME_MATCH = `${BASE}/me/open-matches/m1`;

describe('As miñas reservas: buscar xente para xogar (ADR-0040)', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/events/mine'), []);
    flushMatching((r) => r.url.endsWith('/me/open-matches'), []);
    // As entidades das que é responsable (ADR-0039): «Eu · ‹Entidade›» e «Reservar como».
    flushMatching((r) => r.url.endsWith('/me/organizations'), []);
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(upcoming: unknown[], enabled = true) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({})),
        provideRouter([
          { path: 'naron/reservas', component: MyBookingsPage },
          { path: 'naron/partidas/:id', component: StubPage },
        ]),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality({ openMatchPolicy: { enabled, cancelHours: 12 } }));
    const harness = await RouterTestingHarness.create('/naron/reservas');
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    (await expectRequest(NOTES_URL)).flush(NOTES);
    (await expectRequest(`${BASE}/sports-contact`)).flush({});
    (await expectRequest((r) => r.url === WAITS_URL && r.method === 'GET')).flush([]);
    (await expectRequest((r) => r.url === BOOKINGS_URL && r.params.get('scope') === 'UPCOMING')).flush(page(upcoming));
    (await expectRequest((r) => r.url === `${BASE}/me/reminders`)).flush({ enabled: true });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('furar-booking-card')).not.toBeNull();
    });
    return { harness, el, settle };
  }

  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const dialog = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')].at(-1) ?? null;
  const card = (el: HTMLElement) => el.querySelector<HTMLElement>('[data-booking="b1"]')!;

  async function openDetail(el: HTMLElement, settle: () => Promise<void>) {
    card(el).querySelector<HTMLButtonElement>('.f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-my-booking-detail]')).toBeTruthy();
    });
    return dialog()!;
  }

  async function pick(el: HTMLElement, settle: () => Promise<void>, action: string) {
    const detail = await openDetail(el, settle);
    detail.querySelector<HTMLButtonElement>(`[data-detail-action="${action}"]`)!.click();
  }

  const type = (input: HTMLInputElement | null, value: string) => {
    input!.value = value;
    input!.dispatchEvent(new Event('input'));
  };

  it('a tarxeta que busca xente leva o chip azul «Buscando xente» coa nota «4 de 10»; no detalle, tamén', async () => {
    const { el, settle } = await render([future({ openMatch: bookingMatch() })]);
    const chip = card(el).querySelector('[data-chip="searching"]')!;
    expect(text(chip)).toBe('Buscando xente');
    expect(chip.getAttribute('data-tone')).toBe('info');
    expect(text(chip.nextElementSibling)).toBe('4 de 10');
    const detail = await openDetail(el, settle);
    // No detalle, na súa fila «Partida» co que falta (non entre os chips do importe).
    expect(detail.querySelector('[data-value="state"] [data-chip="searching"]')).toBeNull();
    expect(text(detail.querySelector('[data-value="match"] dt'))).toBe('Partida');
    expect(text(detail.querySelector('[data-value="match"] [data-chip="searching"]'))).toBe('Buscando xente');
    expect(text(detail.querySelector('[data-value="match"] dd > span'))).toMatch(/^4 de 10 · /);
    expect(
      [...detail.querySelectorAll('[data-detail-action]')].map((a) => a.getAttribute('data-detail-action')),
    ).toEqual(['match-see', 'match-edit', 'match-stop']);
  });

  it('oculta ou sen buscar: sen chip; coa función apagada, sen «Buscar xente para xogar»', async () => {
    const { el, settle } = await render([future({ openMatch: bookingMatch({ hidden: true }) })], false);
    expect(card(el).querySelector('[data-chip="searching"]')).toBeNull();
    const detail = await openDetail(el, settle);
    expect(detail.querySelector('[data-detail-action="match-open"]')).toBeNull();
    expect(detail.querySelector('[data-detail-action="match-see"]')).not.toBeNull();
  });

  it('«Buscar xente para xogar»: o diálogo (sen casiña), os erros nos campos e publicar; a tarxeta pasa a buscar; axe', async () => {
    const { el, settle } = await render([future()]);
    await pick(el, settle, 'match-open');
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-open-match-form]')).toBeTruthy();
    });
    const form = dialog()!;
    expect(text(form.querySelector('.f-dialog__title, h2'))).toBe('Buscar xente para xogar');
    expect(form.querySelector<HTMLInputElement>('#match-known')?.value).toBe('1');
    expect(form.querySelector('input[type="checkbox"]')).toBeNull();
    // Sen o texto de axuda de debaixo: a información de protección de datos (ADR-0040 punto 13) vai
    // no «?» do título, e o campo descríbese con ela.
    expect(form.querySelector('[data-match-notice]')).toBeNull();
    const tip = form.querySelector('#match-title-help');
    expect(tip?.textContent).toContain('A partida vese na web do concello ata que se xogue ou deixes de buscar.');
    expect(tip?.textContent).toContain('Na súa páxina aparece o teu nome.');
    expect(form.querySelector('#match-title')?.getAttribute('aria-describedby')).toContain('match-title-help');
    expect(form.querySelector('[data-match-approval] [role="switch"]')?.getAttribute('aria-checked')).toBe('false');
    expect(text(form.querySelector('[data-match-save]'))).toBe('Publicar');
    await expectNoAxeViolations(form);

    // Sen mínimo nin máximo: os erros e ningunha petición; o foco ao primeiro.
    form.querySelector<HTMLButtonElement>('[data-match-save]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(form.querySelector('#match-minimum')?.getAttribute('aria-invalid')).toBe('true');
    });
    backend().expectNone({ method: 'PUT', url: MATCH_URL });
    await vi.waitFor(() => expect(document.activeElement?.id).toBe('match-minimum'));

    // O máximo menor ca o mínimo.
    type(form.querySelector('#match-minimum'), '10');
    type(form.querySelector('#match-maximum'), '8');
    await settle();
    expect(text(form.querySelector('#match-maximum-error'))).toContain('Non pode ser menor ca o mínimo (10).');
    type(form.querySelector('#match-maximum'), '12');
    type(form.querySelector('#match-title'), 'Pachanga');
    form.querySelector<HTMLButtonElement>('[data-match-save]')!.click();
    const put = await expectRequest({ method: 'PUT', url: MATCH_URL });
    expect(put.request.headers.has('If-Match')).toBe(false);
    expect(put.request.body).toEqual({ knownCount: 1, minimum: 10, maximum: 12, approval: false, title: 'Pachanga' });
    put.flush(myMatch({ me: { role: 'ORGANIZER' }, bookingId: 'b1', total: 1, minimum: 10, maximum: 12 }));
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
      expect(text(card(el).querySelector('[data-chip="searching"] + *'))).toBe('1 de 12');
    });
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'openMatch.dialog.published' });
  });

  it('un erro do servidor queda no diálogo', async () => {
    const { el, settle } = await render([future()]);
    await pick(el, settle, 'match-open');
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-open-match-form]')).toBeTruthy();
    });
    const form = dialog()!;
    type(form.querySelector('#match-minimum'), '6');
    type(form.querySelector('#match-maximum'), '10');
    form.querySelector<HTMLButtonElement>('[data-match-save]')!.click();
    flushProblem(await expectRequest({ method: 'PUT', url: MATCH_URL }), 422, 'booking.match-name-required');
    await vi.waitFor(async () => {
      await settle();
      expect(form.textContent).toContain('Para buscar xente ou unirte fai falta o teu nome na conta.');
    });
    form.querySelector<HTMLButtonElement>('[data-match-dismiss]')!.click();
    await vi.waitFor(() => expect(dialog()).toBeNull());
  });

  it('«Editar a partida»: pide a partida, o diálogo co que hai e garda coa súa versión', async () => {
    const { el, settle } = await render([future({ openMatch: bookingMatch() })]);
    await pick(el, settle, 'match-edit');
    (await expectRequest(ME_MATCH)).flush(myMatch({ me: { role: 'ORGANIZER' }, bookingId: 'b1', version: 7 }));
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-open-match-form]')).toBeTruthy();
    });
    const form = dialog()!;
    expect(text(form.querySelector('.f-dialog__title, h2'))).toBe('Editar a partida');
    expect(form.querySelector<HTMLInputElement>('#match-title')?.value).toBe('Pachanga de venres');
    expect(form.querySelector<HTMLInputElement>('#match-minimum')?.value).toBe('6');
    expect(text(form.querySelector('[data-match-save]'))).toBe('Gardar');
    type(form.querySelector('#match-maximum'), '12');
    form.querySelector<HTMLButtonElement>('[data-match-save]')!.click();
    const put = await expectRequest({ method: 'PUT', url: MATCH_URL });
    expect(put.request.headers.get('If-Match')).toBe('"7"');
    put.flush(myMatch({ me: { role: 'ORGANIZER' }, maximum: 12 }));
    await vi.waitFor(async () => {
      await settle();
      expect(text(card(el).querySelector('[data-chip="searching"] + *'))).toBe('4 de 12');
    });
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'openMatch.dialog.saved' });
  });

  it('se a partida non se pode ler, o erro na páxina e sen diálogo', async () => {
    const { el, settle } = await render([future({ openMatch: bookingMatch() })]);
    await pick(el, settle, 'match-edit');
    flushProblem(await expectRequest(ME_MATCH), 404, 'booking.match-not-found');
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[data-mine-error]'))).toContain('Esta partida xa non está dispoñible.');
    });
  });

  it('«Deixar de buscar»: pregunta cantas persoas seguen e pecha; a tarxeta perde o chip', async () => {
    const { el, settle } = await render([future({ openMatch: bookingMatch({ confirmed: 3 }) })]);
    await pick(el, settle, 'match-stop');
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-confirm-accept]')).toBeTruthy();
    });
    expect(dialog()!.textContent).toContain('Deixar de buscar xente?');
    expect(dialog()!.textContent).toContain('A reserva e as 3 persoas seguen.');
    expect(text(dialog()!.querySelector('[data-confirm-cancel]'))).toBe('Manter');
    // liñas de detalle (a instalación e cando; o que pasa) e o botón principal,
    // non o vermello (a reserva segue).
    const details = dialog()!.querySelectorAll('[data-confirm-detail]');
    expect(details).toHaveLength(2);
    expect(details[0]!.getAttribute('data-tone')).toBe('primary');
    expect(details[1]!.textContent).toContain('A reserva e as 3 persoas seguen.');
    expect(dialog()!.querySelector('[data-confirm-accept]')?.getAttribute('data-variant')).toBe('primary');
    dialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    const put = await expectRequest({ method: 'PUT', url: MATCH_URL + '/status' });
    expect(put.request.body).toEqual({ status: 'CLOSED' });
    put.flush(myMatch({ me: { role: 'ORGANIZER' }, status: 'CLOSED' }));
    await vi.waitFor(async () => {
      await settle();
      expect(card(el).querySelector('[data-chip="searching"]')).toBeNull();
    });
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'openMatch.stop.done' });
  });

  it('«Deixar de buscar» sen ninguén ou cunha persoa: o texto en singular ou sen persoas; manter non cambia nada', async () => {
    const { el, settle } = await render([future({ openMatch: bookingMatch({ confirmed: 1 }) })]);
    await pick(el, settle, 'match-stop');
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-confirm-cancel]')).toBeTruthy();
    });
    expect(dialog()!.textContent).toContain('A reserva e a persoa que se uniu seguen.');
    dialog()!.querySelector<HTMLButtonElement>('[data-confirm-cancel]')!.click();
    await vi.waitFor(() => expect(dialog()).toBeNull());
    expect(card(el).querySelector('[data-chip="searching"]')).not.toBeNull();
  });

  it('«Ver a partida» leva á súa páxina', async () => {
    const { el, settle } = await render([future({ openMatch: bookingMatch() })]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    await pick(el, settle, 'match-see');
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(['/', 'naron', 'partidas', 'm1']));
  });

  it('cancelar unha reserva que busca xente avisa das persoas apuntadas', async () => {
    const { el, settle } = await render([future({ openMatch: bookingMatch({ confirmed: 3 }) })]);
    const detail = await openDetail(el, settle);
    detail.querySelector<HTMLButtonElement>('[data-booking-cancel]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-confirm-detail]')).toBeTruthy();
    });
    const lines = [...dialog()!.querySelectorAll('[data-confirm-detail]')].map(text);
    expect(lines.at(-1)).toBe('Cancélase tamén a partida e avisamos por correo ás 3 persoas apuntadas.');
    dialog()!.querySelector<HTMLButtonElement>('[data-confirm-cancel]')!.click();
    await vi.waitFor(() => expect(dialog()).toBeNull());
  });
});
