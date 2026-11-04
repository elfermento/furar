// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem } from '../../../../../testing/api';
import { expectNoAxeViolations } from '../../../../../testing/axe';
import { renderSchedule } from '../../../../../testing/facility-schedule';
import { FACILITY_URL, schedule } from '../schedule.fixture';
import { facility } from '../../detail/facility.fixture';
import { EXCEPTIONS_URL, LOCAL_HOLIDAY, exception } from './exceptions.fixture';

const withExceptions = () => schedule({ exceptions: [exception(), LOCAL_HOLIDAY] });

async function render(response = withExceptions()) {
  const view = await renderSchedule(response);
  const list = () => view.section().querySelector<HTMLElement>('furar-facility-exception-list')!;
  const form = () => view.dialog()?.querySelector<HTMLElement>('[data-exception-form]') as HTMLElement;
  const apply = () => view.click(view.dialog()!.querySelector('[data-exception-apply]')!);
  const open = async (id: string | null) => {
    if (!view.el.querySelector('[data-edit-save]')) await view.edit();
    await view.click(list().querySelector(id ? `[data-exception-id="${id}"]` : '[data-exception-add]')!);
  };
  return { ...view, list, form, apply, open };
}

describe('Excepcións do horario (D1-23b)', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = () => undefined;
  });

  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('fóra da edición: filas por data co día, o tramo ou «Pechado» e o motivo', async () => {
    const { list } = await render();
    expect(list().querySelector('h3')?.textContent).toContain('Excepcións');
    expect(
      [...list().querySelectorAll('furar-row')].map((row) => row.textContent?.replace(/\s+/g, ' ').trim()),
    ).toEqual(['Luns 12/10/2026 Pechado Festa local', 'Xoves 24/12/2026 9:00 – 14:00 Noiteboa']);
    expect(list().querySelector('button')).toBeNull();
    await expectNoAxeViolations(list());
  });

  it('baleiro: «Sen excepcións», tamén sen tempadas', async () => {
    const { list } = await render(schedule({ seasons: [], currentSeasonId: undefined }));
    expect(list().textContent).toContain('Sen excepcións');
  });

  it('días sen tempada: aviso cos rangos de gaps[]; sen tempadas, só o baleiro do horario', async () => {
    const gaps = [
      { from: '2027-07-01', to: '2027-08-31' },
      { from: '2027-09-15', to: '2027-09-15' },
    ];
    const { section } = await render(schedule({ gaps }));
    const callout = section().querySelector<HTMLElement>('[data-schedule-gaps]')!;
    expect(callout.textContent).toContain('Hai días sen tempada');
    expect([...callout.querySelectorAll('li')].map((li) => li.textContent?.trim())).toEqual([
      'Do 01/07/2027 ao 31/08/2027',
      'O 15/09/2027',
    ]);
    expect(callout.getAttribute('role')).toBeNull();
    await expectNoAxeViolations(section());
  });

  it('sen tempadas non se repite o aviso de días sen tempada', async () => {
    const { section } = await render(
      schedule({ seasons: [], currentSeasonId: undefined, gaps: [{ from: '2026-10-31', to: '2027-10-30' }] }),
    );
    expect(section().querySelector('[data-schedule-gaps]')).toBeNull();
  });

  it('«Excepción»: nace pechada e a data é obrigatoria; aberta, ao gardar POST co tramo e o motivo', async () => {
    const { list, form, apply, open, type, save, click, pickDate, pickTime, dialog, stable } = await render();
    await open(null);
    expect(dialog()?.querySelector('h2')?.textContent).toContain('Nova excepción');
    expect(form().querySelectorAll('furar-time-picker')).toHaveLength(0);
    await expectNoAxeViolations(dialog()!);
    await apply();
    expect(form().querySelector('.f-date-field__button')?.getAttribute('aria-invalid')).toBe('true');
    const date = await pickDate(form(), 0, 30);
    await click(form().querySelector('[role="switch"]')!);
    const pickers = form().querySelectorAll<HTMLElement>('furar-time-picker');
    expect(pickers).toHaveLength(2);
    await pickTime(pickers[1], 720);
    await type(form().querySelector<HTMLInputElement>('input[formcontrolname="reason"]')!, '  Festa do patrón ');
    await apply();
    backend().expectNone({ method: 'POST', url: EXCEPTIONS_URL });
    await save();
    const post = await expectRequest({ method: 'POST', url: EXCEPTIONS_URL });
    expect(post.request.body).toEqual({
      date,
      open: true,
      startMinute: 540,
      endMinute: 720,
      reason: 'Festa do patrón',
    });
    post.flush(exception({ id: 'e3', date, endMinute: 720, reason: 'Festa do patrón', version: 0 }));
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await stable();
      expect(list().querySelectorAll('furar-row')).toHaveLength(3);
    });
  });

  it('maqueta 54: nun festivo, unha nota que non bloquea; o interruptor di «Pechado» ou «Aberto»', async () => {
    const holidays = [{ date: '2026-10-12', name: { gl: 'Festa Nacional', es: 'Fiesta Nacional' }, source: 'BOE' }];
    const { form, apply, open, pickDate, click, dialog } = await render(schedule({ exceptions: [], holidays }));
    await open(null);
    const state = () => form().querySelector('[data-switch-state]')?.textContent?.trim();
    expect(state()).toBe('Pechado');
    expect(form().textContent).not.toContain('Aberto ese día');
    expect(form().querySelector('[role="switch"]')?.getAttribute('aria-label')).toBe('Aberto ese día');
    const note = () => dialog()?.querySelector('[data-exception-holiday]')?.textContent?.replace(/\s+/g, ' ').trim();
    expect(await pickDate(form(), 0, 11)).toBe('2026-10-12');
    await vi.waitFor(() =>
      expect(note()).toContain('É festivo (Festa Nacional). Esta excepción cambia o seu horario.'),
    );
    await expectNoAxeViolations(dialog()!);
    await click(form().querySelector('[role="switch"]')!);
    expect(state()).toBe('Aberto');
    expect(form().querySelectorAll('furar-time-picker')).toHaveLength(2);
    await click(form().querySelector('[role="switch"]')!);
    expect(await pickDate(form(), 0, 12)).toBe('2026-10-13');
    await vi.waitFor(() => expect(note()).toBeUndefined());
    await apply();
    expect(dialog()).toBeNull();
  });

  it('data repetida: erro na data sen pedir á API', async () => {
    const { form, apply, open, pickDate, dialog } = await render(schedule({ exceptions: [LOCAL_HOLIDAY] }));
    await open(null);
    // O 12/10/2026 (mes de hoxe no horario de proba) xa ten a festa local.
    expect(await pickDate(form(), 0, 11)).toBe('2026-10-12');
    await apply();
    expect(form().querySelector('.f-date-field__button')?.getAttribute('aria-invalid')).toBe('true');
    expect(dialog()).not.toBeNull();
  });

  it('cambiar e eliminar: PUT e DELETE co If-Match de cada excepción ao gardar', async () => {
    const { list, form, apply, open, type, save, click, dialog, stable } = await render();
    await open('e1');
    expect(dialog()?.querySelector('h2')?.textContent).toContain('Editar a excepción do 24/12/2026');
    await type(form().querySelector<HTMLInputElement>('input[formcontrolname="reason"]')!, 'Noiteboa curta');
    await apply();
    await open('e2');
    await click(dialog()!.querySelector('[data-exception-delete] button')!);
    expect(list().querySelector('[data-exception-id="e2"]')).toBeNull();
    await save();
    const remove = await expectRequest({ method: 'DELETE', url: `${EXCEPTIONS_URL}/e2` });
    expect(remove.request.headers.get('If-Match')).toBe('"1"');
    remove.flush(null, { status: 204, statusText: 'No Content' });
    const put = await expectRequest({ method: 'PUT', url: `${EXCEPTIONS_URL}/e1` });
    expect(put.request.headers.get('If-Match')).toBe('"2"');
    expect(put.request.body.reason).toBe('Noiteboa curta');
    flushProblem(put, 409, 'facility.exception-date-taken');
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await stable();
      expect(list().querySelector('[role="alert"]')?.textContent).toContain('24/12/2026');
    });
    await open('e1');
    expect(form().querySelector('.f-date-field__button')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('pechada: o corpo vai sen tramo nin motivo baleiro', async () => {
    const { list, form, apply, open, save, pickDate, stable } = await render();
    await open(null);
    const date = await pickDate(form(), 0, 0);
    await apply();
    expect(list().querySelector('[data-exception-id="new-0"]')?.textContent).toContain('Pechado');
    await save();
    const post = await expectRequest({ method: 'POST', url: EXCEPTIONS_URL });
    expect(post.request.body).toEqual({ date, open: false });
    post.flush(exception({ id: 'e3', date, open: false, startMinute: undefined, endMinute: undefined, reason: '' }));
    (await expectRequest(FACILITY_URL)).flush(facility());
    await stable();
  });
});
