// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type * as LeafletModule from 'leaflet';
import type { LatLng, LayerGroup, Map as LeafletMap, Marker } from 'leaflet';
import { clusterMarkers, spreadOffsets } from './map-cluster';
import type { MapMarker, MapPoint } from './map-point';
import { arrowOffset } from './map-point';

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
 * que atraparía o desprazamento da páxina. O dun só punto de lectura non se arrastra e fai zoom no
 * centro (a chincheta non se perde). O que se arrastra (editable ou con varias marcas) achega onde
 * se pellizca ou se toca dúas veces: no centro, o que se quere ver sairía do mapa.
 */
export function createMap(L: Leaflet, element: HTMLElement, setup: MapSetup): LeafletMap {
  const { editable, animate } = setup;
  const free = editable || !!setup.pannable;
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
    dragging: free,
    touchZoom: free ? true : 'center',
    doubleClickZoom: free ? true : 'center',
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
 * encadra todas (unha soa, co zoom dado). Devolve as chinchetas polo id da súa marca.
 */
export function placeMarkers(
  L: Leaflet,
  document: Document,
  map: LeafletMap,
  group: LayerGroup,
  markers: readonly MapMarker[],
  zoom: number,
  select: (id: string) => void,
): Map<string, Marker> {
  const pins = drawPins(L, document, group, markers, select);
  frameMarkers(L, map, markers, zoom);
  return pins;
}

/** Unha chincheta por marca no grupo (quitando as de antes), co seu nome e o clic. */
function drawPins(
  L: Leaflet,
  document: Document,
  group: LayerGroup,
  markers: readonly MapMarker[],
  select: (id: string) => void,
): Map<string, Marker> {
  group.clearLayers();
  const pins = new Map<string, Marker>();
  for (const item of markers) {
    const pin = createPin(L, document, item.point, false, item.label);
    pin.on('click', () => select(item.id));
    group.addLayer(pin);
    pins.set(item.id, pin);
  }
  return pins;
}

/** Encadra as marcas (unha soa, co zoom dado). */
export function frameMarkers(L: Leaflet, map: LeafletMap, markers: readonly MapMarker[], zoom: number): void {
  const points = markers.map((m): [number, number] => [m.point.latitude, m.point.longitude]);
  if (points.length === 1) {
    map.setView(points[0], zoom, { animate: false });
  } else if (points.length > 1) {
    map.fitBounds(L.latLngBounds(points), { padding: [48, 48], maxZoom: zoom, animate: false });
  }
}

/** O que fai falta para pintar as marcas agrupadas. */
export interface ClusterSetup {
  /** Picouse unha marca soa: o seu id. */
  readonly select: (id: string) => void;
  /** Picouse un grupo: as marcas que o forman. */
  readonly open: (ids: readonly string[]) => void;
  /** Nome dun grupo ao pasar o rato («3 instalacións»). */
  readonly title: (count: number) => string;
  /** Marcas dun grupo que xa se abriu no sitio: van en círculo arredor do seu centro. */
  readonly spread: ReadonlySet<string>;
}

/**
 * Como {@link drawPins}, pero as marcas que quedarían unha enriba doutra no zoom actual van nun
 * grupo co número (`clusterMarkers`). Non encadra: chámase de novo en cada cambio de zoom. Devolve
 * a chincheta de cada id (a do seu grupo, se vai nun).
 */
export function placeClusters(
  L: Leaflet,
  document: Document,
  map: LeafletMap,
  group: LayerGroup,
  markers: readonly MapMarker[],
  setup: ClusterSetup,
): Map<string, Marker> {
  const zoom = map.getZoom();
  const byId = new Map(markers.map((m) => [m.id, m]));
  const spread = markers.filter((m) => setup.spread.has(m.id));
  const loose = markers.filter((m) => !setup.spread.has(m.id));
  const screen = loose.map((m) => {
    const at = map.project([m.point.latitude, m.point.longitude], zoom);
    return { id: m.id, x: at.x, y: at.y };
  });
  group.clearLayers();
  const pins = new Map<string, Marker>();
  for (const cluster of clusterMarkers(screen)) {
    const single = cluster.ids.length === 1 ? byId.get(cluster.ids[0]) : undefined;
    if (single) {
      const item = single;
      const pin = createPin(L, document, item.point, false, item.label);
      pin.on('click', () => setup.select(item.id));
      group.addLayer(pin);
      pins.set(item.id, pin);
      continue;
    }
    const at = map.unproject([cluster.x, cluster.y], zoom);
    const bubble = createCluster(L, document, [at.lat, at.lng], cluster.ids.length, setup.title(cluster.ids.length));
    bubble.on('click', () => setup.open(cluster.ids));
    group.addLayer(bubble);
    for (const id of cluster.ids) pins.set(id, bubble);
  }
  if (spread.length) {
    const center = spread
      .map((m) => map.project([m.point.latitude, m.point.longitude], zoom))
      .reduce((sum, p) => sum.add(p), L.point(0, 0))
      .divideBy(spread.length);
    spreadOffsets(spread.length).forEach(([dx, dy], i) => {
      const item = spread[i];
      const at = map.unproject(center.add([dx, dy]), zoom);
      const pin = createPin(L, document, { latitude: at.lat, longitude: at.lng }, false, item.label);
      pin.on('click', () => setup.select(item.id));
      group.addLayer(pin);
      pins.set(item.id, pin);
    });
  }
  return pins;
}

/** Grupo de marcas: círculo co número, que se pica co rato (non é parada de tabulador). */
function createCluster(L: Leaflet, document: Document, at: [number, number], count: number, title: string): Marker {
  const bubble = document.createElement('span');
  bubble.className = 'f-map__cluster';
  bubble.textContent = String(count);
  const icon = L.divIcon({
    className: 'f-map__marker f-map__marker--cluster',
    html: bubble,
    iconSize: [PIN_SIZE, PIN_SIZE],
    iconAnchor: [PIN_SIZE / 2, PIN_SIZE / 2],
  });
  return L.marker(at, { icon, keyboard: false, interactive: true, title });
}

/** Punto «Estás aquí» (Preto de min): só se ve, non se pica. */
export function createHere(L: Leaflet, document: Document, point: MapPoint, title: string): Marker {
  const dot = document.createElement('span');
  dot.className = 'f-map__here-dot';
  const icon = L.divIcon({ className: 'f-map__here', html: dot, iconSize: [20, 20], iconAnchor: [10, 10] });
  return L.marker([point.latitude, point.longitude], { icon, keyboard: false, interactive: false, title });
}

/** Por riba das demais chinchetas, estean onde estean (Leaflet ordénaas pola latitude). */
const ACTIVE_Z_OFFSET = 1000;

/**
 * Marca como activa (`data-active`, por riba das demais) a chincheta con ese id (ou o grupo onde
 * vai) e ningunha outra.
 */
export function markActive(pins: ReadonlyMap<string, Marker>, id: string | null): void {
  const active = id === null ? undefined : pins.get(id);
  for (const pin of new Set(pins.values())) {
    pin.getElement()?.toggleAttribute('data-active', pin === active);
    pin.setZIndexOffset(pin === active ? ACTIVE_Z_OFFSET : 0);
  }
}

/** Onde vai a chincheta editable cunha frecha (10 px; con Maiús, 50); `null` se a tecla non a move. */
export function nudgedPoint(map: LeafletMap, marker: Marker, event: KeyboardEvent): LatLng | null {
  const offset = arrowOffset(event);
  if (!offset) return null;
  const zoom = map.getZoom();
  return map.unproject(map.project(marker.getLatLng(), zoom).add([offset[0], offset[1]]), zoom);
}
