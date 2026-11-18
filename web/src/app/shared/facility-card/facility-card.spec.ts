// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { collageCovers } from './facility-card';

const cover = (id: string, n: number) => ({ cover: { kind: 'DRAWING', id, large: `${id}-${n}` } });

describe('collageCovers', () => {
  it('unha de cada tipo, primeiro os máis repetidos (a empate, o que vén antes)', () => {
    const covers = [
      cover('football_field', 1),
      cover('football_field', 2),
      cover('fronton', 1),
      cover('pool', 1),
      cover('sports_hall', 1),
      cover('pool', 2),
      cover('sports_hall', 2),
      cover('football_field', 3),
    ];
    expect(collageCovers(covers, 3).map((c) => c.large)).toEqual(['football_field-1', 'pool-1', 'sports_hall-1']);
  });

  it('con menos tipos, repite na orde da lista', () => {
    const covers = [cover('pool', 1), cover('pool', 2), cover('gym', 1), cover('pool', 3)];
    expect(collageCovers(covers, 3).map((c) => c.large)).toEqual(['pool-1', 'gym-1', 'pool-2']);
  });

  it('as fotos cóntanse cada unha', () => {
    const covers = [
      { kind: 'PHOTO', id: 'a' },
      { kind: 'PHOTO', id: 'b' },
    ];
    expect(
      collageCovers(
        covers.map((cover) => ({ cover })),
        3,
      ),
    ).toEqual(covers);
  });
});
