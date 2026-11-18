// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { FACILITIES } from './home.fixture';
import { addressLine, dayLabel, sportKey, suggestions } from '../../../shared/facility-search/facility-search';

const GROUPS = { sports: 'Deportes', facilities: 'Instalacións' };

describe('buscador da portada', () => {
  it('suxire deportes (sen repetir, en orde) e instalacións, sen distinguir acentos', () => {
    const options = suggestions(FACILITIES, 'FUT', 'gl', GROUPS);
    expect(options.map((o) => [o.group, o.label])).toEqual([
      ['Deportes', 'Fútbol 11'],
      ['Deportes', 'Fútbol 7'],
      ['Deportes', 'Fútbol sala'],
      ['Instalacións', 'Campo de fútbol 1 de O Alto'],
      ['Instalacións', 'Pavillón de Piñeiros'],
    ]);
    expect(options[0]).toMatchObject({ value: { kind: 'sport', key: 'football' }, icon: 'search' });
    expect(options[3]).toMatchObject({
      value: { kind: 'facility', id: 'f-campo' },
      detail: 'Rúa Buxo, 2',
      image: FACILITIES[0].cover.thumb,
    });
    // Sempre coa miniatura da portada (foto ou debuxo).
    expect(options[4].image).toBe('/api/v1/municipalities/naron/drawings/sports_hall/thumb');
  });

  it('busca tamén polo nome dos deportes da instalación e no idioma de quen mira', () => {
    expect(suggestions(FACILITIES, 'badminton', 'es', GROUPS).map((o) => o.label)).toEqual([
      'Bádminton',
      'Pavillón de Piñeiros',
    ]);
    expect(suggestions(FACILITIES, '   ', 'gl', GROUPS)).toEqual([]);
    expect(suggestions(FACILITIES, 'tenis', 'gl', GROUPS)).toEqual([]);
  });

  it('chave do deporte, enderezo e texto do día', () => {
    expect(sportKey({ code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } })).toBe('padel');
    expect(sportKey({ customSportId: 'c-1', label: { gl: 'X', es: 'X' } })).toBe('c-1');
    expect(addressLine(FACILITIES[1])).toBe('Rúa Buxo');
    const words = { today: 'Hoxe', tomorrow: 'Mañá' };
    expect(dayLabel('2026-10-26', '2026-10-26', 'gl', words)).toBe('Hoxe');
    expect(dayLabel('2026-10-27', '2026-10-26', 'gl', words)).toBe('Mañá');
    expect(dayLabel('2026-10-29', '2026-10-26', 'es', words)).toMatch(/29/);
  });
});
