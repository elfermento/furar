// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type * as LeafletModule from 'leaflet';
import type { LayerGroup, Map as LeafletMap, Marker } from 'leaflet';
import type { MapMarker, MapPoint } from './map-point';

/** Módulo de Leaflet (ESM), cargado á parte: non entra no paquete inicial (ADR-0021). */
export type Leaflet = typeof LeafletModule;

/** Nome do paquete de estilos de `angular.json` (`bundleName`, `inject: false`). */
export const LEAFLET_STYLESHEET = 'leaflet.css';
const LINK_ID = 'furar-leaflet-css';

export const MAX_ZOOM = 19;
export const MIN_ZOOM = 5;
/** A chincheta mide 44 px (área de toque) e apunta co pico, abaixo no medio. */
const PIN_SIZE = 44;

let loading: Promise<Leaflet> | undefined;

/**
 * Carga Leaflet e a súa folla unha soa vez por páxina. A folla vai nun `<link>` á mesma orixe
 * (`style-src 'self'`); devólvese para que o mapa recalcule o tamaño cando chegue.
 */
export async function loadLeaflet(document: Document): Promise<{ L: Leaflet; stylesheet: HTMLLinkElement }> {
  const stylesheet = ensureStylesheet(document);
  loading ??= import('leaflet/dist/leaflet-src.esm.js');
  return { L: await loading, stylesheet };
}

function ensureStylesheet(document: Document): HTMLLinkElement {
  const existing = document.getElementById(LINK_ID);
  if (existing instanceof HTMLLinkElement) {
    return existing;
  }
  const link = document.createElement('link');
  link.id = LINK_ID;
  link.rel = 'stylesheet';
  link.href = new URL(LEAFLET_STYLESHEET, document.baseURI).href;
  document.head.append(link);
  return link;
}

export interface MapSetup {
  readonly editable: boolean;
  readonly animate: boolean;
  readonly center: MapPoint;
  readonly zoom: number;
  readonly tileUrl: string;
  /** Arrástrase aínda que non sexa editable (mapa con varias marcas). */
  readonly pannable?: boolean;
}

/**
 * Mapa sen os controis de Leaflet (zoom e atribución van no modelo de furar-map) nin o seu
 * teclado (o mapa non é parada de tabulador; a chincheta si, se é editable) e sen roda do rato,
 * que atraparía o desprazamento da páxina. O de só lectura non se arrastra e fai zoom no centro.
 */
export function createMap(L: Leaflet, element: HTMLElement, setup: MapSetup): LeafletMap {
  const { editable, animate } = setup;
  const map = L.map(element, {
    center: [setup.center.latitude, setup.center.longitude],
    zoom: setup.zoom,
    minZoom: MIN_ZOOM,
    maxZoom: MAX_ZOOM,
    zoomControl: false,
    attributionControl: false,
    keyboard: false,
    boxZoom: false,
    scrollWheelZoom: false,
    dragging: editable || !!setup.pannable,
    touchZoom: editable ? true : 'center',
    doubleClickZoom: editable ? true : 'center',
    zoomAnimation: animate,
    fadeAnimation: animate,
    markerZoomAnimation: animate,
  });
  L.tileLayer(setup.tileUrl, { minZoom: MIN_ZOOM, maxZoom: MAX_ZOOM }).addTo(map);
  return map;
}

/**
 * Chincheta propia (CSS, sen as imaxes de Leaflet); editable: enfocable e arrastrable. Con `title`
 * (marca dun mapa con varias), pícase co rato e o nome sae ao pasar; non é parada de tabulador:
 * a alternativa é a lista.
 */
export function createPin(L: Leaflet, document: Document, point: MapPoint, editable: boolean, title = ''): Marker {
  const pin = document.createElement('span');
  pin.className = 'f-map__pin';
  const icon = L.divIcon({
    className: 'f-map__marker',
    html: pin,
    iconSize: [PIN_SIZE, PIN_SIZE],
    iconAnchor: [PIN_SIZE / 2, PIN_SIZE],
  });
  return L.marker([point.latitude, point.longitude], {
    icon,
    keyboard: editable,
    interactive: editable || !!title,
    title,
    draggable: editable,
    autoPanOnFocus: true,
  });
}

/**
 * Pon unha chincheta por marca no grupo (quitando as de antes), cada unha co seu nome e o clic, e
 * encadra todas (unha soa, co zoom dado).
 */
export function placeMarkers(
  L: Leaflet,
  document: Document,
  map: LeafletMap,
  group: LayerGroup,
  markers: readonly MapMarker[],
  zoom: number,
  select: (id: string) => void,
): void {
  group.clearLayers();
  for (const item of markers) {
    const pin = createPin(L, document, item.point, false, item.label);
    pin.on('click', () => select(item.id));
    group.addLayer(pin);
  }
  const points = markers.map((m): [number, number] => [m.point.latitude, m.point.longitude]);
  if (points.length === 1) {
    map.setView(points[0], zoom, { animate: false });
  } else if (points.length > 1) {
    map.fitBounds(L.latLngBounds(points), { padding: [48, 48], maxZoom: zoom, animate: false });
  }
}
