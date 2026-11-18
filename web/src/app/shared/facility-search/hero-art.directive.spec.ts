// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { largest } from './hero-art.directive';

const box = (left: number, right: number, top: number, bottom: number) => ({ left, right, top, bottom });

describe('furarHeroArt: o oco da collaxe no móbil', () => {
  // 390 px de ancho, contido de 16 a 374: «Reserva a túa» (ata 240), «pista» (ata 100) e o botón.
  const title = [box(16, 240, 100, 140), box(16, 100, 140, 180)];
  const button = box(16, 152, 196, 244);

  it('a máis grande que colle á dereita, sen pisar texto, botón nin o encabezado de despois', () => {
    const found = largest([...title, button], 374, 100, 380)!;
    expect(found.right).toBe(374);
    // Non pisa o botón nin a primeira liña (con aire).
    expect(found.left).toBeGreaterThanOrEqual(152 + 16);
    expect(found.top).toBeGreaterThanOrEqual(140 + 16);
    expect(found.bottom).toBeLessThanOrEqual(380);
    // Sobe á beira de «pista» (a liña curta deixa sitio): empeza por riba do botón.
    expect(found.top).toBeLessThan(196);
  });

  it('sen sitio para 90 × 80 px, ningunha', () => {
    expect(largest([box(16, 340, 100, 300)], 374, 100, 320)).toBeNull();
  });
});
