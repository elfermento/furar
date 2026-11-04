// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  filterParams,
  hasCoveredChip,
  matchesCovered,
  matchesSports,
  readFilters,
  slotTime,
  sportChips,
} from './public-list';
import { LIST_FACILITIES } from './public-list.fixture';

const TODAY = '2026-10-26';

describe('listado público: filtros e textos', () => {
  it('le os filtros do URL e descarta o que non vale', () => {
    expect(readFilters({}, TODAY)).toEqual({ sports: [], covered: false, date: TODAY, view: 'list' });
    expect(
      readFilters({ deporte: ['padel', 'padel', 'futsal'], cuberta: 'si', data: '2026-10-28', vista: 'mapa' }, TODAY),
    ).toEqual({
      sports: ['padel', 'futsal'],
      covered: true,
      date: '2026-10-28',
      view: 'map',
    });
    expect(readFilters({ deporte: 'padel', data: '2026-10-01', vista: 'outra' }, TODAY)).toEqual({
      sports: ['padel'],
      covered: false,
      date: TODAY,
      view: 'list',
    });
    expect(readFilters({ data: 'mañá' }, TODAY).date).toBe(TODAY);
  });

  it('escribe no URL só o que non é o valor por defecto', () => {
    expect(filterParams({ sports: [], covered: false, date: TODAY, view: 'list' }, TODAY)).toEqual({
      deporte: null,
      cuberta: null,
      data: null,
      vista: null,
    });
    expect(filterParams({ sports: ['padel'], covered: true, date: '2026-10-27', view: 'map' }, TODAY)).toEqual({
      deporte: ['padel'],
      cuberta: 'si',
      data: '2026-10-27',
      vista: 'mapa',
    });
  });

  it('chips: os deportes sen repetir, en orde do idioma; filtro por calquera deles', () => {
    const twice = [...LIST_FACILITIES, LIST_FACILITIES[1]];
    expect(sportChips(twice, 'es').map((c) => c.key)).toEqual(['c-1', 'football', 'football_7', 'futsal', 'padel']);
    expect(LIST_FACILITIES.filter((f) => matchesSports(f, ['padel', 'futsal'])).map((f) => f.id)).toEqual([
      'f-padel',
      'f-pavillon',
    ]);
    expect(LIST_FACILITIES.filter((f) => matchesSports(f, []))).toHaveLength(3);
  });

  it('cuberta: chip só se algunha o di; o filtro deixa só esas', () => {
    expect(hasCoveredChip(LIST_FACILITIES)).toBe(true);
    expect(hasCoveredChip([{}, { covered: false }])).toBe(false);
    expect(LIST_FACILITIES.filter((f) => matchesCovered(f, true)).map((f) => f.id)).toEqual(['f-pavillon']);
    expect(LIST_FACILITIES.filter((f) => matchesCovered(f, false))).toHaveLength(3);
  });

  it('hora do oco desde o minuto do día', () => {
    expect(slotTime(570)).toBe('09:30');
    expect(slotTime(0)).toBe('00:00');
    expect(slotTime(1260)).toBe('21:00');
  });
});
