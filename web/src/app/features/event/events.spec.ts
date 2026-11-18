// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { managedActivity, managedEvent } from './event.fixture';
import { filterEvents, isOpen, kindCounts, seatsText, statusCounts, statusOf, whenLines } from './events';

describe('events: estado, tipo e prazas na lista da xestión', () => {
  const now = new Date('2026-11-11T10:00:00Z');
  const t = (key: string, params?: Record<string, unknown>) => `${key}:${JSON.stringify(params)}`;

  it('publicado, borrador, cancelado ou pasado', () => {
    expect(statusOf(managedEvent(), now)).toBe('PUBLISHED');
    expect(statusOf(managedEvent({ published: false }), now)).toBe('DRAFT');
    expect(statusOf(managedEvent({ cancelledAt: '2026-11-10T10:00:00Z' }), now)).toBe('CANCELLED');
    expect(statusOf(managedEvent({ startsAt: '2026-11-01T10:00:00Z' }), now)).toBe('PAST');
    expect(isOpen(managedEvent(), now)).toBe(true);
    expect(isOpen(managedEvent({ cancelledAt: '2026-11-10T10:00:00Z' }), now)).toBe(false);
  });

  it('unha actividade xa empezada segue aberta ata a última sesión', () => {
    const running = managedActivity({ startsAt: '2026-10-01T15:00:00Z', endsAt: '2027-06-16T16:00:00Z' });
    expect(statusOf(running, now)).toBe('PUBLISHED');
    expect(statusOf({ ...running, endsAt: '2026-11-01T16:00:00Z' }, now)).toBe('PAST');
  });

  it('«162 de 200», «23 apuntadas» ou un trazo, e a lista de espera detrás', () => {
    expect(seatsText(managedEvent(), t)).toBe('event.manage.seatsOf:{"count":162,"total":200}');
    expect(seatsText(managedEvent({ capacity: undefined, confirmed: 23 }), t)).toBe('event.manage.joined:{"count":23}');
    expect(seatsText(managedEvent({ capacity: undefined, confirmed: 0 }), t)).toBe('—');
    expect(seatsText(managedActivity({ waiting: 2, notified: 1 }), t)).toBe(
      'event.manage.seatsOf:{"count":20,"total":20} · event.manage.waiting:{"count":3}',
    );
  });

  it('«Cando»: un día nunha liña; unha actividade, os días e a hora e debaixo as datas', () => {
    const oneDay = whenLines(managedEvent(), 'gl', t, () => 'dom. 20 dec. · 11:00');
    expect(oneDay).toEqual({ main: 'dom. 20 dec. · 11:00', sub: null });
    const activity = whenLines(
      managedActivity(),
      'gl',
      (key) => (key === 'booking.who.and' ? ' e ' : key),
      () => '',
    );
    expect(activity.main).toBe('Luns e mércores · 17:00');
    expect(activity.sub).toBe('event.public.dates');
  });

  it('os filtros do estado e do tipo: todos sen escoller, e os números de cada un', () => {
    const list = [managedEvent({ id: 'a' }), managedEvent({ id: 'b', published: false }), managedActivity({ id: 'c' })];
    expect(filterEvents(list, [], [], now)).toHaveLength(3);
    expect(filterEvents(list, ['DRAFT'], [], now).map((e) => e.id)).toEqual(['b']);
    expect(filterEvents(list, [], ['SCHEDULED'], now).map((e) => e.id)).toEqual(['c']);
    expect(filterEvents(list, ['PUBLISHED'], ['ONE_DAY'], now).map((e) => e.id)).toEqual(['a']);
    expect([...statusCounts(list, now)]).toEqual([
      ['PUBLISHED', 2],
      ['DRAFT', 1],
    ]);
    expect([...kindCounts(list)]).toEqual([
      ['ONE_DAY', 2],
      ['SCHEDULED', 1],
    ]);
  });
});
