// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { SLUG_PATTERN } from './municipality-form';
import { suggestSlug } from './slug-suggestion';

describe('suggestSlug', () => {
  it.each([
    ['A Coruña', 'a-coruna'],
    ['Vilagarcía de Arousa', 'vilagarcia-de-arousa'],
    ['O Porriño', 'o-porrino'],
    ['Narón', 'naron'],
    ['  Santiago  de   Compostela ', 'santiago-de-compostela'],
    ["Ponte d'Ulla", 'ponte-d-ulla'],
    ['Açúcar — Vila Nova (VN)', 'acucar-vila-nova-vn'],
    ['Concello Nº 3', 'concello-n-3'],
  ])('%s → %s', (name, slug) => {
    expect(suggestSlug(name)).toBe(slug);
    expect(slug).toMatch(SLUG_PATTERN);
  });

  it('ata 40 caracteres e sen guión ao final', () => {
    const slug = suggestSlug('As Pontes de García Rodríguez e máis concellos da comarca');
    expect(slug.length).toBeLessThanOrEqual(40);
    expect(slug).toMatch(SLUG_PATTERN);
    expect(slug.endsWith('-')).toBe(false);
  });

  it('baleiro se non valería: curto, sen letras ou reservado', () => {
    expect(suggestSlug('A')).toBe('');
    expect(suggestSlug('¿?')).toBe('');
    expect(suggestSlug('Plataforma')).toBe('');
    expect(suggestSlug('ES')).toBe('');
  });
});
