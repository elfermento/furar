// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { centsToInput, formatCents, parseCents } from './money';

const NBSP = String.fromCharCode(0xa0);

describe('Importes en euros', () => {
  it('escribe coma decimal, punto de miles e o símbolo despois', () => {
    expect(formatCents(3000)).toBe(`30,00${NBSP}€`);
    expect(formatCents(5)).toBe(`0,05${NBSP}€`);
    expect(formatCents(100000)).toBe(`1.000,00${NBSP}€`);
    expect(centsToInput(1250)).toBe('12,50');
    expect(centsToInput(undefined)).toBe('');
  });

  it('le o que escribe a persoa ou devolve null', () => {
    expect(parseCents('30')).toBe(3000);
    expect(parseCents(' 30,5 € ')).toBe(3050);
    expect(parseCents('12.50')).toBe(1250);
    expect(parseCents('1.000,00')).toBe(100000);
    expect(parseCents('1.000')).toBe(100000);
    expect(parseCents('0,01')).toBe(1);
    expect(parseCents('')).toBeNull();
    expect(parseCents('3,333')).toBeNull();
    expect(parseCents('trinta')).toBeNull();
    expect(parseCents('-5')).toBeNull();
  });
});
