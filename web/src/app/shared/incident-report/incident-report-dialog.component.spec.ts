// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { pickOption, selectButton } from '../../../testing/select';
import { DialogService } from '../ui/dialog/dialog.service';
import { ToastService } from '../ui/toast/toast.service';
import { openIncidentReport } from './open-incident-report';

const REPORT = '/api/v1/municipalities/naron/incidents/reports';
const stable = () => TestBed.inject(ApplicationRef).whenStable();
const top = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')].at(-1)!;
const opened = async () => {
  await vi.waitFor(() => expect(document.querySelector('furar-dialog-container form')).not.toBeNull());
  await stable();
};
const submit = () =>
  top()
    .querySelector('form')!
    .dispatchEvent(new Event('submit', { cancelable: true }));

describe('«Avisar dun problema» e «Nova incidencia»', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({}))] }));
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  function open(mode: 'resident' | 'staff' = 'resident') {
    const sent = openIncidentReport(TestBed.inject(DialogService), {
      slug: 'naron',
      mode,
      facilityId: mode === 'resident' ? 'f1' : undefined,
      bookingId: mode === 'resident' ? 'b1' : undefined,
      subheading: 'Pista de pádel 2',
      facilities: [{ value: 'f1', label: 'Pista de pádel 2' }],
    });
    return sent;
  }

  it('tipo nun despregable, descrición co «?» de non poñer nomes e fotos; sen tipo nin texto, non envía', async () => {
    void open();
    await opened();
    const dialog = top();
    expect(dialog.querySelector('.f-dialog__title')?.textContent).toBe('Avisar dun problema');
    selectButton(dialog.querySelector('[data-incident-type]')!).click();
    await stable();
    expect([...document.querySelectorAll('.f-select__option')].map((o) => o.textContent?.trim())).toEqual([
      'Avaría',
      'Limpeza',
      'Seguridade',
      'Outro',
    ]);
    // Pechase picando outra vez no campo.
    selectButton(dialog.querySelector('[data-incident-type]')!).click();
    await stable();
    expect(document.querySelector('.f-select__option')).toBeNull();
    expect(dialog.querySelector('[data-incident-facility]')).toBeNull();
    submit();
    await stable();
    expect(dialog.textContent).toContain('Escolle que pasa.');
    expect(dialog.textContent).toContain('Conta que pasa.');
    await expectNoAxeViolations(dialog);
  });

  it('envía multipart coa reserva e as fotos, pecha e avisa cun toast', async () => {
    const sent = open();
    await opened();
    const dialog = top();
    await pickOption(dialog.querySelector('[data-incident-type]')!, 'Limpeza', stable);
    const text = dialog.querySelector<HTMLTextAreaElement>('[data-incident-description]')!;
    text.value = 'Papeleiras cheas';
    text.dispatchEvent(new Event('input'));
    const file = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'rede.jpg', { type: 'image/jpeg' });
    const input = dialog.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
    await vi.waitFor(() => expect(dialog.querySelectorAll('.f-incident-photos__item img')).toHaveLength(1));
    submit();
    const request = await expectRequest(REPORT);
    const body = request.request.body as FormData;
    expect(body.get('type')).toBe('CLEANING');
    expect(body.get('facilityId')).toBe('f1');
    expect(body.get('bookingId')).toBe('b1');
    expect(body.getAll('photos')).toHaveLength(1);
    request.flush({ id: 'i1' }, { status: 201, statusText: 'Created' });
    expect(await sent).toBe(true);
    expect(TestBed.inject(ToastService).current()?.key).toBe('incident.report.sent');
  });

  it('un erro da API queda na capa; un ficheiro que non é foto avisa sen engadilo', async () => {
    void open();
    await opened();
    const dialog = top();
    const input = dialog.querySelector<HTMLInputElement>('input[type="file"]')!;
    const gif = new File([new Uint8Array([1])], 'xif.gif', { type: 'image/gif' });
    Object.defineProperty(input, 'files', { value: [gif], configurable: true });
    input.dispatchEvent(new Event('change'));
    await vi.waitFor(() => expect(dialog.textContent).toContain('xif.gif: sube a foto en JPEG ou PNG.'));
    await pickOption(dialog.querySelector('[data-incident-type]')!, 'Outro', stable);
    const text = dialog.querySelector<HTMLTextAreaElement>('[data-incident-description]')!;
    text.value = 'Algo';
    text.dispatchEvent(new Event('input'));
    submit();
    flushProblem(await expectRequest(REPORT), 429, 'incident.too-many-reports', { params: { max: 10 } });
    await stable();
    expect(dialog.querySelector('[data-incident-error]')?.textContent).toContain('10 avisos');
  });

  it('o persoal escolle a instalación', async () => {
    void open('staff');
    await opened();
    const dialog = top();
    expect(dialog.querySelector('.f-dialog__title')?.textContent).toBe('Nova incidencia');
    expect(dialog.querySelector('[data-incident-facility]')).not.toBeNull();
    submit();
    await stable();
    expect(dialog.textContent).toContain('Escolle a instalación.');
  });
});
