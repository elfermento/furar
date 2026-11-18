// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef, ChangeDetectionStrategy, Component, Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { backend, expectRequest, fakeSession, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { blockerRows } from './account-deletion';
import type { AccountDeletionBlockerResponse } from './account-deletion.store';
import { openAccountDeletionWith } from './open-account-deletion';

const URL = '/api/v1/me/account-deletion';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class PageComponent {}
const t = (key: string, params?: Record<string, unknown>) => (params ? `${key} ${JSON.stringify(params)}` : key);

const BLOCKERS: AccountDeletionBlockerResponse[] = [
  { kind: 'BOOKING', municipality: 'naron', municipalityName: 'Narón', count: 2 },
  { kind: 'OPEN_MATCH', municipality: 'naron', municipalityName: 'Narón', count: 1 },
  {
    kind: 'EVENT',
    municipality: 'ares',
    municipalityName: 'Ares',
    eventId: 'e1',
    eventName: 'Carreira popular',
    count: 1,
  },
  { kind: 'STAFF', municipality: 'ferrol', municipalityName: 'Ferrol', count: 1 },
  { kind: 'PLATFORM_ADMIN', count: 1 },
];

describe('eliminar a conta: o que hai que cancelar antes', () => {
  it('as reservas e as partidas levan a «As miñas reservas» do concello; o evento, á súa ficha; o persoal e a plataforma, sen ligazón', () => {
    const rows = blockerRows(BLOCKERS, t);
    expect(rows.map((r) => r.link)).toEqual([
      ['/', 'naron', 'reservas'],
      ['/', 'naron', 'reservas'],
      ['/', 'ares', 'eventos', 'e1'],
      null,
      null,
    ]);
    expect(rows.map((r) => r.icon)).toEqual(['calendar-month', 'group', 'local-activity', 'tune', 'shield']);
    expect(rows[0].text).toBe('accountDeletion.bookingOther {"count":2,"municipality":"Narón"}');
    expect(rows[1].text).toBe('accountDeletion.openMatchOne {"count":1,"municipality":"Narón"}');
    expect(rows[2].text).toBe('accountDeletion.event {"name":"Carreira popular","municipality":"Ares"}');
    expect(rows[3].text).toBe('accountDeletion.staff {"municipality":"Ferrol"}');
    expect(rows[4].text).toBe('accountDeletion.platformAdmin');
    expect(new Set(rows.map((r) => r.key)).size).toBe(rows.length);
  });
});

describe('«Eliminar a miña conta» desde o menú', () => {
  const stable = () => TestBed.inject(ApplicationRef).whenStable();
  const layers = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')];
  let deleteAccount: ReturnType<typeof vi.fn<() => Promise<void>>>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(fakeSession({})), provideRouter([{ path: '**', component: PageComponent }])],
    });
    deleteAccount = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
  });
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const open = () => openAccountDeletionWith(TestBed.inject(Injector), deleteAccount);

  it('sen nada pendente, vai directo a Keycloak, sen diálogo', async () => {
    const opened = open();
    (await expectRequest(URL)).flush({ allowed: true, blockers: [] });
    await opened;
    expect(deleteAccount).toHaveBeenCalledOnce();
    expect(layers()).toHaveLength(0);
  });

  it('con algo pendente, lístao coas ligazóns e non vai a Keycloak; pasa axe', async () => {
    const opened = open();
    (await expectRequest(URL)).flush({ allowed: false, blockers: BLOCKERS });
    await opened;
    await stable();
    const dialog = layers().at(-1)!;
    expect(dialog.querySelector('.f-dialog__title')?.textContent).toBe('Antes de eliminar a túa conta');
    const links = [...dialog.querySelectorAll<HTMLAnchorElement>('[data-account-deletion-links] a')];
    expect(links.map((a) => [a.textContent?.trim(), a.getAttribute('href')])).toEqual([
      ['2 reservas en Narón', '/naron/reservas'],
      ['Unha partida aberta en Narón', '/naron/reservas'],
      ['Carreira popular · Ares', '/ares/eventos/e1'],
    ]);
    const texts = [...dialog.querySelectorAll('p.f-confirm__detail')].map((p) => p.textContent?.trim());
    expect(texts).toEqual([
      'Es persoal de Ferrol: pide que te quiten do persoal.',
      'Es administración da plataforma: pide que te quiten.',
    ]);
    expect(deleteAccount).not.toHaveBeenCalled();
    await expectNoAxeViolations(dialog);
    links[0].click();
    await stable();
    expect(layers()).toHaveLength(0);
  });

  it('se a API non responde, avisa e non vai a Keycloak', async () => {
    const opened = open();
    (await expectRequest(URL)).flush(null, { status: 503, statusText: 'Unavailable' });
    // A capa aparece antes ca o seu texto (carga á parte): agárdase ao texto, non só á capa.
    await vi.waitFor(() => {
      expect(layers()).toHaveLength(1);
      expect(layers()[0].textContent).toContain('Agora non se pode comprobar a túa conta.');
    });
    const dialog = layers()[0];
    dialog.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    await opened;
    expect(deleteAccount).not.toHaveBeenCalled();
  });
});
