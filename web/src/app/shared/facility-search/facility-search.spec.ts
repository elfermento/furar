// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { SearchFacility } from './facility-search';
import { matchesText, municipalitiesOf, placeLine, suggestions } from './facility-search';

function facility(id: string, name: string, town: { slug: string; name: string } | null, sport = 'Pádel') {
  return {
    id,
    name,
    address: { street: 'Rúa Buxo', streetNumber: '12' },
    sports: [{ code: sport.toLowerCase(), label: { gl: sport, es: sport } }],
    cover: { thumb: `/${id}.jpg` },
    ...(town ? { municipality: town } : {}),
  } satisfies SearchFacility;
}

const NARON = { slug: 'naron', name: 'Narón' };
const CAMBRE = { slug: 'cambre', name: 'Cambre' };
const CATALOG = [
  facility('n1', 'Pavillón da Gándara', NARON, 'Baloncesto'),
  facility('n2', 'Pista de pádel 1', NARON),
  facility('c1', 'Pista de pádel', CAMBRE),
];
const GROUPS = { sports: 'Deportes', facilities: 'Instalacións' };

describe('buscador de instalacións: concellos (portada común)', () => {
  it('o lugar leva o concello diante do enderezo só se a instalación o trae', () => {
    expect(placeLine(CATALOG[0])).toBe('Narón · Rúa Buxo, 12');
    expect(placeLine(facility('x', 'Pista', null))).toBe('Rúa Buxo, 12');
    // O lugar igual ao concello (sen mirar maiúsculas nin acentos) dise unha vez.
    const same = {
      ...facility('v', 'Campo', { slug: 'vilardevos', name: 'Vilardevós' }),
      address: { street: 'VILARDEVOS' },
    };
    expect(placeLine(same)).toBe('VILARDEVOS');
  });

  it('os concellos das instalacións, sen repetir e en orde alfabética', () => {
    expect(municipalitiesOf(CATALOG, 'gl')).toEqual([CAMBRE, NARON]);
    expect(municipalitiesOf([facility('x', 'Pista', null)], 'gl')).toEqual([]);
  });

  it('co grupo de concellos, suxire o concello e atopa as súas instalacións polo nome del, sen acentos', () => {
    const found = suggestions(CATALOG, 'naron', 'gl', { ...GROUPS, municipalities: 'Concellos' });
    expect(found.map((o) => [o.group, o.label, o.detail])).toEqual([
      ['Concellos', 'Narón', undefined],
      ['Instalacións', 'Pavillón da Gándara', 'Narón · Rúa Buxo, 12'],
      ['Instalacións', 'Pista de pádel 1', 'Narón · Rúa Buxo, 12'],
    ]);
    expect(found[0].value).toEqual({ kind: 'municipality', slug: 'naron' });
  });

  it('sen o grupo de concellos (o listado dun concello), non se suxiren', () => {
    const found = suggestions(CATALOG, 'padel', 'gl', GROUPS);
    expect(found.map((o) => o.group)).toEqual(['Deportes', 'Instalacións', 'Instalacións']);
    expect(suggestions(CATALOG, 'cambre', 'gl', GROUPS).map((o) => o.group)).toEqual(['Instalacións']);
  });
});

describe('buscador de instalacións: deportes propios co mesmo nome', () => {
  const own = (id: string, name: string) => ({ customSportId: id, label: { gl: name, es: name } });
  const OWN = [
    { ...facility('n3', 'Pavillón de Narón', NARON), sports: [own('n-1', 'Bádminton')] },
    { ...facility('c2', 'Pavillón de Cambre', CAMBRE), sports: [own('c-1', 'Badminton')] },
  ];
  const sports = (found: ReturnType<typeof suggestions>) =>
    found.filter((o) => o.group === 'Deportes').map((o) => [o.label, o.value]);

  it('sen chave propia (un concello), agrupa polo id: cada deporte propio é o seu', () => {
    expect(sports(suggestions(OWN, 'badm', 'gl', GROUPS))).toEqual([
      ['Badminton', { kind: 'sport', key: 'c-1' }],
      ['Bádminton', { kind: 'sport', key: 'n-1' }],
    ]);
  });

  it('coa chave da portada común, o mesmo nome en dous concellos sae unha soa vez, coa chave común', () => {
    const byName = (sport: { label: { gl?: string } }) => '~' + (sport.label.gl ?? '').toLowerCase().replace('á', 'a');
    const found = suggestions(OWN, 'badm', 'gl', { ...GROUPS, municipalities: 'Concellos' }, byName);
    expect(sports(found)).toEqual([['Bádminton', { kind: 'sport', key: '~badminton' }]]);
    // As instalacións dos dous concellos seguen saíndo.
    expect(found.filter((o) => o.group === 'Instalacións').map((o) => o.label)).toEqual([
      'Pavillón de Narón',
      'Pavillón de Cambre',
    ]);
  });
});

describe('buscador de instalacións: texto solto', () => {
  const ids = (text: string, lang: 'gl' | 'es' = 'gl') =>
    CATALOG.filter((f) => matchesText(f, text, lang)).map((f) => f.id);

  it('filtra polo nome, o concello, o enderezo ou un deporte, sen acentos nin maiúsculas', () => {
    expect(ids('GANDARA')).toEqual(['n1']);
    expect(ids('naron')).toEqual(['n1', 'n2']);
    expect(ids('buxo 12')).toEqual(['n1', 'n2', 'c1']);
    expect(ids('baloncesto')).toEqual(['n1']);
  });

  it('cada palabra ten que estar; sen texto, todas', () => {
    expect(ids('pista cambre')).toEqual(['c1']);
    expect(ids('pista torre')).toEqual([]);
    expect(ids('   ')).toEqual(['n1', 'n2', 'c1']);
  });
});
