// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { preview, series } from '../../../../testing/seasons';
import type { SeasonDraft } from './seasons';
import {
  EMPTY_DRAFT,
  NOTE_MAX,
  capitalized,
  conflictDates,
  conflictText,
  conflictWhen,
  datesText,
  draftErrors,
  draftOf,
  joinList,
  maskToWeekdays,
  minutesText,
  paymentKey,
  paymentOptions,
  placeParts,
  requestBody,
  scheduleOf,
  scheduleParts,
  seriesRows,
  sessionSeriesRow,
  weekdaysText,
  weekdaysToMask,
  yearAfter,
} from './seasons';

/** Un tradutor de proba: a clave cos parámetros. */
const and = ' e ';
const t = (key: string, params?: Record<string, unknown>) =>
  key === 'booking.who.and' ? and : params && Object.keys(params).length ? `${key}${JSON.stringify(params)}` : key;

const full: SeasonDraft = {
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

describe('Tempadas das entidades: regras puras (ADR-0039)', () => {
  it('os días ISO e a máscara do selector van e veñen; fóra de rango non conta', () => {
    expect(weekdaysToMask([1, 3, 7, 9])).toBe(0b1000101);
    expect(maskToWeekdays(0b1000101)).toEqual([1, 3, 7]);
    expect(maskToWeekdays(0)).toEqual([]);
  });

  it('frases: «a», «a e b», «a, b e c»; os días en orde e sen repetir; maiúscula inicial', () => {
    expect(joinList([], and)).toBe('');
    expect(joinList(['luns'], and)).toBe('luns');
    expect(joinList(['luns', 'martes', 'venres'], and)).toBe('luns, martes e venres');
    expect(weekdaysText([3, 1, 3], 'gl', and)).toBe('luns e mércores');
    expect(weekdaysText([5], 'es', ' y ')).toBe('viernes');
    expect(capitalized('luns e mércores', 'gl')).toBe('Luns e mércores');
  });

  it('as horas en reloxo de parede (24:00 ao final do día) e a liña da tempada partida nos « · »', () => {
    expect(minutesText(1140, 1230, 'gl')).toBe('19:00–20:30');
    expect(minutesText(1320, 1440, 'gl')).toBe('22:00–24:00');
    expect(scheduleParts(series(), 'gl', t)).toEqual([
      'luns e mércores',
      '19:00–20:30',
      'booking.seasons.range{"from":"16 nov.","to":"18 xuño"}',
    ]);
  });

  it('a instalación con «· parte · uso» en texto; sen parte, só o uso', () => {
    expect(placeParts(series())).toEqual(['Pavillón', 'Pista central', 'Baloncesto']);
    expect(placeParts(series({ partName: undefined }))).toEqual(['Pavillón', 'Baloncesto']);
  });

  it('o pago: as tres opcións coas cores dos estados', () => {
    expect(paymentKey('PAID')).toBe('booking.mine.status.paid');
    expect(paymentOptions(t).map((o) => [o.value, o.tone])).toEqual([
      ['PENDING', 'warn'],
      ['PAID', 'success'],
      ['FREE', 'info'],
    ]);
  });

  it('os choques: as datas distintas en orde (o `skip`) e o que hai ese día', () => {
    const p = preview();
    expect(conflictDates(p)).toEqual(['2026-11-23', '2026-12-14']);
    expect(conflictDates(null)).toEqual([]);
    const base = p.conflicts[0];
    expect(conflictText(base, t)).toBe('booking.seasons.conflict.BOOKING{"name":"Roi Varela"}');
    expect(conflictText({ ...base, kind: 'SESSION', name: 'Club' }, t)).toBe(
      'booking.seasons.conflict.SESSION{"name":"Club"}',
    );
    expect(conflictText(p.conflicts[1], t)).toBe('booking.seasons.conflict.BLOCK{"name":"Torneo de Nadal"}');
    expect(conflictText({ ...base, kind: 'BLOCK', name: undefined }, t)).toBe('booking.seasons.conflict.blocked');
    expect(conflictText({ ...base, kind: 'OTHER_USE_EXCLUSIVE', name: 'Fútbol' }, t)).toBe(
      'booking.seasons.conflict.OTHER_USE_EXCLUSIVE{"name":"Fútbol"}',
    );
    expect(conflictText({ ...base, kind: 'OUT_OF_HOURS', name: undefined, label: '9:00–14:00' }, t)).toBe(
      'booking.seasons.conflict.OUT_OF_HOURS{"hours":"9:00–14:00"}',
    );
    expect(conflictText({ ...base, kind: 'OUT_OF_HOURS', name: undefined }, t)).toBe(
      'booking.seasons.conflict.closedHours',
    );
  });

  it('cando é un choque: o día e as horas en Europe/Madrid ou «todo o día»', () => {
    const [booking, block] = preview().conflicts;
    expect(conflictWhen(booking, 'gl', t)).toBe('luns 23 nov. · 19:00–20:00');
    expect(conflictWhen(block, 'gl', t)).toBe('luns 14 dec. · booking.seasons.allDay');
  });

  it('as datas en frase, como moito seis e «e N máis»', () => {
    expect(datesText(['2027-01-06', '2027-05-17'], 'gl', t)).toBe('6 xan. e 17 maio');
    const many = Array.from({ length: 8 }, (_, i) => `2027-03-0${i + 1}`);
    expect(datesText(many, 'gl', t)).toContain('booking.seasons.more{"count":2}');
  });

  it('erros do borrador: o que falta, ningún día, horas e datas que non valen, nota longa', () => {
    const today = '2026-11-12';
    expect(draftErrors(full, { organization: true, part: true, today })).toEqual({});
    const empty = draftErrors(EMPTY_DRAFT, { organization: true, part: true, today });
    expect(Object.keys(empty).sort()).toEqual([
      'dates',
      'facility',
      'hours',
      'organization',
      'part',
      'use',
      'weekdays',
    ]);
    expect(empty.weekdays?.key).toBe('errors.booking.series-weekdays-required');
    expect(draftErrors(EMPTY_DRAFT, { organization: false, part: false, today }).organization).toBeUndefined();
    const bad = (change: Partial<SeasonDraft>) =>
      draftErrors({ ...full, ...change }, { organization: true, part: true, today });
    expect(bad({ endMinute: 1140 }).hours?.key).toBe('errors.booking.series-time-invalid');
    expect(bad({ startMinute: 1145 }).hours?.key).toBe('errors.booking.series-time-invalid');
    expect(bad({ firstDate: '2026-11-01' }).dates?.key).toBe('errors.booking.series-range-invalid');
    expect(bad({ lastDate: '2026-11-15' }).dates?.key).toBe('errors.booking.series-range-invalid');
    expect(bad({ lastDate: '2027-12-01' }).dates?.key).toBe('errors.booking.series-range-invalid');
    expect(bad({ note: 'x'.repeat(NOTE_MAX + 1) }).note?.params).toEqual({ max: NOTE_MAX });
  });

  it('un ano despois: 365 días, tamén con anos bisestos', () => {
    expect(yearAfter('2026-11-16')).toBe('2027-11-16');
    expect(yearAfter('2027-03-01')).toBe('2028-02-29');
  });

  it('o que se manda: o horario completo (sen parte se non hai) ou nada; a solicitude coa nota', () => {
    expect(scheduleOf(EMPTY_DRAFT)).toBeNull();
    expect(scheduleOf({ ...full, weekdays: 0 })).toBeNull();
    expect(scheduleOf(full)).toEqual({
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
    expect(scheduleOf({ ...full, partId: null })).not.toHaveProperty('partId');
    expect(requestBody({ ...full, organizationId: null, note: '  Infantil  ' })).toEqual({
      facilityId: 'f1',
      useId: 'u1',
      partId: 'p1',
      weekdays: [1, 3],
      startMinute: 1140,
      endMinute: 1230,
      firstDate: '2026-11-16',
      lastDate: '2027-06-18',
      note: 'Infantil',
    });
    expect(requestBody({ ...full, partId: null })).not.toHaveProperty('partId');
    expect(requestBody({ ...full, note: ' ' })).not.toHaveProperty('note');
    expect(requestBody(EMPTY_DRAFT)).toBeNull();
  });

  it('unha solicitude como borrador: a súa entidade, o horario e a máscara', () => {
    expect(draftOf({ ...series(), partId: undefined })).toEqual({ ...full, partId: null });
  });

  it('as filas do detalle dunha tempada e a fila «Tempada» dunha sesión', () => {
    expect(seriesRows(series(), 'gl', t).map((row) => row.id)).toEqual(['when', 'dates', 'sessions']);
    const ended = seriesRows(series({ cancelled: 3, endedFrom: '2027-03-01' }), 'gl', t);
    expect(ended.map((row) => row.value)).toEqual([
      'luns e mércores · 19:00–20:30',
      'booking.seasons.range{"from":"16 nov.","to":"18 xuño"}',
      'booking.seasons.detail.sessionsCancelled{"count":57,"cancelled":3}',
      '1 de marzo de 2027',
    ]);
    expect(sessionSeriesRow(null, 'gl', t)).toBeNull();
    expect(sessionSeriesRow({ weekdays: [1, 3], sessions: 57 }, 'gl', t)?.value).toBe(
      'luns e mércores · booking.seasons.detail.sessionsCount{"count":57}',
    );
  });
});
