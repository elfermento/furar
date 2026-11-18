// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, fakeSession, flushMatching, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { madridToday } from '../../shared/ui/calendar/madrid-time';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { openEventDetail } from './event-detail-dialog.component';
import { managedActivity, managedEvent, session } from './event.fixture';
import type { EventResponse } from './events.store';

const BASE = '/api/v1/municipalities/naron/events/manage';
const stable = () => TestBed.inject(ApplicationRef).whenStable();
const top = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')].at(-1)!;
const detail = () => document.querySelector<HTMLElement>('[data-event-detail]')!;
const PEOPLE = [
  { id: 'r1', status: 'PENDING', name: 'Lois Proba', createdAt: '2026-11-10T09:00:00Z' },
  { id: 'r2', status: 'CONFIRMED', name: 'Antía Proba', payment: 'PENDING', createdAt: '2026-11-09T09:00:00Z' },
];

describe('detalle dun evento ou dunha actividade na xestión', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/pending-count'), { count: 1 });
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function open(roles: Parameters<typeof fakeSession>[0], event: EventResponse) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession(roles))] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    void openEventDetail(TestBed.inject(DialogService), { slug: 'naron', event, facilities: [] });
    await vi.waitFor(() => expect(document.querySelector('[data-event-detail]')).not.toBeNull());
    await stable();
    return detail();
  }

  const tab = async (name: string) => {
    detail().querySelector<HTMLButtonElement>(`[data-detail-tab="${name}"]`)!.click();
    await stable();
  };
  const menu = async () => {
    detail().querySelector<HTMLButtonElement>('[data-detail-more]')!.click();
    await stable();
  };
  const accept = async () => {
    await vi.waitFor(() => expect(document.querySelector('[data-confirm-accept]')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
  };
  const closed = () => vi.waitFor(() => expect(document.querySelector('[data-event-detail]')).toBeNull());

  it('actividade: diálogo grande con Datos · Persoas · Sesións, os datos e «Editar» ao pé', async () => {
    const el = await open({ manager: 'naron' }, managedActivity());
    expect(el.getAttribute('data-size')).toBe('full');
    expect(el.querySelector('.f-dialog__title')?.textContent).toContain('Natación · Iniciación');
    expect(el.querySelector('.f-dialog__subheading')?.textContent).toContain('Piscina municipal');
    expect([...el.querySelectorAll('[role="tab"]')].map((b) => b.textContent?.trim())).toEqual([
      'Datos',
      'Persoas',
      'Sesións',
    ]);
    const data = el.querySelector('[data-detail-data]')!.textContent ?? '';
    expect(data).toContain('Luns e mércores · 17:00–18:00');
    expect(data).toContain('25,00');
    expect(el.querySelector('[data-value="place"] dd')?.textContent?.trim()).toBe('Piscina municipal · Vaso pequeno');
    expect(el.querySelector('[data-detail-edit]')?.textContent?.trim()).toBe('Editar');
    await expectNoAxeViolations(el);

    await tab('sessions');
    const today = madridToday();
    (await expectRequest(`${BASE}/a1/sessions`)).flush([session(today), session('2099-12-01')]);
    await vi.waitFor(() =>
      expect(el.querySelector('[data-detail-end]')?.textContent?.trim()).toBe('Rematar a actividade…'),
    );
    expect(el.querySelector('[data-detail-edit]')).toBeNull();
    await expectNoAxeViolations(el);

    await tab('people');
    flushMatching((r) => r.url === `${BASE}/a1/registrations`, PEOPLE);
    flushMatching((r) => r.url === `${BASE}/a1/attendance`, []);
    await vi.waitFor(() => expect(el.querySelector('[data-request="r1"]')).not.toBeNull());
  });

  it('«Pasar lista» nunha sesión pecha o detalle e vai á súa pantalla', async () => {
    const el = await open({ staff: 'naron' }, managedActivity());
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await tab('sessions');
    (await expectRequest(`${BASE}/a1/sessions`)).flush([session(madridToday())]);
    await vi.waitFor(() => expect(el.querySelector('[data-session-attend]')).not.toBeNull());
    el.querySelector<HTMLButtonElement>('[data-session-attend]')!.click();
    await closed();
    expect(navigate).toHaveBeenCalledWith(['/', 'naron', 'xestion', 'eventos', 'a1', 'sesions', 's-' + madridToday()]);
  });

  it('Recepción le sen cambiar nada: sen «⋯», «Editar» nin «Rematar»', async () => {
    const el = await open({ staff: 'naron' }, managedActivity());
    expect(el.querySelector('[data-detail-more]')).toBeNull();
    expect(el.querySelector('[data-detail-edit]')).toBeNull();
    await tab('sessions');
    (await expectRequest(`${BASE}/a1/sessions`)).flush([session('2099-12-01')]);
    await stable();
    expect(el.querySelector('[data-detail-end]')).toBeNull();
    expect(el.querySelector('[data-session-more]')).toBeNull();
  });

  it('un día: sen «Sesións»; con persoas, «Cancelar o evento» no menú', async () => {
    const el = await open({ manager: 'naron' }, managedEvent({ confirmed: 1, pending: 1 }));
    expect(el.querySelector('[data-detail-tab="sessions"]')).toBeNull();
    await menu();
    expect(document.querySelector('[data-detail-delete]')).toBeNull();
    document.querySelector<HTMLButtonElement>('[data-detail-cancel]')!.click();
    await accept();
    const cancel = await expectRequest((r) => r.method === 'POST' && r.url === `${BASE}/e1/cancel`);
    expect(cancel.request.headers.get('If-Match')).toBe('"0"');
    cancel.flush(managedEvent({ cancelledAt: '2026-11-11T10:00:00Z', version: 1 }));
    await closed();
  });

  it('sen ninguén apuntado: «Eliminar» confirma e borra; un erro queda no diálogo', async () => {
    const el = await open({ manager: 'naron' }, managedEvent({ confirmed: 0, pending: 0 }));
    await menu();
    document.querySelector<HTMLButtonElement>('[data-detail-delete]')!.click();
    await accept();
    flushProblem(await expectRequest((r) => r.method === 'DELETE'), 409, 'event.has-registrations');
    await vi.waitFor(() => expect(el.querySelector('furar-problem-alert')?.textContent?.trim()).toBeTruthy());
    await menu();
    document.querySelector<HTMLButtonElement>('[data-detail-delete]')!.click();
    await accept();
    (await expectRequest((r) => r.method === 'DELETE' && r.url === `${BASE}/e1`)).flush(null);
    await closed();
  });

  it('cancelado: só «Duplicar» no menú e sen «Editar»', async () => {
    const el = await open({ manager: 'naron' }, managedEvent({ cancelledAt: '2026-11-11T10:00:00Z' }));
    expect(el.querySelector('[data-detail-edit]')).toBeNull();
    await menu();
    expect(document.querySelector('[data-detail-duplicate]')).not.toBeNull();
    expect(document.querySelector('[data-detail-cancel]')).toBeNull();
    expect(document.querySelector('[data-detail-delete]')).toBeNull();
  });

  it('«Rematar a actividade…»: desde que día, cantas sesións e a actividade rematada', async () => {
    const el = await open({ manager: 'naron' }, managedActivity());
    await tab('sessions');
    (await expectRequest(`${BASE}/a1/sessions`)).flush([session('2099-12-01'), session('2099-12-03')]);
    await vi.waitFor(() => expect(el.querySelector('[data-detail-end]')).not.toBeNull());
    el.querySelector<HTMLButtonElement>('[data-detail-end]')!.click();
    // O texto pinta cando chegan as traducións: espérase o texto, non só o elemento.
    await vi.waitFor(() => expect(document.querySelector('[data-end-text]')?.textContent).toContain('2 sesións'));
    await expectNoAxeViolations(top());
    document.querySelector<HTMLButtonElement>('[data-end-confirm]')!.click();
    const end = await expectRequest((r) => r.method === 'POST' && r.url === `${BASE}/a1/end`);
    expect(end.request.body).toEqual({ from: '2099-10-05' });
    end.flush(managedActivity({ version: 1, schedule: { ...managedActivity().schedule!, endedFrom: '2099-10-05' } }));
    (await expectRequest(`${BASE}/a1/sessions`)).flush([]);
    await vi.waitFor(() => expect(el.querySelector('[data-detail-end]')).toBeNull());
  });
});
