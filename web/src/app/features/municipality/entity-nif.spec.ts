// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { nifError, normaliseNif } from './entity-nif';

/** A mesma táboa ca `EntityNifTest` da API. */
describe('nifError', () => {
  it.each([
    // Concello (P), patronato (Q), fundación (G, control en díxito e en letra), empresa (B)
    ['P1599900F', null],
    ['Q1573421C', null],
    ['G70123450', null],
    ['G7012345J', null],
    ['B70345673', null],
    ['q1573421c', null],
    ['Q-1573421 C', null],
    ['P1505400J', null],
    ['P1503700E', null],
    ['P3208600A', null],
    ['Q1573421D', 'municipality.nif-check-digit'],
    ['P1503700J', 'municipality.nif-check-digit'],
    ['34871265E', 'municipality.nif-personal'],
    ['X1234567L', 'municipality.nif-personal'],
    ['K1234567A', 'municipality.nif-personal'],
    ['I1234567A', 'legal.nif'],
    ['P150540J', 'legal.nif'],
    ['P15054000J', 'legal.nif'],
    ['P1505400K', 'legal.nif'],
  ])('%s → %s', (nif, expected) => {
    expect(nifError(nif)).toBe(expected);
  });

  it('normaliza maiúsculas, espazos, puntos e guións; o baleiro vale', () => {
    expect(normaliseNif(' q.1573421-c ')).toBe('Q1573421C');
    expect(nifError(' - ')).toBeNull();
  });
});
