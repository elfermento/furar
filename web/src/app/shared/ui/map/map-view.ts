// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Map as LeafletMap } from 'leaflet';
import type { Leaflet } from './leaflet-map';
import type { MapBounds, MapPoint } from './map-point';

/** Sen punto: Galicia enteira. */
export const GALICIA: MapPoint = { latitude: 42.8, longitude: -8 };
/** Zoom de rúa: o editable vai ata aquí cando o punto chega de fóra (o buscador de enderezos). */
export const STREET_ZOOM = 17;
/** Zoom máximo ao encadrar unha área (o concello, sen chegar ao detalle da rúa). */
const AREA_ZOOM = 15;

/** Vai ao punto con zoom de rúa, sen afastar se xa se está máis preto. */
export function focusPoint(map: LeafletMap, point: MapPoint, animate: boolean): void {
  map.setView([point.latitude, point.longitude], Math.max(map.getZoom(), STREET_ZOOM), { animate });
}

/**
 * Encadra os puntos (un só, co zoom do concello). Se o mapa aínda non ten tamaño (a folla de
 * Leaflet sen cargar), agarda a que o teña; `still` di se aínda fai falta (p. ex. sen chincheta).
 */
export function frameArea(L: Leaflet, map: LeafletMap, area: readonly MapPoint[], still: () => boolean): void {
  const frame = () => {
    if (!still()) return;
    if (area.length === 1) {
      map.setView([area[0].latitude, area[0].longitude], AREA_ZOOM, { animate: false });
      return;
    }
    const bounds = L.latLngBounds(area.map((p): [number, number] => [p.latitude, p.longitude]));
    map.fitBounds(bounds, { padding: [48, 48], maxZoom: AREA_ZOOM, animate: false });
  };
  if (map.getSize().x > 0) {
    frame();
  } else {
    map.once('resize', frame);
  }
}

/** Zoom de «Preto de min»: o barrio arredor de quen mira (uns poucos quilómetros). */
export const NEAR_ZOOM = 13;

/** Avisa cando o marco cambia de tamaño (p. ex. o mapa da lista crece ao marchar a barra). */
export function watchSize(
  view: (Window & typeof globalThis) | null,
  element: HTMLElement,
  resized: () => void,
): () => void {
  if (!view?.ResizeObserver) return () => undefined;
  const observer = new view.ResizeObserver(resized);
  observer.observe(element);
  return () => observer.disconnect();
}

/** Espera tras parar de mover o mapa antes de avisar (que a lista non pestanexe a cada paso). */
const VIEW_DELAY = 300;

/**
 * Avisa do rectángulo á vista cada vez que o mapa para de moverse, pero só despois de que a
 * persoa o tocase (arrastre, roda, teclado ou un botón do mapa, que van no compoñente): o encadre
 * automático ao abrir non conta. Agarda `VIEW_DELAY` tras o último movemento.
 */
export function watchView(
  element: HTMLElement,
  map: LeafletMap,
  moved: (bounds: MapBounds) => void,
  destroyRef: { onDestroy(fn: () => void): unknown },
): void {
  const host = element.closest<HTMLElement>('furar-map') ?? element;
  let touched = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const touch = () => (touched = true);
  const events = ['pointerdown', 'wheel', 'keydown', 'touchstart'] as const;
  for (const type of events) host.addEventListener(type, touch, { passive: true });
  const onMoveEnd = () => {
    if (!touched) return;
    clearTimeout(timer);
    timer = setTimeout(() => {
      const b = map.getBounds();
      moved({ south: b.getSouth(), west: b.getWest(), north: b.getNorth(), east: b.getEast() });
    }, VIEW_DELAY);
  };
  map.on('moveend', onMoveEnd);
  destroyRef.onDestroy(() => {
    clearTimeout(timer);
    for (const type of events) host.removeEventListener(type, touch);
  });
}
