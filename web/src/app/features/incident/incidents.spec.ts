// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Incident } from './incidents';
import { NO_FILTERS, dayOf, monthOf, optionsOf, reporterOf, toggled, visible } from './incidents';
import { incident } from './incident.fixture';

describe('incidencias: filtros, busca e textos', () => {
  const items: Incident[] = [
    incident({ id: 'i1', status: 'NEW', type: 'BREAKDOWN', facilityId: 'f1', facilityName: 'Pista de pádel 2' }),
    incident({
      id: 'i2',
      status: 'RESOLVED',
      type: 'CLEANING',
      facilityId: 'f2',
      facilityName: 'Pavillón de Piñeiros',
      createdAt: '2026-10-29T09:00:00Z',
      reporter: { anonymized: true },
    }),
    incident({ id: 'i3', status: 'NEW', type: 'SAFETY', facilityId: 'f2', facilityName: 'Pavillón de Piñeiros' }),
  ];
  const label = (i: Incident) => `${i.facilityName} ${i.description}`;

  it('a data e o mes van na hora de Galicia', () => {
    expect(dayOf('2026-11-10T23:30:00Z')).toBe('11/11/2026');
    expect(monthOf('2026-10-31T23:30:00Z')).toBe('2026-11');
  });

  it('quen avisou: o nome, se non o correo; anonimizada, ninguén', () => {
    expect(reporterOf(items[0]!)).toBe('Uxía Proba');
    expect(reporterOf(incident({ reporter: { name: undefined, email: 'a@example.org' } }))).toBe('a@example.org');
    expect(reporterOf(items[1]!)).toBeNull();
  });

  it('os filtros dunha columna suman e os de columnas distintas restan; a busca non mira acentos', () => {
    const status = toggled(NO_FILTERS, 'status', 'NEW');
    expect(visible(items, status, '', label).map((i) => i.id)).toEqual(['i1', 'i3']);
    const both = toggled(status, 'facility', 'f2');
    expect(visible(items, both, '', label).map((i) => i.id)).toEqual(['i3']);
    expect(toggled(both, 'facility', 'f2').facility).toEqual([]);
    expect(visible(items, NO_FILTERS, 'pinéiros', label).map((i) => i.id)).toEqual(['i2', 'i3']);
  });

  it('as opcións levan cantas filas e, no estado, a orde Nova, En curso, Resolta co seu ton', () => {
    const options = optionsOf(
      items,
      'status',
      (v) => v,
      (v) => (v === 'NEW' ? 'danger' : 'success'),
    );
    expect(options.map((o) => [o.value, o.count, o.tone])).toEqual([
      ['NEW', 2, 'danger'],
      ['RESOLVED', 1, 'success'],
    ]);
    expect(optionsOf(items, 'facility', (_, i) => i.facilityName).map((o) => o.label)).toEqual([
      'Pavillón de Piñeiros',
      'Pista de pádel 2',
    ]);
    expect(optionsOf(items, 'reporter', (v) => v).map((o) => o.value)).toEqual(['Uxía Proba']);
  });
});
