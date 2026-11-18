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
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { BASE, BOOKINGS_URL, NOTES, NOTES_URL, WAITS_URL, booking } from '../reserve/reserve.fixture';
import { MyBookingsPage } from './my-bookings.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

/** Unha que se pode valorar, unha xa valorada (4) e unha falta (sen botón). */
const HISTORY = [
  booking({ id: 'h1', done: true, paymentStatus: 'PAID', canReview: true }),
  booking({ id: 'h2', done: true, paymentStatus: 'PAID', reviewStars: 4 }),
  booking({ id: 'h3', done: true, paymentStatus: 'PAID', noShow: true }),
];

const page = (items: unknown[]) => ({ items, page: 0, size: 20, totalItems: items.length });
const isList = (scope: string) => (r: { url: string; params: { get(name: string): string | null } }) =>
  r.url === BOOKINGS_URL && r.params.get('scope') === scope;

describe('As miñas reservas: Valorar (ADR-0037)', () => {
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

  async function renderHistory() {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({})),
        provideRouter([
          { path: 'naron/reservas', component: MyBookingsPage },
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
    (await expectRequest(`${BASE}/sports-contact`)).flush({});
    (await expectRequest((r) => r.url === WAITS_URL && r.method === 'GET')).flush([]);
    (await expectRequest(isList('UPCOMING'))).flush(page([]));
    (await expectRequest((r) => r.url.endsWith('/me/reminders') && r.method === 'GET')).flush({ enabled: true });
    await settle();
    el.querySelector<HTMLButtonElement>('[data-mine-tab="history"]')!.click();
    (await expectRequest(isList('HISTORY'))).flush(page(HISTORY));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-booking="h1"]')).not.toBeNull();
    });
    return { el, settle };
  }

  const card = (el: HTMLElement, id: string) => el.querySelector<HTMLElement>(`[data-booking="${id}"]`)!;
  const dialog = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')].at(-1) ?? null;

  async function openDetail(el: HTMLElement, id: string, settle: () => Promise<void>) {
    card(el, id).querySelector<HTMLButtonElement>('.f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-my-booking-detail]')).toBeTruthy();
    });
    return dialog()!;
  }
  async function openReview(el: HTMLElement, settle: () => Promise<void>) {
    const detail = await openDetail(el, 'h1', settle);
    detail.querySelector<HTMLButtonElement>('[data-booking-review]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-review-send]')).toBeTruthy();
    });
    return dialog()!;
  }

  it('«Valorar» só no detalle da que se pode, como botón principal; a xa valorada leva as súas estrelas', async () => {
    const { el, settle } = await renderHistory();
    expect(el.querySelector('[data-booking-review]')).toBeNull();
    const stars = card(el, 'h2').querySelector('[data-card-chips] [data-booking-reviewed]')!;
    expect(stars.getAttribute('aria-label')).toBe('4 de 5 estrelas');
    expect(stars.querySelectorAll('furar-icon')).toHaveLength(5);
    await expectNoAxeViolations(el);

    let detail = await openDetail(el, 'h1', settle);
    const review = detail.querySelector('[data-booking-review]')!;
    expect(review.textContent?.trim()).toBe('Valorar');
    expect(review.getAttribute('data-variant')).toBe('primary');
    await expectNoAxeViolations(detail);
    detail.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
    await vi.waitFor(() => expect(dialog()).toBeNull());

    detail = await openDetail(el, 'h2', settle);
    expect(detail.querySelector('[data-booking-review]')).toBeNull();
    const row = detail.querySelector('[data-value="review"]')!;
    expect(row.querySelector('dt')?.textContent?.trim()).toBe('A túa valoración');
    expect(row.querySelector('[data-booking-reviewed]')?.getAttribute('aria-label')).toBe('4 de 5 estrelas');
    detail.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
    await vi.waitFor(() => expect(dialog()).toBeNull());

    // A falta: «Non presentado» na tarxeta e no detalle, sen «Valorar».
    expect(card(el, 'h3').querySelector('[data-card-chips] [data-attendance="NO_SHOW"]')).not.toBeNull();
    detail = await openDetail(el, 'h3', settle);
    expect(detail.querySelector('[data-booking-review]')).toBeNull();
    expect(detail.querySelector('[data-value="state"] [data-attendance="NO_SHOW"]')).not.toBeNull();
  });

  it('a capa pide a nota, envía e a tarxeta queda coas estrelas e as grazas', async () => {
    const { el, settle } = await renderHistory();
    const layer = await openReview(el, settle);
    expect(document.querySelectorAll('furar-dialog-container')).toHaveLength(1);
    expect(layer.querySelector('h2')?.textContent?.trim()).toBe('Valorar');
    expect(layer.querySelectorAll('input[type="radio"]')).toHaveLength(5);
    expect(layer.querySelector('furar-help-tip')).not.toBeNull();
    await expectNoAxeViolations(layer);

    // Sen nota, «Enviar» marca o erro e non chama á API.
    layer.querySelector<HTMLButtonElement>('[data-review-send]')!.click();
    await settle();
    expect(layer.querySelector('.f-field-error')?.textContent?.trim()).toBe('Escolle unha nota.');
    backend().expectNone((r) => r.url.endsWith('/review'));

    layer.querySelector<HTMLInputElement>('[data-star="4"]')!.click();
    const comment = layer.querySelector<HTMLTextAreaElement>('[data-review-comment]')!;
    comment.value = '  Boa pista.  ';
    comment.dispatchEvent(new Event('input'));
    await settle();
    expect(layer.querySelectorAll('.f-star-rating__star[data-on]')).toHaveLength(4);
    layer.querySelector<HTMLButtonElement>('[data-review-send]')!.click();
    const sent = await expectRequest((r) => r.url === `${BOOKINGS_URL}/h1/review` && r.method === 'POST');
    expect(sent.request.body).toEqual({ stars: 4, comment: 'Boa pista.' });
    sent.flush(
      { stars: 4, comment: 'Boa pista.', createdAt: '2026-10-28T19:00:00Z' },
      { status: 201, statusText: 'Created' },
    );
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
    });
    expect(card(el, 'h1').querySelector('[data-booking-reviewed]')?.getAttribute('aria-label')).toBe('4 de 5 estrelas');
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'reviews.thanks' });
    const detail = await openDetail(el, 'h1', settle);
    expect(detail.querySelector('[data-booking-review]')).toBeNull();
  });

  it('se a API a rexeita, o erro queda na capa e a reserva segue por valorar', async () => {
    const { el, settle } = await renderHistory();
    const layer = await openReview(el, settle);
    layer.querySelector<HTMLInputElement>('[data-star="2"]')!.click();
    await settle();
    layer.querySelector<HTMLButtonElement>('[data-review-send]')!.click();
    flushProblem(await expectRequest((r) => r.url.endsWith('/h1/review')), 409, 'booking.review-exists');
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()!.querySelector('furar-problem-alert')?.textContent).toContain('Xa valoraches esta reserva.');
    });
    expect(card(el, 'h1').querySelector('[data-booking-reviewed]')).toBeNull();
  });
});
