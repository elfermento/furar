// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { sessionRows, sessionWindow } from './event-sessions';
import { session } from './event.fixture';

describe('event-sessions: as sesións dunha actividade', () => {
  const today = '2026-11-18';
  const now = new Date('2026-11-18T10:00:00Z');

  it('cancelada, de hoxe ou doutro día; que se pode facer con cada unha', () => {
    const rows = sessionRows(
      [
        session('2026-11-23'),
        session('2026-11-16', { attendanceTaken: true, present: 18, absent: 2 }),
        session(today),
        session('2026-11-25', { cancelledAt: '2026-11-10T10:00:00Z' }),
      ],
      today,
      now,
    );
    expect(rows.map((r) => [r.session.date, r.state, r.past, r.cancellable])).toEqual([
      ['2026-11-16', 'other', true, false],
      [today, 'today', false, true],
      ['2026-11-23', 'other', false, true],
      ['2026-11-25', 'cancelled', false, false],
    ]);
  });

  it('vense desde a última pasada', () => {
    const dates = ['2026-11-04', '2026-11-09', '2026-11-11', '2026-11-16', '2026-11-18', '2026-11-23', '2026-11-25'];
    const rows = sessionRows(
      dates.map((d) => session(d)),
      today,
      now,
    );
    expect(sessionWindow(rows, 3, today).map((r) => r.session.date)).toEqual([
      '2026-11-16',
      '2026-11-18',
      '2026-11-23',
    ]);
    expect(sessionWindow(rows, 3, '2027-01-01').map((r) => r.session.date)).toEqual(['2026-11-25']);
    expect(sessionWindow(rows, 2, '2026-01-01').map((r) => r.session.date)).toEqual(['2026-11-04', '2026-11-09']);
  });
});
