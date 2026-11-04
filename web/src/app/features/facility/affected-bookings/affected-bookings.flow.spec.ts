// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { ApiProblem } from '../../../core/errors/api-problem';
import type { AffectedBooking, BookingDecision } from './affected-bookings.store';
import { affectedFrom } from './affected-bookings.store';
import { AffectedBookingsFlow } from './affected-bookings.flow';

const CLUB: AffectedBooking = {
  bookingId: 'b1',
  holderName: 'Club Deportivo Narón',
  date: '2026-11-02',
  startMinute: 480,
  endMinute: 570,
  useName: 'Fútbol 11',
  hasEmail: true,
};
const LUCIA: AffectedBooking = {
  bookingId: 'b2',
  holderName: 'Lucía Varela',
  date: '2026-11-02',
  startMinute: 600,
  endMinute: 660,
  useName: 'Fútbol 7, Campo A',
  hasEmail: false,
};
const ESCOLA: AffectedBooking = {
  bookingId: 'b3',
  holderName: 'Escola Xoán XXIII',
  date: '2026-11-02',
  startMinute: 600,
  endMinute: 660,
  useName: 'Fútbol 7, Campo B',
  hasEmail: false,
};

const conflict = (affected: unknown[]) =>
  new ApiProblem(409, 'facility.affected-bookings', { count: affected.length }, [], 't', '', { affected });

/** Deixa correr as promesas (o 409 chega nunha microtarefa) e despois o render. */
const stable = async () => {
  await new Promise((resolve) => setTimeout(resolve));
  await TestBed.inject(ApplicationRef).whenStable();
};
const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container')!;
const row = (id: string) => dialog().querySelector<HTMLFieldSetElement>(`fieldset[data-booking="${id}"]`)!;
const option = (id: string, label: string) =>
  [...row(id).querySelectorAll<HTMLButtonElement>('[role="radio"]')].find((b) => b.textContent?.trim() === label)!;
const channel = (id: string, index: number) => row(id).querySelectorAll<HTMLInputElement>('input[data-channel]')[index];
const button = (selector: string) => dialog().querySelector<HTMLButtonElement>(selector)!;

