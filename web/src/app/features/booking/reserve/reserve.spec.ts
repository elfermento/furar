// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  durationOptions,
  formatDay,
  hourOptions,
  lastBookableDay,
  offeredSlots,
  readSelection,
  selectionParams,
  totalCents,
  useMatches,
  useOpensOn,
  withPartHints,
} from './reserve';
import { busy, facility, slot } from './reserve.fixture';

describe('Páxina de reservar: regras puras', () => {
  const [parts, whole] = facility().uses;

  it('le o URL e descarta o que non vale', () => {
    expect(readSelection({ uso: 'u1', data: '2026-10-28', hora: '18:00', franxas: '2', parte: 'p2' })).toEqual({
      useId: 'u1',
      date: '2026-10-28',
      time: '18:00',
      slots: 2,
      partId: 'p2',
      change: null,
      booking: null,
      organizationId: null,
    });
    expect(readSelection({ data: '2026-02-30', hora: '25:00', franxas: '0', uso: '<x>' })).toEqual({
      useId: null,
      date: null,
      time: null,
      slots: 1,
      partId: null,
      change: null,
      booking: null,
      organizationId: null,
    });
  });

  it('o URL dunha escolla leva só o que ten valor e volve dar a mesma escolla', () => {
    const selection = readSelection({ uso: 'u1', data: '2026-10-28', hora: '18:00' });
    const params = selectionParams(selection);
    expect(params).toEqual({
      uso: 'u1',
      data: '2026-10-28',
      hora: '18:00',
      franxas: null,
      parte: null,
      cambiar: null,
      reserva: null,
      como: null,
    });
    expect(readSelection(params)).toEqual(selection);
  });

  it('días sen ano en gl e es', () => {
    expect(formatDay('2026-10-27', 'gl', 'long')).toBe('martes, 27 de outubro');
    expect(formatDay('2026-10-28', 'es', 'long')).toBe('miércoles, 28 de octubre');
    expect(formatDay('2026-10-27', 'gl', 'short')).toBe('mar 27 out');
    expect(formatDay('2026-10-28', 'es', 'short')).toBe('mié 28 oct');
  });

  it('só ofrece ocos da API: coa duración, partes libres en todas as franxas', () => {
    const slots = [slot(1020, 2, ['p1']), slot(1080, 2, ['p1', 'p2']), slot(1140, 1, ['p2'])];
    expect(offeredSlots(slots, 1, 60).map((each) => [each.time, each.freeParts])).toEqual([
      ['17:00', ['p1']],
      ['18:00', ['p1', 'p2']],
      ['19:00', ['p2']],
    ]);
    // Dúas horas: 17:00 só ten a norte libre e ás 18:00 tamén; 18:00 → 19:00 só a sur.
    expect(offeredSlots(slots, 2, 60).map((each) => [each.time, each.freeParts])).toEqual([
      ['17:00', ['p1']],
      ['18:00', ['p2']],
    ]);
  });

  it('a API manda null fóra das prazas e sen norma: ofrécense os ocos e non hai último día', () => {
    // Regresión: `null` contaba como «0 prazas» e «0 días de antelación».
    const whole = { ...slot(660, 3), freeParts: null, freeSeats: null } as unknown as ReturnType<typeof slot>;
    expect(offeredSlots([whole], 1, 90).map((each) => [each.time, each.freeSeats])).toEqual([['11:00', null]]);
    const noRule = { maxAdvanceDays: null } as unknown as { maxAdvanceDays?: number };
    expect(lastBookableDay('2026-10-26', noRule)).toBeNull();
    expect(lastBookableDay('2026-10-26', { maxAdvanceDays: 7 })).toBe('2026-11-02');
    expect(lastBookableDay(null, { maxAdvanceDays: 7 })).toBeNull();
  });

  it('duracións ata a norma do uso ou o oco máis longo, e nunca máis de catro', () => {
    expect(durationOptions(parts, [slot(1020, 3)])).toEqual([1, 2]);
    expect(durationOptions(whole, [slot(1020, 1)])).toEqual([1]);
    expect(durationOptions(whole, [slot(600, 9)])).toEqual([1, 2, 3, 4]);
  });

  it('días do uso e total', () => {
    expect(useOpensOn({ ...whole, weekdays: 32 + 64 }, '2026-10-31')).toBe(true);
    expect(useOpensOn({ ...whole, weekdays: 32 + 64 }, '2026-10-27')).toBe(false);
    expect(
      useOpensOn(
        { ...whole, availability: 'DATES', periods: [{ from: '2026-11-01', to: '2026-11-30' }] },
        '2026-10-31',
      ),
    ).toBe(false);
    expect(totalCents(parts, 2)).toBe(6000);
    expect(totalCents({ ...parts, price: { kind: 'FREE', per: 'BOOKING' } }, 2)).toBeNull();
  });
  it('as horas do selector mesturan libres e ocupadas por hora, sen repetir a que xa é libre', () => {
    const offered = offeredSlots([slot(1020, 1), slot(1140, 1)], 1, 60);
    const hours = hourOptions(offered, [busy(1200), busy(1080), busy(1140)]);
    expect(hours.map((hour) => [hour.time, hour.offered === null])).toEqual([
      ['17:00', false],
      ['18:00', true],
      ['19:00', false],
      ['20:00', true],
    ]);
    expect(hourOptions(offered, []).every((hour) => hour.offered)).toBe(true);
  });

  it('unha ocupada libre noutro uso que vale leva ese uso: a primeira parte libre ou, enteiro, o seu nome', () => {
    const hours = hourOptions(offeredSlots([slot(1080, 1)], 1, 60), [busy(1140), busy(1200), busy(1260)]);
    const thirds = {
      useId: 'u1',
      label: null,
      offered: offeredSlots([slot(1080, 1, ['p1']), slot(1140, 1, ['p2', 'p1'])], 1, 60),
    };
    const match = { useId: 'u3', label: 'Partido de dúas horas', offered: offeredSlots([slot(1200, 1)], 1, 60) };
    expect(withPartHints(hours, [thirds, match]).map((hour) => [hour.time, hour.part ?? null])).toEqual([
      ['18:00', null],
      ['19:00', { useId: 'u1', partId: 'p2', label: null }],
      ['20:00', { useId: 'u3', partId: null, label: 'Partido de dúas horas' }],
      ['21:00', null],
    ]);
  });

  it('le os deportes da busca (`?deporte=`, repetible) e quedan ao escoller', () => {
    expect(readSelection({ deporte: ['football_7', '<x>'] }).sports).toEqual(['football_7']);
    expect(readSelection({}).sports).toBeUndefined();
    expect(selectionParams({ ...readSelection({ deporte: 'padel' }), time: '18:00' })['deporte']).toEqual(['padel']);
    expect(useMatches({ sports: ['football', 'football_7'] }, ['football_7'])).toBe(true);
    expect(useMatches({ sports: ['football'] }, ['football_7'])).toBe(false);
    expect(useMatches({ sports: ['football'] }, undefined)).toBe(true);
  });
});
