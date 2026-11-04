// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { deckPoses, sectionProgress } from './step-deck';

describe('baralla «Así de fácil» (respaldo en JS)', () => {
  it('ao comezo só se ve a primeira; ao final están as tres pousadas e apiladas', () => {
    expect(deckPoses(0, 3)).toEqual([
      { t: 1, u: 0 },
      { t: 0, u: 0 },
      { t: 0, u: 0 },
    ]);
    const end = deckPoses(1, 3);
    expect(end.map((p) => p.t)).toEqual([1, 1, 1]);
    expect(end[0].u).toBe(1);
    expect(end[1].u).toBe(1);
    expect(end[2].u).toBeCloseTo(0.3);
  });

  it('a segunda entra antes ca terceira e o progreso acóutase', () => {
    const middle = deckPoses(0.3, 3);
    expect(middle[1].t).toBeGreaterThan(0.5);
    expect(middle[2].t).toBe(0);
    expect(deckPoses(-1, 3)).toEqual(deckPoses(0, 3));
    expect(deckPoses(2, 3)).toEqual(deckPoses(1, 3));
  });

  it('progreso da sección fixa', () => {
    expect(sectionProgress(0, 3000, 1000)).toBe(0);
    expect(sectionProgress(-1000, 3000, 1000)).toBe(0.5);
    expect(sectionProgress(-5000, 3000, 1000)).toBe(1);
    expect(sectionProgress(-10, 800, 1000)).toBe(0);
  });
});
