// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, flushMatching, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { detail } from '../../../../testing/manage-bookings';
import { SLUG_BASE, organizationRow, preview, seasonFacility, series } from '../../../../testing/seasons';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { NewSeasonComponent } from './new-season.component';
import { SeasonPlanner } from './season-planner';
import type { SeasonDraft } from './seasons';

const FULL: SeasonDraft = {
  organizationId: 'o1',
  facilityId: 'f1',
  useId: 'u1',
  partId: 'p1',
  weekdays: 0b101,
  startMinute: 1140,
  endMinute: 1230,
  firstDate: '2026-11-16',
  lastDate: '2027-06-18',
  note: '',
};

describe('Nova reserva › Tempada (ADR-0039, punto 8)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-12T10:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    flushMatching((r) => r.url.endsWith('/pending-count'), { count: 0 });
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const settle = async (fixture: { whenStable: () => Promise<unknown>; detectChanges: () => void }) => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

  async function render(organizations = [organizationRow()]) {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const toast = vi.spyOn(TestBed.inject(ToastService), 'show');
    const fixture = TestBed.createComponent(NewSeasonComponent);
    fixture.componentRef.setInput('slug', 'naron');
    fixture.detectChanges();
    (await expectRequest((r) => r.url === `${SLUG_BASE}/organizations`)).flush(organizations);
    (await expectRequest((r) => r.url === `${SLUG_BASE}/booking-series-requests`)).flush([]);
    (await expectRequest(`${SLUG_BASE}/public/facilities`)).flush([{ id: 'f1', name: 'Pavillón' }]);
    await settle(fixture);
    const planner = fixture.debugElement.injector.get(SeasonPlanner);
    return { fixture, el: fixture.nativeElement as HTMLElement, planner, toast };
  }

  /** Enche o borrador, responde á ficha da instalación e á vista previa. */
  async function fill(
    fixture: Awaited<ReturnType<typeof render>>['fixture'],
    planner: SeasonPlanner,
    answer = preview(),
  ) {
    planner.draft.set(FULL);
    await settle(fixture);
    (await expectRequest(`${SLUG_BASE}/public/facilities/f1`)).flush(seasonFacility());
    await settle(fixture);
    const request = await expectRequest({ method: 'POST', url: `${SLUG_BASE}/booking-series/preview` });
    expect(request.request.body).toEqual({
      organizationId: 'o1',
      facilityId: 'f1',
      useId: 'u1',
      partId: 'p1',
      weekdays: [1, 3],
      startMinute: 1140,
      endMinute: 1230,
      firstDate: '2026-11-16',
      lastDate: '2027-06-18',
    });
    request.flush(answer);
    await settle(fixture);
  }

  it('os campos nunha cápsula; crear sen nada marca os erros e non manda nada; axe', async () => {
    const { fixture, el } = await render();
    for (const field of ['organization', 'facility', 'use', 'days', 'hours', 'dates']) {
      expect(el.querySelector(`[data-season-${field}]`), field).not.toBeNull();
    }
    expect(el.querySelector('[data-season-part]')).toBeNull();
    expect(text(el.querySelector('[data-season-create]'))).toBe('Crear a tempada');
    await expectNoAxeViolations(el);
    el.querySelector<HTMLButtonElement>('[data-season-create]')!.click();
    await settle(fixture);
    expect(el.querySelectorAll('[aria-invalid="true"]').length).toBeGreaterThan(3);
    expect(text(el)).toContain('Escolle polo menos un día da semana.');
  });

  it('sen entidades dadas de alta, dío', async () => {
    const { el } = await render([]);
    expect(el.querySelector('[data-season-no-organizations]')).not.toBeNull();
  });

  it('os días ocupados na mesma pantalla, os festivos nunha liña e «Crear N sesións»; axe', async () => {
    const { fixture, el, planner } = await render();
    await fill(fixture, planner);
    expect(el.querySelector('[data-season-part]')).not.toBeNull();
    const clashes = el.querySelector('[data-season-conflicts]')!;
    expect(text(clashes.querySelector('.f-season-clashes__head'))).toBe(
      '2 días xa están ocupados. Eses días non se crean.',
    );
    const items = [...clashes.querySelectorAll('[data-conflict]')];
    expect(items.map((item) => text(item.querySelector('.f-season-clashes__text')))).toEqual([
      'luns 23 nov. · 19:00–20:00Reserva de Roi Varela',
      'luns 14 dec. · todo o díaBloqueo: Torneo de Nadal',
    ]);
    expect(text(items[0].querySelector('[data-conflict-cancel]'))).toBe('Cancelar a reserva');
    expect(items[1].querySelector('[data-conflict-cancel]')).toBeNull();
    expect(text(el.querySelector('[data-skipped="holidays"]'))).toBe('Os festivos (6 xan.) tampouco se crean.');
    expect(text(el.querySelector('[data-season-create]'))).toBe('Crear 57 sesións');
    await expectNoAxeViolations(el);
  });

  it('«Cancelar a reserva» dun choque: o diálogo de sempre, cancela e pide outra vista previa', async () => {
    const { fixture, el, planner, toast } = await render();
    await fill(fixture, planner);
    el.querySelector<HTMLButtonElement>('[data-conflict-cancel="b9"]')!.click();
    (await expectRequest(`${SLUG_BASE}/bookings/b9`)).flush(detail({ id: 'b9', holderName: 'Roi Varela' }));
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-cancel-confirm]')).toBeTruthy();
    });
    expect(document.querySelectorAll('[data-channel]')).toHaveLength(2);
    document.querySelector<HTMLButtonElement>('[data-cancel-confirm]')!.click();
    const cancel = await expectRequest({ method: 'POST', url: `${SLUG_BASE}/bookings/b9/cancel` });
    expect(cancel.request.body).toEqual({ channels: ['EMAIL'] });
    cancel.flush(detail({ id: 'b9', status: 'CANCELLED_BY_MUNICIPALITY' }));
    (await expectRequest({ method: 'POST', url: `${SLUG_BASE}/booking-series/preview` })).flush(
      preview({ sessions: 58, conflicts: [preview().conflicts[1]] }),
    );
    await settle(fixture);
    expect(toast).toHaveBeenCalledWith('booking.manage.detail.done.cancelled');
    expect(text(el.querySelector('.f-season-clashes__head'))).toBe('1 día xa está ocupado. Ese día non se crea.');
    expect(text(el.querySelector('[data-season-create]'))).toBe('Crear 58 sesións');
  });

  it('crear co `skip` que se viu; se mentres tanto cambiou, a vista previa nova e o aviso; despois crea', async () => {
    const { fixture, el, planner, toast } = await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await fill(fixture, planner);
    el.querySelector<HTMLButtonElement>('[data-season-create]')!.click();
    const first = await expectRequest({ method: 'POST', url: `${SLUG_BASE}/booking-series` });
    expect(first.request.body.skip).toEqual(['2026-11-23', '2026-12-14']);
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    const fresh = preview({
      sessions: 56,
      conflicts: [...preview().conflicts, { ...preview().conflicts[0], date: '2026-11-25', name: 'Ana' }],
    });
    flushProblem(first, 409, 'booking.series-conflicts-changed', { preview: fresh });
    await settle(fixture);
    expect(text(el)).toContain('Mentres tanto cambiou algún día.');
    expect(text(el.querySelector('[data-season-create]'))).toBe('Crear 56 sesións');

    el.querySelector<HTMLButtonElement>('[data-season-create]')!.click();
    const second = await expectRequest({ method: 'POST', url: `${SLUG_BASE}/booking-series` });
    expect(second.request.body.skip).toEqual(['2026-11-23', '2026-11-25', '2026-12-14']);
    second.flush(series({ sessions: 56 }));
    await settle(fixture);
    expect(toast).toHaveBeenCalledWith('booking.seasons.created', { name: 'Club Baloncesto', count: 56 });
    expect(navigate).toHaveBeenCalledWith(['/', 'naron', 'xestion', 'reservas']);
  });

  it('se a vista previa falla, o aviso; sen sesións, o botón desactivado', async () => {
    const { fixture, el, planner } = await render();
    planner.draft.set(FULL);
    await settle(fixture);
    (await expectRequest(`${SLUG_BASE}/public/facilities/f1`)).flush(seasonFacility());
    flushProblem(
      await expectRequest({ method: 'POST', url: `${SLUG_BASE}/booking-series/preview` }),
      422,
      'booking.organization-seats-use',
    );
    await settle(fixture);
    expect(text(el)).toContain('Neste uso resérvase por prazas');
    planner.draft.update((draft) => ({ ...draft, lastDate: '2027-06-17' }));
    await settle(fixture);
    (await expectRequest({ method: 'POST', url: `${SLUG_BASE}/booking-series/preview` })).flush(
      preview({ sessions: 0, conflicts: [], holidays: [], useOff: ['2026-11-16'], closed: ['2026-11-18'] }),
    );
    await settle(fixture);
    expect(el.querySelector('[data-season-none]')).not.toBeNull();
    expect(el.querySelector('[data-skipped="useOff"]')).not.toBeNull();
    expect(el.querySelector('[data-skipped="closed"]')).not.toBeNull();
    // Sen sesións, «Crear a tempada» segue activo: non pide nada e o foco vai ao aviso.
    const create = el.querySelector<HTMLButtonElement>('[data-season-create]')!;
    expect(create.disabled).toBe(false);
    create.click();
    await settle(fixture);
    expect(document.activeElement).toBe(el.querySelector('[data-season-none]'));
  });

  it('«Manter» no diálogo non cancela nada; se a reserva do choque non se le, o erro', async () => {
    const { fixture, el, planner } = await render();
    await fill(fixture, planner);
    el.querySelector<HTMLButtonElement>('[data-conflict-cancel="b9"]')!.click();
    (await expectRequest(`${SLUG_BASE}/bookings/b9`)).flush(detail({ id: 'b9' }));
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(document.querySelector('[data-cancel-keep]')).toBeTruthy();
    });
    document.querySelector<HTMLButtonElement>('[data-cancel-keep]')!.click();
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('[data-conflict-cancel="b9"]')!.click();
    flushProblem(await expectRequest(`${SLUG_BASE}/bookings/b9`), 404, 'booking.not-found');
    await settle(fixture);
    expect(el.querySelector('furar-problem-alert')?.textContent?.trim()).toBeTruthy();
    // Sen a vista previa lista, crear non manda nada.
    planner.draft.update((draft) => ({ ...draft, weekdays: 0 }));
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('[data-season-create]')!.click();
    await settle(fixture);
    backend().expectNone({ method: 'POST', url: `${SLUG_BASE}/booking-series` });
  });
});
