// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Map as LeafletMap } from 'leaflet';
import type { MapBounds } from './map-point';
import { watchView } from './map-view';

describe('watchView: o mapa movido pola persoa', () => {
  afterEach(() => vi.useRealTimers());

  it('o encadre automático non conta; despois de tocalo, avisa 300 ms tras o último movemento', () => {
    vi.useFakeTimers();
    const host = document.createElement('furar-map');
    const element = host.appendChild(document.createElement('div'));
    let moveEnd: () => void = () => undefined;
    const map = {
      on: (_type: string, fn: () => void) => (moveEnd = fn),
      getBounds: () => ({ getSouth: () => 1, getWest: () => 2, getNorth: () => 3, getEast: () => 4 }),
    } as unknown as LeafletMap;
    const moved: MapBounds[] = [];
    let destroy = () => undefined as unknown;
    watchView(element, map, (b) => moved.push(b), { onDestroy: (fn) => (destroy = fn) });

    moveEnd();
    vi.advanceTimersByTime(400);
    expect(moved).toEqual([]);

    host.dispatchEvent(new Event('wheel'));
    moveEnd();
    vi.advanceTimersByTime(200);
    moveEnd();
    vi.advanceTimersByTime(299);
    expect(moved).toEqual([]);
    vi.advanceTimersByTime(1);
    expect(moved).toEqual([{ south: 1, west: 2, north: 3, east: 4 }]);
    destroy();
  });
});
