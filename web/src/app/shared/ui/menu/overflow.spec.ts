// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { overflow } from './overflow';

describe('overflow', () => {
  it('se caben, todos á vista e nada en «Máis»', () => {
    expect(overflow([1, 2, 3, 4], 4)).toEqual({ visible: [1, 2, 3, 4], more: [] });
  });

  it('se non, os max − 1 primeiros e o resto en «Máis», na mesma orde', () => {
    expect(overflow([1, 2, 3, 4, 5], 4)).toEqual({ visible: [1, 2, 3], more: [4, 5] });
    expect(overflow([1, 2], 1)).toEqual({ visible: [], more: [1, 2] });
  });
});
