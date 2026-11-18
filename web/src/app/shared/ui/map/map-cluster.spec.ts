// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { CLUSTER_RADIUS, clusterMarkers, spreadOffsets } from './map-cluster';

describe('map-cluster', () => {
  it('agrupa as marcas que quedarían unha enriba doutra, co centro na media; as soltas van soas', () => {
    const clusters = clusterMarkers([
      { id: 'a', x: 100, y: 100 },
      { id: 'b', x: 110, y: 120 },
      { id: 'c', x: 400, y: 100 },
      { id: 'd', x: 100, y: 100 },
    ]);
    expect(clusters).toEqual([
      { ids: ['a', 'b', 'd'], x: 310 / 3, y: 320 / 3 },
      { ids: ['c'], x: 400, y: 100 },
    ]);
  });

  it('a distancia do raio xa non agrupa; sen marcas, ningún grupo', () => {
    expect(
      clusterMarkers([
        { id: 'a', x: 0, y: 0 },
        { id: 'b', x: CLUSTER_RADIUS, y: 0 },
      ]).map((c) => c.ids),
    ).toEqual([['a'], ['b']]);
    expect(clusterMarkers([])).toEqual([]);
  });

  it('ao abrir no sitio, cada marca a unha chincheta das outras, arredor do centro', () => {
    expect(spreadOffsets(1)).toEqual([[0, 0]]);
    const offsets = spreadOffsets(3);
    expect(offsets).toHaveLength(3);
    expect(offsets[0]).toEqual([0, -CLUSTER_RADIUS]);
    for (let i = 0; i < offsets.length; i++) {
      const [x1, y1] = offsets[i];
      const [x2, y2] = offsets[(i + 1) % offsets.length];
      expect(Math.hypot(x1 - x2, y1 - y2)).toBeGreaterThanOrEqual(CLUSTER_RADIUS);
    }
    // Moitas: o círculo medra para que non se pisen.
    const many = spreadOffsets(12);
    expect(Math.hypot(...many[0])).toBeGreaterThan(CLUSTER_RADIUS);
  });
});
