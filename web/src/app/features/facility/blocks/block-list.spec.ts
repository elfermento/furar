// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { renderBlocks } from '../../../../testing/facility-blocks';
import { ALL_DAY, BLOCKS_URL, block } from './blocks.fixture';

/** Outro bloqueo o mesmo día ca b1, pola tarde. */
const AFTERNOON = block({ id: 'b3', startMinute: 960, endMinute: 1080, reason: 'Torneo', version: 4 });
const AFFECTED = {
  bookingId: 'r1',
  holderName: 'Club Deportivo Narón',
  date: '2026-11-03',
  startMinute: 480,
  endMinute: 570,
  useName: 'Fútbol 11',
  hasEmail: true,
  phone: '981 38 44 10',
};

describe('Bloqueos da instalación (D1-24)', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = () => undefined;
  });

  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('fóra da edición: filas por data coas horas ou «Todo o día» e o motivo, sen lapis', async () => {
    const { section } = await renderBlocks([block(), ALL_DAY]);
    const rows = [...section().querySelectorAll('furar-row')];
    expect(rows.map((r) => r.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      'Martes 03/11/2026 8:00 – 12:00 Mantemento do céspede',
      'Sábado 07/11/2026 Todo o día',
    ]);
    expect(section().querySelector('button')).toBeNull();
    await expectNoAxeViolations(section());
  });

  it('baleiro: «Sen bloqueos»', async () => {
    const { section } = await renderBlocks([]);
    expect(section().querySelector('[data-blocks-empty]')?.textContent).toContain('Sen bloqueos');
  });

  it('erro ao cargar: «Tentar de novo» volve pedir a lista', async () => {
    const { section, click, button, stable } = await renderBlocks('error');
    await click(button('Tentar de novo', section()));
    (await expectRequest({ method: 'GET', url: BLOCKS_URL })).flush([block()]);
    await vi.waitFor(async () => {
      await stable();
      expect(section().querySelectorAll('furar-row')).toHaveLength(1);
    });
  });

  it('«Bloqueo» no modo edición: data obrigatoria; «Aceptar» déixao pendente e «Gardar» fai o POST', async () => {
    const { section, form, apply, open, save, type, pickDate, pickTime, dialog, stable } = await renderBlocks();
    await open(null);
    expect(dialog()?.querySelector('h2')?.textContent).toContain('Novo bloqueo');
    await expectNoAxeViolations(dialog()!);
    await apply();
    expect(form().querySelector('.f-date-field__button')?.getAttribute('aria-invalid')).toBe('true');
    const date = await pickDate(form(), 0, 0);
    const pickers = form().querySelectorAll<HTMLElement>('furar-time-picker');
    await pickTime(pickers[0], 0);
    await pickTime(pickers[1], 1440);
    await type(form().querySelector<HTMLInputElement>('input[formcontrolname="reason"]')!, '  Festa do patrón ');
    await apply();
    expect(dialog()).toBeNull();
    backend().expectNone({ method: 'POST', url: BLOCKS_URL });
    expect(section().querySelector('[data-block-id="new-0"]')?.textContent).toContain('Todo o día');
    await save();
    const post = await expectRequest({ method: 'POST', url: BLOCKS_URL });
    expect(post.request.body).toEqual({ date, startMinute: 0, endMinute: 1440, reason: 'Festa do patrón' });
    post.flush(block({ id: 'b9', date, startMinute: 0, endMinute: 1440, reason: 'Festa do patrón', version: 0 }));
    await vi.waitFor(async () => {
      await stable();
      expect(section().querySelectorAll('furar-row')).toHaveLength(2);
    });
  });

  it('fin antes do inicio e solape cun bloqueo do mesmo día: erro no tramo, sen pechar o modal', async () => {
    const { form, apply, open, pickTime, dialog } = await renderBlocks([block(), AFTERNOON]);
    await open('b3');
    const pickers = form().querySelectorAll<HTMLElement>('furar-time-picker');
    await pickTime(pickers[0], 600);
    await apply();
    expect(form().querySelector('.f-field-error')?.textContent).toContain('Xa hai un bloqueo nesas horas');
    expect(document.activeElement).toBe(pickers[0].querySelector('button'));
    await pickTime(pickers[1], 540);
    await apply();
    expect(form().querySelector('.f-field-error')?.textContent).toContain('O bloqueo remata despois de empezar');
    expect(dialog()).not.toBeNull();
  });

  it('cambiar: PUT co If-Match do bloqueo; con reservas afectadas, o modal de D1-19 e reenvío con decisions[]', async () => {
    const { section, form, apply, open, save, pickTime, click, dialog, stable } = await renderBlocks();
    await open('b1');
    await pickTime(form().querySelectorAll<HTMLElement>('furar-time-picker')[0], 420);
    await apply();
    await save();
    const first = await expectRequest({ method: 'PUT', url: `${BLOCKS_URL}/b1` });
    expect(first.request.headers.get('If-Match')).toBe('"2"');
    flushProblem(first, 409, 'facility.affected-bookings', { params: { count: 1 }, affected: [AFFECTED] });
    await vi.waitFor(async () => {
      await stable();
      expect(dialog()?.textContent).toContain('Club Deportivo Narón');
    });
    const keep = [...dialog()!.querySelectorAll<HTMLButtonElement>('[role="radio"]')].find(
      (b) => b.textContent?.trim() === 'Manter',
    )!;
    await click(keep);
    await click(dialog()!.querySelector('[data-confirm]')!);
    const put = await expectRequest({ method: 'PUT', url: `${BLOCKS_URL}/b1` });
    expect(put.request.body.decisions).toEqual([{ bookingId: 'r1', action: 'KEEP', channels: [] }]);
    put.flush(block({ startMinute: 420, version: 3 }));
    await vi.waitFor(async () => {
      await stable();
      expect(section().querySelector('furar-row')?.textContent).toContain('7:00 – 12:00');
    });
  });

  it('eliminar: sae da lista e, ao gardar, DELETE co If-Match; o erro da API queda no aviso', async () => {
    const { section, open, click, dialog, save, el, stable } = await renderBlocks([block(), ALL_DAY]);
    await open('b2');
    await click(dialog()!.querySelector('[data-block-delete] button')!);
    expect(section().querySelector('[data-block-id="b2"]')).toBeNull();
    await save();
    const remove = await expectRequest({ method: 'DELETE', url: `${BLOCKS_URL}/b2` });
    expect(remove.request.headers.get('If-Match')).toBe('"1"');
    flushProblem(remove, 412, 'common.stale-version');
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('[data-edit-status]')?.textContent).toContain('Non se gardou todo');
    });
    expect(section().querySelector('[role="alert"]')?.textContent).toContain('07/11/2026');
  });

  it('se se volve atrás no modal de reservas afectadas, o bloqueo segue pendente e a ficha en edición', async () => {
    const { section, form, apply, open, save, pickTime, click, dialog, stable, el } = await renderBlocks();
    await open('b1');
    await pickTime(form().querySelectorAll<HTMLElement>('furar-time-picker')[0], 420);
    await apply();
    await save();
    flushProblem(await expectRequest({ method: 'PUT', url: `${BLOCKS_URL}/b1` }), 409, 'facility.affected-bookings', {
      params: { count: 1 },
      affected: [AFFECTED],
    });
    await vi.waitFor(async () => {
      await stable();
      expect(dialog()?.textContent).toContain('Club Deportivo Narón');
    });
    await click(dialog()!.querySelector('[data-back]')!);
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('[data-edit-status]')?.textContent).toContain('Non se gardou todo');
    });
    expect(section().querySelector('[data-block-id="b1"]')?.textContent).toContain('Sen gardar');
  });
});
