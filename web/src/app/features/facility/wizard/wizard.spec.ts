// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { SportResponse } from '../data-card/data-form';
import { use as useFixture } from '../uses/uses.fixture';
import {
  DEFAULT_HOURS,
  REVIEW,
  STEP,
  modeOf,
  presetsFor,
  priceText,
  resumeStep,
  sameWeek,
  stepLabelKey,
  stepState,
  wholeYear,
} from './wizard';

const sport = (code: string): SportResponse => ({ code, label: { gl: code, es: code } });
/** Tradución de proba: a clave, cos parámetros ao final. */
const t = (key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key);

describe('asistente «Nova instalación»: lóxica', () => {
  it('un borrador segue no primeiro paso obrigatorio sen completar; se non falta nada, en «Revisar»', () => {
    const address = { latitude: 43.5, longitude: -8.2 };
    expect(resumeStep({ address: null, missing: ['SPORTS', 'USES', 'SCHEDULE'] }, true)).toBe(STEP.data);
    expect(resumeStep({ address: null, missing: ['USES', 'SCHEDULE'] }, true)).toBe(STEP.location);
    expect(resumeStep({ address, missing: ['USES', 'SCHEDULE'] }, true)).toBe(STEP.uses);
    expect(resumeStep({ address, missing: ['SCHEDULE'] }, true)).toBe(STEP.schedule);
    expect(resumeStep({ address, missing: [] }, true)).toBe(REVIEW);
    // Sen mapa no despregue, o enderezo sen punto vale; con mapa, falta a chincheta.
    const noPoint = { latitude: null, longitude: null };
    expect(resumeStep({ address: noPoint, missing: [] }, false)).toBe(REVIEW);
    expect(resumeStep({ address: noPoint, missing: [] }, true)).toBe(STEP.location);
  });

  it('estado de cada paso no indicador', () => {
    expect(stepState(2, 2, 4, false)).toBe('current');
    expect(stepState(1, 2, 4, true)).toBe('done');
    expect(stepState(1, 2, 4, false)).toBe('todo');
    expect(stepState(5, 2, 4, true)).toBe('pending');
    expect(stepState(REVIEW, 2, REVIEW, true)).toBe('pending');
  });

  it('«Todo o ano» empeza o 1 de xaneiro de hoxe e non ten fin', () => {
    expect(wholeYear('2026-11-03')).toEqual({ startDate: '2026-01-01' });
  });

  it('o mesmo horario todos os días deixa os festivos pechados e lese de volta igual', () => {
    const week = sameWeek({ startMinute: 540, endMinute: 1260 });
    expect(week).toHaveLength(8);
    expect(week.filter((row) => row.intervals.length)).toHaveLength(7);
    expect(week.at(-1)).toEqual({ day: 'HOLIDAYS', intervals: [] });
    expect(modeOf(week)).toEqual({ mode: 'SAME', same: { startMinute: 540, endMinute: 1260 } });
    const saturday = week.map((row) =>
      row.day === 'SATURDAY' ? { ...row, intervals: [{ startMinute: 600, endMinute: 840 }] } : row,
    );
    expect(modeOf(saturday)).toEqual({ mode: 'DAYS', same: { startMinute: 540, endMinute: 1260 } });
    expect(modeOf(sameWeek(DEFAULT_HOURS).map((row) => ({ ...row, intervals: [] }))).mode).toBe('DAYS');
  });

  it('os atallos saen dos deportes, sen repetir nin chocar cos usos que xa hai', () => {
    const presets = presetsFor([sport('basketball'), sport('futsal'), sport('swimming')], [], t);
    expect(presets.map((p) => p.name)).toEqual([
      'facility.wizard.uses.presets.wholeCourt',
      'facility.wizard.uses.presets.halfCourt',
      'facility.wizard.uses.presets.lanes',
      'facility.wizard.uses.presets.freeSwim',
    ]);
    expect(presets[1]).toMatchObject({ rentalMode: 'PARTS', capacity: null });
    expect(presets[1].parts).toHaveLength(2);
    expect(presets[2].parts).toHaveLength(6);
    expect(presets[3]).toMatchObject({ rentalMode: 'SEATS', capacity: 40 });
    const existing = useFixture({ name: 'FACILITY.WIZARD.USES.PRESETS.WHOLECOURT' });
    expect(presetsFor([sport('handball')], [existing], t).map((p) => p.name)).toEqual([
      'facility.wizard.uses.presets.halfCourt',
    ]);
    expect(presetsFor([{ customSportId: 'x', label: { gl: 'Petanca', es: 'Petanca' } }], [], t)).toEqual([]);
  });

  it('o prezo dunha fila e o nome do paso 7 segundo o pago', () => {
    expect(priceText({ price: { kind: 'PAID', amountCents: 4500, per: 'BOOKING' } }, t)).toBe('45,00 €');
    expect(priceText({ price: { kind: 'FREE', per: 'BOOKING' } }, t)).toBe('facility.uses.price.FREE');
    expect(priceText({ price: { kind: 'NONE', per: 'BOOKING' } }, t)).toBe('facility.uses.price.NONE');
    expect(stepLabelKey('rules', 'ALL')).toBe('facility.wizard.steps.rules');
    expect(stepLabelKey('rules', 'FACILITY')).toBe('facility.wizard.steps.rulesAndPayment');
    expect(stepLabelKey('uses', 'FACILITY')).toBe('facility.wizard.steps.uses');
  });
});