describe('AffectedBookingsFlow e o modal de reservas afectadas (D1-19)', () => {
  let flow: AffectedBookingsFlow;
  let calls: (BookingDecision[] | undefined)[];

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    flow = TestBed.inject(AffectedBookingsFlow);
    calls = [];
  });
  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  /** Envío falso: as respostas en orde (un erro lánzase; outra cousa devólvese). */
  function sender(...responses: unknown[]) {
    return async (decisions?: BookingDecision[]) => {
      calls.push(decisions);
      const next = responses.shift();
      if (next instanceof Error) throw next;
      return next as string;
    };
  }

  async function openWith(affected = [CLUB, LUCIA, ESCOLA], ...after: unknown[]) {
    const outcome = flow.run(sender(conflict(affected), ...after), { summary: 'Bloqueo do 2 nov, 8:00–12:00' });
    await stable();
    // Nun obxecto: devolver a promesa dun async faría esperar ao peche do modal.
    return { outcome };
  }

  it('sen 409 non abre nada e devolve o resultado; outro erro chega a quen chama', async () => {
    expect(await flow.run(sender('ok'))).toEqual({ done: true, value: 'ok' });
    expect(dialog()).toBeNull();
    const stale = new ApiProblem(412, 'common.stale-version', {}, [], undefined, '');
    await expect(flow.run(sender(stale))).rejects.toBe(stale);
    expect(affectedFrom(new Error('x'))).toBeNull();
    expect(affectedFrom(conflict([{ bookingId: 1 }, CLUB]))).toEqual([CLUB]);
  });

  it('abre a lista de fieldset co titular e a reserva na lenda, sen nada marcado (P18), e pasa axe', async () => {
    await openWith();
    expect(dialog().getAttribute('role')).toBe('dialog');
    expect(dialog().querySelector('h2')?.textContent).toContain('3 reservas afectadas');
    expect(document.activeElement).toBe(dialog().querySelector('h2'));
    expect(dialog().textContent).toContain('Bloqueo do 2 nov, 8:00–12:00');
    expect(dialog().querySelectorAll('ul.f-row-list > li > furar-affected-booking fieldset')).toHaveLength(3);
    expect(row('b1').querySelector('legend')?.textContent).toContain('Club Deportivo Narón');
    expect(row('b1').querySelector('legend')?.textContent).toContain('Luns 2 nov · 8:00–9:30 · Fútbol 11');
    expect(dialog().querySelectorAll('[role="radio"][aria-checked="true"]')).toHaveLength(0);
    expect(dialog().querySelectorAll('input[data-channel]')).toHaveLength(0);
    expect(row('b1').querySelector('input[type="checkbox"]')?.getAttribute('aria-label')).toBe(
      'Marcar Club Deportivo Narón',
    );
    expect(row('b1').querySelector('[role="radiogroup"]')?.getAttribute('aria-label')).toBe('Que facer');
    await expectNoAxeViolations(dialog());
  });

  it('ao cancelar márcase correo ou, se non ten correo, a web; ao manter quítanse as canles', async () => {
    await openWith();
    option('b1', 'Cancelar').click();
    option('b2', 'Cancelar').click();
    option('b3', 'Cancelar').click();
    await stable();
    const checked = (id: string) =>
      [...row(id).querySelectorAll<HTMLInputElement>('input[data-channel]')]
        .filter((c) => c.checked)
        .map((c) => c.closest('label')?.textContent?.trim());
    expect(checked('b1')).toEqual(['Correo']);
    expect(checked('b2')).toEqual(['Aviso na web']);
    expect(checked('b3')).toEqual(['Aviso na web']);
    // Só se ofrecen as canles posibles: Escola non ten correo; nunca «Chamar» (a reserva non garda teléfono).
    expect(row('b3').querySelectorAll('input[data-channel]')).toHaveLength(1);
    expect(row('b1').querySelectorAll('input[data-channel]')).toHaveLength(2);
    expect(row('b1').querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Como avisar');
    option('b1', 'Manter').click();
    await stable();
    expect(row('b1').querySelectorAll('input[data-channel]')).toHaveLength(0);
    expect(row('b1').textContent).toContain('Mantense e non se avisa.');
    await expectNoAxeViolations(dialog());
  });

  it('«Confirmar» sen decidir todas non envía: aviso e foco no primeiro que falta', async () => {
    await openWith();
    button('[data-confirm]').click();
    await stable();
    expect(calls).toHaveLength(1);
    expect(dialog().querySelector('[role="alert"]')?.textContent).toContain('Falta decidir 3 reservas');
    expect(document.activeElement).toBe(option('b1', 'Cancelar'));
    expect(row('b1').getAttribute('aria-describedby')).toBe('affected-b1-error');
    expect(row('b1').textContent).toContain('Escolle cancelar ou manter.');
    await expectNoAxeViolations(dialog());
    // Cancelada sen canle: o erro pasa a ser da canle e o foco vai a ela.
    option('b1', 'Cancelar').click();
    option('b2', 'Manter').click();
    option('b3', 'Manter').click();
    await stable();
    channel('b1', 0).click();
    await stable();
    button('[data-confirm]').click();
    await stable();
    expect(calls).toHaveLength(1);
    expect(row('b1').textContent).toContain('Escolle polo menos unha forma de avisar.');
    expect(document.activeElement).toBe(channel('b1', 0));
  });

  it('«Seleccionar todas» e «Cancelar as marcadas»; Confirmar reenvía con decisions[] e pecha', async () => {
    const { outcome } = await openWith(undefined, 'saved');
    const bulk = button('[data-bulk="CANCEL"]');
    expect(bulk.disabled).toBe(true);
    const all = dialog().querySelector<HTMLInputElement>('[data-select-all]')!;
    all.click();
    await stable();
    expect(
      [...dialog().querySelectorAll<HTMLInputElement>('fieldset input[type="checkbox"]')].every((c) => c.checked),
    ).toBe(true);
    button('[data-bulk="CANCEL"]').click();
    await stable();
    expect(dialog().querySelector('[role="status"]')?.textContent).toContain('Canceladas: 3');
    option('b3', 'Manter').click();
    await stable();
    button('[data-confirm]').click();
    expect(await outcome).toEqual({ done: true, value: 'saved' });
    expect(calls[1]).toEqual([
      { bookingId: 'b1', action: 'CANCEL', channels: ['EMAIL'] },
      { bookingId: 'b2', action: 'CANCEL', channels: ['WEB'] },
      { bookingId: 'b3', action: 'KEEP', channels: [] },
    ]);
    expect(dialog()).toBeNull();
  });

  it('se a lista cambia entre os dous pasos, amósaa sen pechar e conserva o decidido', async () => {
    const { outcome } = await openWith([CLUB, LUCIA], conflict([CLUB, ESCOLA]), 'saved');
    option('b1', 'Manter').click();
    option('b2', 'Manter').click();
    await stable();
    button('[data-confirm]').click();
    await stable();
    await stable();
    expect(dialog().querySelector('h2')?.textContent).toContain('2 reservas afectadas');
    expect(dialog().textContent).toContain('As reservas afectadas cambiaron');
    expect(row('b2')).toBeNull();
    expect(option('b1', 'Manter').getAttribute('aria-checked')).toBe('true');
    expect(option('b3', 'Manter').getAttribute('aria-checked')).toBe('false');
    option('b3', 'Manter').click();
    await stable();
    button('[data-confirm]').click();
    expect(await outcome).toEqual({ done: true, value: 'saved' });
    expect(calls[2]?.map((d) => d.bookingId)).toEqual(['b1', 'b3']);
  });

  it('Esc ou «Volver» non gardan; un erro no reenvío pecha e chega a quen chama', async () => {
    const { outcome: escaped } = await openWith();
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
    Object.defineProperty(event, 'keyCode', { get: () => 27 });
    dialog().dispatchEvent(event);
    expect(await escaped).toEqual({ done: false });
    expect(calls).toHaveLength(1);

    calls = [];
    const { outcome: back } = await openWith();
    button('[data-back]').click();
    expect(await back).toEqual({ done: false });

    const stale = new ApiProblem(412, 'common.stale-version', {}, [], undefined, '');
    const { outcome: failed } = await openWith([CLUB], stale);
    option('b1', 'Manter').click();
    await stable();
    button('[data-confirm]').click();
    await expect(failed).rejects.toBe(stale);
    expect(dialog()).toBeNull();
  });
});
