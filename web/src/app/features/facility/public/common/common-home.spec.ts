// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  COVERED_OPTION,
  catalogParams,
  catalogSportKeys,
  catalogSportOptions,
  matchesCatalogSports,
  byDistance,
  distanceKm,
  formatDistance,
  hasCatalogFilter,
  matchesMunicipality,
  readCatalogFilters,
  railsByMunicipality,
} from './common-home';
import { CATALOG_FACILITIES } from './common-home.fixture';

const TODAY = '2026-11-10';

describe('portada común: filtros e vista', () => {
  it('le os filtros do URL, co concello, e descarta o que non vale', () => {
    expect(readCatalogFilters({}, TODAY)).toEqual({
      sports: [],
      covered: false,
      date: TODAY,
      municipality: null,
      text: '',
      dateChosen: false,
    });
    expect(
      readCatalogFilters({ deporte: ['padel'], cuberta: 'si', data: '2026-11-12', concello: 'naron' }, TODAY),
    ).toEqual({
      sports: ['padel'],
      covered: true,
      date: '2026-11-12',
      municipality: 'naron',
      text: '',
      dateChosen: true,
    });
    expect(readCatalogFilters({ busca: 'Torre' }, TODAY).text).toBe('Torre');
    // Hoxe escollido na folla do móbil: segue sendo hoxe, pero escollido; un día pasado, non.
    expect(readCatalogFilters({ data: TODAY }, TODAY)).toMatchObject({ date: TODAY, dateChosen: true });
    expect(readCatalogFilters({ data: '2026-11-01' }, TODAY)).toMatchObject({ date: TODAY, dateChosen: false });
    expect(readCatalogFilters({ concello: ['naron', 'cambre'], data: '2026-01-01' }, TODAY).municipality).toBeNull();
  });

  it('no URL só vai o que non é o valor por defecto; a vista, nunca', () => {
    expect(catalogParams({ sports: [], covered: false, date: TODAY, municipality: null, text: '' }, TODAY)).toEqual({
      busca: null,
      deporte: null,
      cuberta: null,
      data: null,
      concello: null,
    });
    expect(
      catalogParams(
        { sports: ['padel'], covered: true, date: '2026-11-12', municipality: 'naron', text: 'torre' },
        TODAY,
      ),
    ).toEqual({ busca: 'torre', deporte: ['padel'], cuberta: 'si', data: '2026-11-12', concello: 'naron' });
    // Hoxe escollido na folla: vai no URL para que o chip o siga mostrando.
    expect(
      catalogParams({ sports: [], covered: false, date: TODAY, municipality: null, text: '', dateChosen: true }, TODAY)[
        'data'
      ],
    ).toBe(TODAY);
  });

  it('sen concello escollido, todas; con el, só as súas', () => {
    expect(CATALOG_FACILITIES.filter((f) => matchesMunicipality(f, null))).toHaveLength(CATALOG_FACILITIES.length);
    expect(CATALOG_FACILITIES.filter((f) => matchesMunicipality(f, 'cambre')).map((f) => f.id)).toEqual(['c-pista']);
  });

  it('«Cuberta» non pode confundirse cun deporte', () => {
    expect(COVERED_OPTION).toMatch(/^:/);
  });

  it('os deportes propios únense polo nome sen maiúsculas nin acentos; os do catálogo, pola súa chave', () => {
    const own = (id: string, name: string) => ({ customSportId: id, label: { gl: name, es: name } });
    const padel = { code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } };
    const [cambre, campo] = CATALOG_FACILITIES;
    const facilities = [
      { ...cambre, sports: [padel, own('a', 'Bádminton'), own('b', 'PADEL')] },
      { ...campo, sports: [own('c', 'badminton'), own('d', 'Esgrima')] },
    ];
    const key = catalogSportKeys(facilities, 'gl');
    expect(catalogSportOptions(facilities, 'gl', key)).toEqual([
      { key: '~badminton', name: 'Bádminton' },
      { key: '~esgrima', name: 'Esgrima' },
      { key: 'padel', name: 'Pádel' },
    ]);
    // Un propio que se chama coma un do catálogo vai con el.
    expect(key(own('b', 'PADEL'))).toBe('padel');
    expect(facilities.filter((f) => matchesCatalogSports(f, ['~badminton'], key)).map((f) => f.id)).toEqual([
      cambre.id,
      campo.id,
    ]);
    expect(facilities.filter((f) => matchesCatalogSports(f, ['~esgrima'], key)).map((f) => f.id)).toEqual([campo.id]);
    expect(facilities.filter((f) => matchesCatalogSports(f, [], key))).toHaveLength(2);
  });
});

