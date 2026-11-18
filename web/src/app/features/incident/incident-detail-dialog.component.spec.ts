// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushMatching, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { incident } from './incident.fixture';
import { openIncidentDetail } from './incident-detail-dialog.component';

const URL = '/api/v1/municipalities/naron/incidents/i1';
const stable = () => TestBed.inject(ApplicationRef).whenStable();
const top = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')].at(-1)!;

describe('detalle dunha incidencia', () => {
  afterEach(() => {
    flushMatching((r) => r.url.includes('/photos/') || r.url.endsWith('/new-count'), null);
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function open(roles: Parameters<typeof fakeSession>[0], shown = incident()) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession(roles))] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    void openIncidentDetail(TestBed.inject(DialogService), { slug: 'naron', id: 'i1' });
    (await expectRequest(URL)).flush(shown);
    await vi.waitFor(() => expect(top()?.querySelector('[data-incident-status]')).not.toBeNull());
    await stable();
    return top();
  }

  it('a xestión ve «Eliminar» no pé, á parte de «Gardar», e quitar cada foto', async () => {
    const dialog = await open({ manager: 'naron' });
    expect(dialog.querySelector('[data-incident-delete]')?.textContent?.trim()).toBe('Eliminar');
    expect(dialog.querySelector('[data-incident-save]')?.textContent?.trim()).toBe('Gardar');
    expect(dialog.querySelectorAll('[data-incident-photo-delete]')).toHaveLength(1);
    await expectNoAxeViolations(dialog);
  });

  it('Recepción cambia o estado pero non elimina', async () => {
    const dialog = await open({ staff: 'naron' });
    expect(dialog.querySelector('[data-incident-delete]')).toBeNull();
    expect(dialog.querySelector('[data-incident-photo-delete]')).toBeNull();
    expect(dialog.querySelector('[data-incident-status] furar-chip-menu')).not.toBeNull();
  });

  it('ao escoller «Resolta» aparece a mensaxe para a persoa', async () => {
    const dialog = await open({ manager: 'naron' });
    expect(dialog.querySelector('[data-incident-message]')).toBeNull();
    dialog.querySelector<HTMLButtonElement>('[data-incident-status] button')!.click();
    await stable();
    const resolved = [...document.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]')].find(
      (b) => b.textContent?.trim() === 'Resolta',
    )!;
    resolved.click();
    await stable();
    expect(dialog.textContent).toContain('Mensaxe para Uxía');
    expect(dialog.querySelector('[data-incident-message]')).not.toBeNull();
  });

  async function chooseStatus(dialog: HTMLElement, label: string) {
    dialog.querySelector<HTMLButtonElement>('[data-incident-status] button')!.click();
    await stable();
    [...document.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]')]
      .find((b) => b.textContent?.trim() === label)!
      .click();
    await stable();
  }

  const type = async (field: HTMLTextAreaElement, value: string) => {
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await stable();
  };

  const accept = async () => {
    await vi.waitFor(() => expect(document.querySelector('[data-confirm-accept]')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
  };

  it('«Gardar» manda o estado, a nota e a mensaxe con If-Match, avisa e pecha', async () => {
    const dialog = await open({ manager: 'naron' });
    await chooseStatus(dialog, 'Resolta');
    await type(dialog.querySelector<HTMLTextAreaElement>('[data-incident-note]')!, '  Avisado mantemento ');
    await type(dialog.querySelector<HTMLTextAreaElement>('[data-incident-message]')!, 'Cambiamos a rede.');
    dialog.querySelector<HTMLButtonElement>('[data-incident-save]')!.click();
    const put = await expectRequest((r) => r.method === 'PUT' && r.url === URL);
    expect(put.request.headers.get('If-Match')).toBe('"0"');
    expect(put.request.body).toEqual({
      status: 'RESOLVED',
      internalNote: 'Avisado mantemento',
      resolutionMessage: 'Cambiamos a rede.',
    });
    put.flush(incident({ status: 'RESOLVED', resolvedAt: '2026-11-11T10:00:00Z', version: 1 }));
    await vi.waitFor(() => expect(document.querySelector('furar-incident-detail-dialog')).toBeNull());
  });

  it('un erro ao gardar sae no diálogo, que segue aberto', async () => {
    const dialog = await open({ staff: 'naron' });
    dialog.querySelector<HTMLButtonElement>('[data-incident-save]')!.click();
    const put = await expectRequest((r) => r.method === 'PUT');
    expect(put.request.body).toEqual({ status: 'NEW', internalNote: undefined, resolutionMessage: undefined });
    flushProblem(put, 412, 'common.stale-version');
    await vi.waitFor(() => expect(dialog.querySelector('[data-incident-error]')).not.toBeNull());
    expect(document.querySelector('furar-incident-detail-dialog')).not.toBeNull();
  });

  it('se non carga, «Tentar de novo» vólvea pedir', async () => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({ manager: 'naron' }))] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    void openIncidentDetail(TestBed.inject(DialogService), { slug: 'naron', id: 'i1' });
    flushProblem(await expectRequest(URL), 500, 'common.internal');
    await vi.waitFor(() => expect(top()?.querySelector('furar-status-state button')).not.toBeNull());
    expect(top().querySelector('[data-incident-save]')).toBeNull();
    top().querySelector<HTMLButtonElement>('furar-status-state button')!.click();
    (await expectRequest(URL)).flush(incident());
    await vi.waitFor(() => expect(top().querySelector('[data-incident-status]')).not.toBeNull());
  });

  it('persoa anonimizada: sen correo nin mensaxe ao resolver', async () => {
    const dialog = await open({ manager: 'naron' }, incident({ reporter: { anonymized: true } }));
    expect(dialog.querySelector('[data-incident-person]')?.textContent?.trim()).toBe('Persoa anonimizada');
    await chooseStatus(dialog, 'Resolta');
    expect(dialog.querySelector('[data-incident-message]')).toBeNull();
  });

  it('anotada polo persoal desde unha reserva: «Persoal do concello», a reserva e sen fotos', async () => {
    const dialog = await open(
      { manager: 'naron' },
      incident({
        origin: 'STAFF',
        reporter: { name: undefined, email: undefined },
        photos: [],
        photosRemoved: true,
        booking: { id: 'b1', code: 'ABC', start: '2026-11-10T17:00:00Z', end: '2026-11-10T18:00:00Z', useName: '' },
      }),
    );
    expect(dialog.querySelector('[data-incident-person]')?.textContent?.trim()).toBe('Persoal do concello');
    expect(dialog.querySelector('[data-incident-booking]')?.textContent?.trim()).toBe('10/11 · 18:00–19:00');
    expect(dialog.textContent).toContain('As fotos xa se borraron.');
    await chooseStatus(dialog, 'Resolta');
    expect(dialog.querySelector('[data-incident-message]')).toBeNull();
  });

  it('sen nome pero con correo, a mensaxe é «para a persoa»; a reserva leva o uso', async () => {
    const dialog = await open(
      { manager: 'naron' },
      incident({
        reporter: { name: undefined },
        booking: {
          id: 'b1',
          code: 'ABC',
          start: '2026-11-10T17:00:00Z',
          end: '2026-11-10T18:00:00Z',
          useName: 'Pista',
        },
      }),
    );
    expect(dialog.querySelector('[data-incident-booking]')?.textContent?.trim()).toBe('10/11 · 18:00–19:00 · Pista');
    await chooseStatus(dialog, 'Resolta');
    expect(dialog.textContent).toContain('Mensaxe para a persoa');
  });

  it('«Eliminar» confirma, borra e pecha; se se cancela, nada', async () => {
    const dialog = await open({ manager: 'naron' });
    dialog.querySelector<HTMLButtonElement>('[data-incident-delete] button')!.click();
    await vi.waitFor(() => expect(document.querySelector('[data-confirm-cancel]')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('[data-confirm-cancel]')!.click();
    await stable();
    backend().expectNone((r) => r.method === 'DELETE');
    expect(document.querySelector('furar-incident-detail-dialog')).not.toBeNull();

    dialog.querySelector<HTMLButtonElement>('[data-incident-delete] button')!.click();
    await accept();
    (await expectRequest((r) => r.method === 'DELETE' && r.url === URL)).flush(null);
    await vi.waitFor(() => expect(document.querySelector('furar-incident-detail-dialog')).toBeNull());
  });

  it('un erro ao eliminar sae no diálogo', async () => {
    const dialog = await open({ manager: 'naron' });
    dialog.querySelector<HTMLButtonElement>('[data-incident-delete] button')!.click();
    await accept();
    flushProblem(await expectRequest((r) => r.method === 'DELETE'), 403, 'common.forbidden');
    await vi.waitFor(() => expect(dialog.querySelector('[data-incident-error]')).not.toBeNull());
    expect(document.querySelector('furar-incident-detail-dialog')).not.toBeNull();
  });

  it('quitar unha foto confirma e deixa a incidencia sen ela', async () => {
    const dialog = await open({ manager: 'naron' });
    dialog.querySelector<HTMLButtonElement>('[data-incident-photo-delete] button')!.click();
    await accept();
    (await expectRequest((r) => r.method === 'DELETE' && r.url === `${URL}/photos/p1`)).flush(
      incident({ photos: [], photosRemoved: false, version: 1 }),
    );
    await vi.waitFor(() => expect(dialog.querySelector('[data-incident-photo-delete]')).toBeNull());
  });
});
