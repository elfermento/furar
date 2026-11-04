// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { FACILITY_IDS, filterOptions } from '../../../testing/indicators';
import type { IndicatorFilters } from './indicator-filters';
import {
  DEFAULT_FILTERS,
  facilityOptions,
  filtersFromParams,
  filtersToParams,
  panelParams,
  useIdsFor,
  useOptions,
} from './indicator-filters';

describe('Filtros dos indicadores no URL (D3-08)', () => {
  it('URL → filtros → URL: ida e volta, sen os valores por defecto', () => {
    const params = { periodo: 'datas', desde: '2026-10-01', ata: '2026-10-15', instalacion: 'f-p1', uso: 'Partido' };
    const filters = filtersFromParams(params);
    expect(filters).toEqual({
      period: 'datas',
      from: '2026-10-01',
      to: '2026-10-15',
      facilityId: 'f-p1',
      use: 'Partido',
    } satisfies IndicatorFilters);
    expect(filtersToParams(filters)).toEqual(params);
    expect(filtersFromParams({})).toEqual(DEFAULT_FILTERS);
    expect(filtersToParams(DEFAULT_FILTERS)).toEqual({
      periodo: null,
      desde: null,
      ata: null,
      instalacion: null,
      uso: null,
    });
    expect(filtersToParams({ ...DEFAULT_FILTERS, period: 'trimestre' })['periodo']).toBe('trimestre');
  });

  it('o que non vale cae no mes en curso: período descoñecido, datas ao revés ou de máis dun ano', () => {
    expect(filtersFromParams({ periodo: 'década' }).period).toBe('mes');
    expect(filtersFromParams({ periodo: 'datas', desde: '2026-10-15', ata: '2026-10-01' }).period).toBe('mes');
    expect(filtersFromParams({ periodo: 'datas', desde: '2025-01-01', ata: '2026-01-02' }).period).toBe('mes');
    expect(filtersFromParams({ periodo: 'datas', desde: '2025-01-01', ata: '2026-01-01' }).period).toBe('datas');
    expect(filtersFromParams({ periodo: 'datas', desde: '2026-02-30', ata: '2026-03-01' }).period).toBe('mes');
    // As datas só van co período «datas».
    expect(filtersFromParams({ periodo: 'ano', desde: '2026-10-01', ata: '2026-10-15' }).from).toBeNull();
  });

  it('o uso vai polo nome: sen instalación, os usos co mesmo nome xuntos; cunha, só o seu', () => {
    const options = filterOptions();
    expect(useIdsFor({ ...DEFAULT_FILTERS, use: 'Partido' }, options)).toEqual(['u-p1', 'u-p2']);
    expect(useIdsFor({ ...DEFAULT_FILTERS, use: 'Partido', facilityId: FACILITY_IDS.padel2 }, options)).toEqual([
      'u-p2',
    ]);
    expect(useOptions(options, null, 'gl').map((o) => [o.label, o.retired])).toEqual([
      ['Campo enteiro', false],
      ['Fútbol 7', true],
      ['Partido', false],
    ]);
    expect(useOptions(options, FACILITY_IDS.padel1, 'gl').map((o) => o.label)).toEqual(['Partido']);
    expect(facilityOptions(options, 'gl').map((o) => [o.label, o.retired])).toEqual([
      ['Campo de fútbol 1 de O Alto', true],
      ['Pista de pádel 1 de O Alto', false],
      ['Pista de pádel 2 de O Alto', false],
    ]);
  });

  it('a petición do panel: período da API, rango, instalación e usos repetidos', () => {
    const filters: IndicatorFilters = {
      period: 'datas',
      from: '2026-10-01',
      to: '2026-10-15',
      facilityId: null,
      use: 'Partido',
    };
    expect(panelParams('naron', filters, ['u-p1', 'u-p2'])).toEqual({
      slug: 'naron',
      period: 'CUSTOM',
      from: '2026-10-01',
      to: '2026-10-15',
      useId: ['u-p1', 'u-p2'],
    });
    expect(panelParams('naron', { ...DEFAULT_FILTERS, period: 'semana' }, [])).toEqual({
      slug: 'naron',
      period: 'WEEK',
    });
  });
});