describe('portada común: preto de min', () => {
  const NARON = { latitude: 43.5167, longitude: -8.1903 };
  const CAMBRE = { latitude: 43.2944, longitude: -8.3436 };

  it('distancia en liña recta entre dous puntos', () => {
    expect(distanceKm(NARON, NARON)).toBe(0);
    // Narón–Cambre: uns 27,5 km.
    expect(distanceKm(NARON, CAMBRE)).toBeCloseTo(27.5, 0);
    expect(distanceKm(CAMBRE, NARON)).toBeCloseTo(distanceKm(NARON, CAMBRE), 9);
  });

  it('«350 m», «1,2 km», «26 km», no formato do idioma', () => {
    expect(formatDistance(0.01, 'gl')).toBe('50 m');
    expect(formatDistance(0.349, 'gl')).toBe('350 m');
    expect(formatDistance(0.99, 'gl')).toBe('1 km');
    expect(formatDistance(1.234, 'gl')).toBe('1,2 km');
    expect(formatDistance(1.234, 'es')).toBe('1,2 km');
    expect(formatDistance(9.94, 'gl')).toBe('9,9 km');
    expect(formatDistance(26.4, 'gl')).toBe('26 km');
  });

  it('ordena da máis próxima á máis lonxana; as que non teñen punto, ao final na súa orde', () => {
    const [cambre, ...naron] = CATALOG_FACILITIES;
    const lost = { ...naron[0], id: 'sen-punto', address: { ...naron[0].address, latitude: undefined } };
    const sorted = byDistance([lost, cambre, ...naron], { latitude: 43.3, longitude: -8.34 });
    expect(sorted.map((s) => s.facility.id)).toEqual(['c-pista', ...naron.map((f) => f.id), 'sen-punto']);
    expect(sorted[0].km).toBeLessThan(1);
    expect(sorted.at(-1)?.km).toBeNull();
    // Desde Narón, Cambre vai ao final (de entre as que teñen punto).
    expect(
      byDistance(CATALOG_FACILITIES, NARON)
        .map((s) => s.facility.id)
        .at(-1),
    ).toBe('c-pista');
  });

  it('sen filtros, un carrusel por concello na orde na que veñen; calquera filtro (un día que non é hoxe tamén) cambia aos resultados; hoxe non', () => {
    expect(
      railsByMunicipality(CATALOG_FACILITIES).map((r) => [r.municipality.name, r.facilities.map((f) => f.id)]),
    ).toEqual([
      ['Cambre', ['c-pista']],
      ['Narón', CATALOG_FACILITIES.filter((f) => f.municipality.slug === 'naron').map((f) => f.id)],
    ]);
    const none = readCatalogFilters({}, TODAY);
    expect(hasCatalogFilter(none, TODAY)).toBe(false);
    // Escoller hoxe non é un filtro.
    expect(hasCatalogFilter(readCatalogFilters({ data: TODAY }, TODAY), TODAY)).toBe(false);
    for (const params of [
      { busca: 'torre' },
      { concello: 'naron' },
      { deporte: 'padel' },
      { cuberta: 'si' },
      { data: '2026-11-11' },
    ]) {
      expect(hasCatalogFilter(readCatalogFilters(params, TODAY), TODAY)).toBe(true);
    }
  });
});
