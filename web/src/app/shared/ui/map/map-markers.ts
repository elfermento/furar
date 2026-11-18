// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { LayerGroup, Map as LeafletMap, Marker } from 'leaflet';
import type { Leaflet } from './leaflet-map';
import { MAX_ZOOM, createHere, frameMarkers, placeClusters, placeMarkers } from './leaflet-map';
import type { MapMarker, MapPoint } from './map-point';
import { NEAR_ZOOM } from './map-view';

/** O que lle fai falta ás marcas do que as usa (furar-map). */
export interface MarkerLayerSetup {
  /** Agrupar as que quedarían unha enriba doutra. */
  readonly cluster: boolean;
  /** Zoom cunha soa marca. */
  readonly zoom: number;
  /** Picouse unha marca soa: o seu id. */
  readonly select: (id: string) => void;
  /** Nome dun grupo ao pasar o rato («3 instalacións»). */
  readonly title: (count: number) => string;
  /** Nome do punto «Estás aquí». */
  readonly hereTitle: () => string;
  /** Con movemento (sen movemento reducido, `reducedMotion()`). */
  readonly animate: () => boolean;
  /** As chinchetas pintadas, polo id da súa marca (as dun grupo, a do grupo). */
  readonly drawn: (pins: Map<string, Marker>) => void;
}

/**
 * As marcas dun mapa con varias (`furar-map` con `markers`). Con `cluster`, as que quedarían unha
 * enriba doutra van nun círculo co número e reagrúpanse en cada cambio de zoom. Picar un grupo achega ata
 * separalo; se achegar non o separa (o mesmo punto, xa no máximo), ábrese no sitio, en círculo. Co
 * punto «Estás aquí» o mapa céntrase nel; sen el, encadra as marcas.
 */
export class MarkerLayer {
  private readonly group: LayerGroup;
  private markers: readonly MapMarker[] | null = null;
  private here: MapPoint | null = null;
  private hereMarker?: Marker;
  /** Grupos abertos no sitio (as súas marcas) ata o seguinte zoom. */
  private spread = new Set<string>();

  constructor(
    private readonly L: Leaflet,
    private readonly document: Document,
    private readonly map: LeafletMap,
    private readonly setup: MarkerLayerSetup,
  ) {
    this.group = L.layerGroup().addTo(map);
    if (!setup.cluster) return;
    map.on('zoomend', () => {
      this.spread = new Set();
      this.draw();
    });
  }

  /** Pon as marcas e o punto «Estás aquí» que cambiasen. */
  update(markers: readonly MapMarker[], here: MapPoint | null): void {
    if (markers !== this.markers) this.setMarkers(markers);
    if (here !== this.here) this.setHere(here);
  }

  /** Marcas novas: encadre (o zoom decide os grupos) e despois as marcas. */
  private setMarkers(markers: readonly MapMarker[]): void {
    this.markers = markers;
    if (!this.setup.cluster) {
      const { L, document, map, group, setup } = this;
      setup.drawn(placeMarkers(L, document, map, group, markers, setup.zoom, setup.select));
      return;
    }
    this.spread = new Set();
    this.frame(false);
    this.draw();
  }

  /** «Estás aquí» (coas agrupadas): o punto e o mapa nel; ao quitalo, volve encadrar as marcas. */
  private setHere(here: MapPoint | null): void {
    if (!this.setup.cluster) return;
    const had = this.here !== null;
    this.here = here;
    this.hereMarker?.remove();
    this.hereMarker = here
      ? createHere(this.L, this.document, here, this.setup.hereTitle()).addTo(this.map)
      : undefined;
    if (here || had) this.frame(this.setup.animate());
  }

  private frame(animate: boolean): void {
    const here = this.here;
    if (here) {
      this.map.setView([here.latitude, here.longitude], NEAR_ZOOM, { animate });
    } else {
      frameMarkers(this.L, this.map, this.markers ?? [], this.setup.zoom);
    }
  }

  private draw(): void {
    const pins = placeClusters(this.L, this.document, this.map, this.group, this.markers ?? [], {
      select: this.setup.select,
      open: (ids) => this.open(ids),
      title: this.setup.title,
      spread: this.spread,
    });
    this.setup.drawn(pins);
  }

  /** Picouse un grupo: achégase ata que as súas marcas se vexan ou, se non se separan, ábrese no sitio. */
  private open(ids: readonly string[]): void {
    const points = (this.markers ?? [])
      .filter((m) => ids.includes(m.id))
      .map((m): [number, number] => [m.point.latitude, m.point.longitude]);
    const bounds = this.L.latLngBounds(points);
    const same = bounds.getNorthEast().equals(bounds.getSouthWest());
    const target = Math.min(this.map.getBoundsZoom(bounds, false, this.L.point(96, 96)), MAX_ZOOM);
    if (same || target <= this.map.getZoom()) {
      this.spread = new Set([...this.spread, ...ids]);
      this.draw();
      return;
    }
    this.map.fitBounds(bounds, { padding: [48, 48], maxZoom: MAX_ZOOM, animate: this.setup.animate() });
  }
}
