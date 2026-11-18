// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { placeIn, placeOf, placeParams } from './place-name';

describe('placeOf', () => {
  it.each([
    ['A Coruña', 'da Coruña', 'de A Coruña'],
    ['O Porriño', 'do Porriño', 'de O Porriño'],
    ['As Pontes de García Rodríguez', 'das Pontes de García Rodríguez', 'de As Pontes de García Rodríguez'],
    ['Os Blancos', 'dos Blancos', 'de Os Blancos'],
    ['Narón', 'de Narón', 'de Narón'],
    ['Oleiros', 'de Oleiros', 'de Oleiros'],
    ['Ames', 'de Ames', 'de Ames'],
    ['  Ourense ', 'de Ourense', 'de Ourense'],
    // A denominación en vez do topónimo: sen «Concello de Concello de…».
    ['Concello de Proba', 'de Proba', 'de Proba'],
    ['concello  da Coruña', 'da Coruña', 'de A Coruña'],
    ['Ayuntamiento de O Porriño', 'do Porriño', 'de O Porriño'],
    ['Concello dos Blancos', 'dos Blancos', 'de Os Blancos'],
    ['Concello', 'de Concello', 'de Concello'],
  ])('%s → gl «%s», es «%s»', (name, gl, es) => {
    expect(placeOf(name, 'gl')).toBe(gl);
    expect(placeOf(name, 'es')).toBe(es);
  });

  it('os parámetros levan o nome e o nome coa preposición', () => {
    expect(placeParams('A Coruña', 'gl')).toEqual({ name: 'A Coruña', ofName: 'da Coruña' });
  });
});

describe('placeIn', () => {
  it.each([
    ['A Coruña', 'na Coruña', 'en A Coruña'],
    ['O Porriño', 'no Porriño', 'en O Porriño'],
    ['As Pontes', 'nas Pontes', 'en As Pontes'],
    ['Os Blancos', 'nos Blancos', 'en Os Blancos'],
    [' Narón ', 'en Narón', 'en Narón'],
    ['Concello da Coruña', 'na Coruña', 'en A Coruña'],
  ])('%s → gl «%s», es «%s»', (name, gl, es) => {
    expect(placeIn(name, 'gl')).toBe(gl);
    expect(placeIn(name, 'es')).toBe(es);
  });
});
