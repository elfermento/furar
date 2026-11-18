// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OpeningDay } from './opening';
import { groupDays, openState } from './opening';

/** Tramo dun día (en UTC, abonda para comparar instantes). */
function slot(date: string, start: number, end: number) {
  const at = (minute: number) => new Date(`${date}T00:00:00Z`).getTime() + minute * 60_000;
  return {
    startMinute: start,
    endMinute: end,
    startsAt: new Date(at(start)).toISOString(),
    endsAt: new Date(at(end)).toISOString(),
  };
}

const DATES = ['2026-10-26', '2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-01'];

function week(hours: (date: string, index: number) => ReturnType<typeof slot>[]): OpeningDay[] {
  return DATES.map((date, index) => ({ date, slots: hours(date, index) }));
}

describe('Horario da ficha pública', () => {
  const split = week((date, i) =>
    i < 5 ? [slot(date, 540, 840), slot(date, 960, 1380)] : i === 5 ? [slot(date, 540, 1260)] : [],
  );

  it('aberto: di ata cando, xuntando tramos seguidos', () => {
    expect(openState(split, new Date('2026-10-26T10:00:00Z'))).toEqual({ open: true, closesAt: 840 });
    const joined = week((date) => [slot(date, 540, 840), slot(date, 840, 1200)]);
    expect(openState(joined, new Date('2026-10-26T10:00:00Z'))).toEqual({ open: true, closesAt: 1200 });
  });

  it('pechado: cando volve abrir, hoxe ou outro día', () => {
    expect(openState(split, new Date('2026-10-26T15:00:00Z'))).toEqual({ open: false, next: { day: 0, minute: 960 } });
    expect(openState(split, new Date('2026-10-26T23:30:00Z'))).toEqual({ open: false, next: { day: 1, minute: 540 } });
    expect(
      openState(
        week(() => []),
        new Date('2026-10-26T10:00:00Z'),
      ),
    ).toEqual({ open: false, next: null });
  });

  it('xunta os días seguidos co mesmo horario, pero hoxe vai só', () => {
    expect(groupDays(split).map(({ from, to }) => [from, to])).toEqual([
      [0, 0],
      [1, 4],
      [5, 5],
      [6, 6],
    ]);
  });
});
