// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { Dialog } from '@angular/cdk/dialog';
import type { WritableSignal } from '@angular/core';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { SLUG_BASE, mySeriesRequest, seasonFacility, session } from '../../../../testing/seasons';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { MyBookingsStore } from '../mine/my-bookings.store';
import { HolderTabsComponent } from './holder-tabs.component';
import type { SeasonDraft } from './seasons';

const ORG = `${SLUG_BASE}/me/organizations/o1`;

@Component({
  selector: 'furar-test-host',
  imports: [HolderTabsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-holder-tabs slug="naron"><p data-own>As miñas</p></furar-holder-tabs>`,
})
class HostComponent {}

describe('«Eu · ‹Entidade›» en As miñas reservas (ADR-0039, punto 10)', () => {
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
  const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');

  async function render(organizations = [{ id: 'o1', name: 'Club Baloncesto' }]) {
    // Na páxina, «As miñas reservas» dá o seu store (o diálogo de cancelar é o de «Eu»).
    TestBed.configureTestingModule({ providers: [...provideTestApi(), MyBookingsStore] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const toast = vi.spyOn(TestBed.inject(ToastService), 'show');
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    (await expectRequest(`${SLUG_BASE}/me/organizations`)).flush(organizations);
    await settle(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement, toast };
  }

  /** Abre a pestana da entidade e responde ás súas reservas e solicitudes. */
  async function openClub(
    fixture: Awaited<ReturnType<typeof render>>['fixture'],
    el: HTMLElement,
    requests = [
      mySeriesRequest(),
      mySeriesRequest({ id: 'q2', status: 'REJECTED', rejectionMessage: 'Non hai oco.' }),
      mySeriesRequest({ id: 'q3', status: 'APPROVED' }),
    ],
  ) {
    el.querySelector<HTMLButtonElement>('[data-mine-holder="o1"]')!.click();
    await settle(fixture);
    const list = await expectRequest((r) => r.url === `${ORG}/bookings`);
    expect(list.request.params.get('scope')).toBe('UPCOMING');
    list.flush({ items: [session()], page: 0, size: 20, totalItems: 2 });
    (await expectRequest(`${ORG}/series-requests`)).flush(requests);
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(el.querySelector('[data-org-bookings]')).not.toBeNull();
    });
  }

  it('sen entidades: só o de sempre, sen pestanas', async () => {
    const { el } = await render([]);
    expect(el.querySelector('[data-mine-holders]')).toBeNull();
    expect(el.querySelector('[data-own]')).not.toBeNull();
  });

  it('responsable: «Eu · Club», a súa pestana coas solicitudes e as sesións («· Tempada»); axe', async () => {
    const { fixture, el } = await render();
    expect([...el.querySelectorAll('[data-mine-holder]')].map(text)).toEqual(['Eu', 'Club Baloncesto']);
    await openClub(fixture, el);
    expect(el.querySelector<HTMLElement>('[data-mine-own]')!.hidden).toBe(true);
    const cards = [...el.querySelectorAll('[data-my-season-request]')];
    expect(cards).toHaveLength(2);
    expect(text(cards[0].querySelector('[data-line="place"]'))).toBe('Pista central · Baloncesto');
    expect(text(cards[0].querySelector('[data-chip="PENDING"]'))).toBe('Solicitude enviada');
    expect(text(cards[1].querySelector('[data-chip="REJECTED"]'))).toBe('Non aprobada');
    expect(text(el.querySelector('[data-booking="b1"] [data-line="meta"]'))).toBe(
      'Baloncesto · Pista central · Tempada',
    );
    expect(el.querySelector('[data-org-more]')).not.toBeNull();
    await expectNoAxeViolations(el);
  });

  it('«Ver máis» e o historial; volver a «Eu» mostra o de sempre', async () => {
    const { fixture, el } = await render();
    await openClub(fixture, el);
    el.querySelector<HTMLButtonElement>('[data-org-more]')!.click();
    const more = await expectRequest((r) => r.url === `${ORG}/bookings` && r.params.get('page') === '1');
    more.flush({ items: [session({ id: 'b2' })], page: 1, size: 20, totalItems: 2 });
    await settle(fixture);
    expect(el.querySelectorAll('furar-booking-card')).toHaveLength(2);
    el.querySelector<HTMLButtonElement>('[data-org-tab="history"]')!.click();
    await settle(fixture);
    flushProblem(await expectRequest((r) => r.params.get('scope') === 'HISTORY'), 500, 'common.unexpected');
    await settle(fixture);
    expect(text(el)).toContain('Tentar de novo');
    el.querySelector<HTMLButtonElement>('[data-mine-holder="me"]')!.click();
    await settle(fixture);
    expect(el.querySelector<HTMLElement>('[data-mine-own]')!.hidden).toBe(false);
  });

  it('o detalle dunha sesión, sen «Cambiar»: cancelar é sen custo ata que empeza', async () => {
    const { fixture, el, toast } = await render();
    await openClub(fixture, el);
    el.querySelector<HTMLButtonElement>('[data-booking="b1"] .f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-booking-cancel]')).toBeTruthy();
    });
    expect(dialog()!.querySelector('[data-booking-change]')).toBeNull();
    dialog()!.querySelector<HTMLButtonElement>('[data-booking-cancel]')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-confirm-accept]')).toBeTruthy();
    });
    expect(text(document.querySelector('[role="alertdialog"] h2, [role="alertdialog"] h1'))).toBe('Cancelar a sesión?');
    expect(text(document.querySelector('[data-confirm-accept]'))).toBe('Cancelar a sesión');
    document.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    const cancel = await expectRequest({ method: 'POST', url: `${ORG}/bookings/b1/cancel` });
    expect(cancel.request.headers.get('If-Match')).toBe('"1"');
    cancel.flush(session({ status: 'CANCELLED_BY_PERSON' }));
    await settle(fixture);
    expect(toast).toHaveBeenCalledWith('booking.mine.cancel.done', { name: 'Pavillón' });
  });

  it('se cancelar falla, o erro enriba', async () => {
    const { fixture, el } = await render();
    await openClub(fixture, el);
    el.querySelector<HTMLButtonElement>('[data-booking="b1"] .f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-booking-cancel]')).toBeTruthy();
    });
    dialog()!.querySelector<HTMLButtonElement>('[data-booking-cancel]')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-confirm-accept]')).toBeTruthy();
    });
    document.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    flushProblem(
      await expectRequest({ method: 'POST', url: `${ORG}/bookings/b1/cancel` }),
      422,
      'booking.not-cancellable',
    );
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(el.querySelector('[data-org-error]')).not.toBeNull();
    });
  });

  it('o detalle dunha solicitude: a mensaxe se non se aprobou; retirar a pendente', async () => {
    const { fixture, el, toast } = await render();
    await openClub(fixture, el);
    el.querySelector<HTMLButtonElement>('[data-my-season-request="q2"] .f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-request-rejection]')).toBeTruthy();
    });
    expect(dialog()!.querySelector('[data-request-withdraw]')).toBeNull();
    TestBed.inject(Dialog).closeAll();
    await settle(fixture);

    el.querySelector<HTMLButtonElement>('[data-my-season-request="q1"] .f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-request-withdraw]')).toBeTruthy();
    });
    expect(text(document.querySelector('[data-value="note"] dd'))).toBe('Equipo infantil');
    document.querySelector<HTMLButtonElement>('[data-request-withdraw]')!.click();
    (await expectRequest({ method: 'DELETE', url: `${ORG}/series-requests/q1` })).flush(null);
    await settle(fixture);
    expect(el.querySelector('[data-my-season-request="q1"]')).toBeNull();
    expect(toast).toHaveBeenCalledWith('booking.seasons.request.withdrawn');
  });

  it('se retirar falla (xa decidida), o erro enriba', async () => {
    const { fixture, el } = await render();
    await openClub(fixture, el);
    el.querySelector<HTMLButtonElement>('[data-my-season-request="q1"] .f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-request-withdraw]')).toBeTruthy();
    });
    document.querySelector<HTMLButtonElement>('[data-request-withdraw]')!.click();
    flushProblem(
      await expectRequest({ method: 'DELETE', url: `${ORG}/series-requests/q1` }),
      409,
      'booking.series-request-decided',
    );
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(text(el.querySelector('[data-org-error]'))).toContain('Esta solicitude xa se decidiu.');
    });
  });

  it('«Solicitar tempada»: os campos (sen entidade) coa nota; os erros e enviar; a tarxeta enriba', async () => {
    const { fixture, el, toast } = await render();
    await openClub(fixture, el, []);
    el.querySelector<HTMLButtonElement>('[data-season-ask]')!.click();
    (await expectRequest(`${SLUG_BASE}/public/facilities`)).flush([{ id: 'f1', name: 'Pavillón' }]);
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-season-note]')).toBeTruthy();
    });
    const d = dialog()!;
    expect(d.querySelector('[data-season-organization]')).toBeNull();
    expect(text(d)).toContain('Club Baloncesto');
    await expectNoAxeViolations(d);
    d.querySelector<HTMLButtonElement>('[data-ask-send]')!.click();
    await settle(fixture);
    expect(d.querySelectorAll('[aria-invalid="true"]').length).toBeGreaterThan(2);

    // Enche como o faría a persoa: o formulario é dobre vínculo co borrador do diálogo.
    const form = d.querySelector('furar-season-form')!;
    const note = form.querySelector<HTMLTextAreaElement>('[data-season-note]')!;
    note.value = 'Infantil';
    note.dispatchEvent(new Event('input'));
    const ask = TestBed.inject(Dialog).openDialogs.at(-1)!.componentInstance as unknown as {
      draft: WritableSignal<SeasonDraft>;
    };
    const draft = ask.draft;
    draft.update((value) => ({
      ...value,
      facilityId: 'f1',
      useId: 'u1',
      partId: 'p1',
      weekdays: 0b10000,
      startMinute: 1080,
      endMinute: 1170,
      firstDate: '2026-11-20',
      lastDate: '2027-06-18',
    }));
    await settle(fixture);
    (await expectRequest(`${SLUG_BASE}/public/facilities/f1`)).flush(seasonFacility());
    await settle(fixture);
    d.querySelector<HTMLButtonElement>('[data-ask-send]')!.click();
    const post = await expectRequest({ method: 'POST', url: `${ORG}/series-requests` });
    expect(post.request.body).toMatchObject({ facilityId: 'f1', weekdays: [5], note: 'Infantil' });
    post.flush(mySeriesRequest({ id: 'q9' }));
    await settle(fixture);
    expect(toast).toHaveBeenCalledWith('booking.seasons.ask.sent');
    expect(el.querySelector('[data-my-season-request="q9"]')).not.toBeNull();
  });
});
