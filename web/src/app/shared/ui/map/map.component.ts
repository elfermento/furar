// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterRenderEffect,
  booleanAttribute,
  computed,
  effect,
  forwardRef,
  inject,
  input,
  linkedSignal,
  numberAttribute,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { LatLng, LayerGroup, LeafletMouseEvent, Map as LeafletMap, Marker } from 'leaflet';
import { DeploymentSettings } from '../../../core/deployment/deployment-settings';
import { LanguageService } from '../../../core/i18n/language.service';
import { FieldComponent } from '../field.component';
import { IconComponent } from '../icon/icon.component';
import { InputDirective } from '../input/input.directive';
import type { Leaflet } from './leaflet-map';
import { MAX_ZOOM, MIN_ZOOM, createMap, createPin, loadLeaflet, placeMarkers } from './leaflet-map';
import type { MapAxis, MapMarker, MapPoint } from './map-point';
import { arrowOffset, formatCoordinate, isMapPoint, parseCoordinate, roundPoint } from './map-point';

let nextId = 0;

/** Sen punto: Galicia enteira. */
const GALICIA: MapPoint = { latitude: 42.8, longitude: -8 };

/**
 * Mapa con Leaflet 1.9.4 (plan do dominio 1, D1-07; ADR-0023). As teselas pídense á mesma orixe
 * (`/tiles/{z}/{x}/{y}.png`: Caddy pídeas ao servidor do despregue, por defecto o Mapa base do
 * IGN) e Leaflet e a súa folla cárganse ao abrir o primeiro mapa, fóra do paquete inicial.
 *
 * Sen mapa no despregue (`FURAR_MAP=off`) non se carga Leaflet: o de só lectura e o de varias
 * marcas non pintan nada e o editable queda nos campos de latitude e lonxitude.
 *
 * - **Só lectura** (por defecto, co punto en `point` ou nun formulario): non é parada de
 *   tabulador nin se arrastra; o texto do enderezo, que pon quen o usa, é a alternativa.
 * - **Editable** (`editable`): ControlValueAccessor co `MapPoint` ou `null`. A chincheta
 *   arrástrase, pícase no mapa para movela, móvese coas frechas cando ten o foco (Maiús: máis
 *   lonxe) e hai campos de latitude e lonxitude (WCAG 2.5.7: sen arrastrar tamén se pode).
 *
 * - **Varias marcas** (`markers`, listado): unha chincheta por elemento, que se pica co rato
 *   (`markerSelected` co id) e mostra o nome ao pasar; o mapa arrástrase e encadra todas. Non é
 *   parada de tabulador: quen o usa ofrece a lista como alternativa.
 *
 * O recoñecemento do despregue (`FURAR_MAP_ATTRIBUTION`, coa súa ligazón) sempre á vista; botóns
 * de zoom de 44 px con tokens.
 */
@Component({
  selector: 'furar-map',
  imports: [TranslocoPipe, FieldComponent, IconComponent, InputDirective],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MapComponent), multi: true }],
  host: { class: 'f-map', '[class.f-map--editable]': 'editable()' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './map.component.html',
})
export class MapComponent implements ControlValueAccessor {
  private readonly document = inject(DOCUMENT);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  protected readonly deployment = inject(DeploymentSettings);
  private readonly canvas = viewChild<ElementRef<HTMLElement>>('canvas');

  /** Nome do mapa editable («Punto da instalación»); no de só lectura non se usa. */
  readonly label = input('');
  /** Chincheta arrastrable e campos de coordenadas. Fíxase ao crear o mapa. */
  readonly editable = input(false, { transform: booleanAttribute });
  /** Punto sen formulario (só lectura). Cun formulario manda o valor do control. */
  readonly point = input<MapPoint | null>(null);
  /** Zoom inicial co punto (sen punto, Galicia enteira). */
  readonly zoom = input(16, { transform: numberAttribute });
  /** Teselas pola mesma orixe (ADR-0023); os tests e o catálogo interceptan esta ruta. */
  readonly tileUrl = input('/tiles/{z}/{x}/{y}.png');
  /** Varias marcas (listado); fíxase ao crear o mapa se se arrastra. */
  readonly markers = input<readonly MapMarker[] | null>(null);
  /** Picouse nunha das `markers`: o seu id. */
  readonly markerSelected = output<string>();
  /** Prefixo dos ids dos campos e da axuda. */
  readonly idPrefix = input(`furar-map-${nextId++}`);

  /** Mapa e recoñecemento do despregue (null mentres carga ou sen mapa). */
  protected readonly settings = computed(() => (this.deployment.mapEnabled() ? this.deployment.map() : null));
  protected readonly axes: readonly MapAxis[] = ['latitude', 'longitude'];
  protected readonly value = linkedSignal<MapPoint | null>(() => this.point());
  protected readonly isDisabled = signal(false);
  protected readonly zoomLevel = signal<number | null>(null);
  protected readonly announcement = signal('');
  protected readonly errors = signal<Partial<Record<MapAxis, string>>>({});
  private readonly lang = toSignal(this.transloco.langChanges$);
  private readonly ready = signal(false);

  protected readonly canZoomIn = computed(() => (this.zoomLevel() ?? MAX_ZOOM) < MAX_ZOOM);
  protected readonly canZoomOut = computed(() => (this.zoomLevel() ?? MIN_ZOOM) > MIN_ZOOM);
  protected readonly coordinates = computed(() => {
    const point = this.value();
    const lang = this.language.lang();
    return {
      latitude: point ? formatCoordinate(point.latitude, lang) : '',
      longitude: point ? formatCoordinate(point.longitude, lang) : '',
    };
  });

  private L?: Leaflet;
  private map?: LeafletMap;
  private marker?: Marker;
  private markerGroup?: LayerGroup;
  /** Coordenada escrita mentres aínda non hai punto (falta a outra). */
  private pending: Partial<MapPoint> = {};
  private onChange: (value: MapPoint | null) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  constructor() {
    const destroyRef = inject(DestroyRef);
    // Leaflet cárgase unha vez, cando aparece o lenzo (só se o despregue ten mapa).
    const start = afterRenderEffect(() => {
      const canvas = this.canvas();
      if (canvas) {
        start.destroy();
        void this.create(canvas.nativeElement, destroyRef);
      }
    });
    effect(() => this.syncMarker(this.value(), this.ready()));
    effect(() => this.syncMarkers(this.markers(), this.ready()));
    effect(() => this.describeMarker(this.value(), this.isDisabled(), this.ready(), this.lang()));
  }

  writeValue(value: unknown): void {
    this.value.set(isMapPoint(value) ? roundPoint(value.latitude, value.longitude) : null);
    this.pending = {};
    this.errors.set({});
  }

  registerOnChange(fn: (value: MapPoint | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.isDisabled.set(disabled);
  }

  protected zoomBy(delta: 1 | -1): void {
    this.map?.setZoom((this.map.getZoom() ?? MIN_ZOOM) + delta, { animate: !this.reducedMotion() });
  }

  /** Campo de latitude ou lonxitude: aplica ao saír (evento `change`) se o número é válido. */
  protected onCoordinate(axis: MapAxis, event: Event): void {
    const parsed = parseCoordinate((event.target as HTMLInputElement).value, axis);
    if (parsed === null) {
      this.errors.update((e) => ({ ...e, [axis]: `ui.map.invalid.${axis}` }));
      return;
    }
    this.errors.update((e) => ({ ...e, [axis]: undefined }));
    const current = this.value();
    const next = { ...this.pending, ...current, [axis]: parsed };
    if (next.latitude === undefined || next.longitude === undefined) {
      this.pending = next;
      return;
    }
    this.place(next.latitude, next.longitude, false);
    this.map?.panInside([next.latitude, next.longitude], { animate: false });
  }

  private async create(element: HTMLElement, destroyRef: DestroyRef): Promise<void> {
    const { L, stylesheet } = await loadLeaflet(this.document);
    if (destroyRef.destroyed) {
      return;
    }
    const editable = this.editable();
    const point = this.value();
    const map = createMap(L, element, {
      editable,
      animate: !this.reducedMotion(),
      center: point ?? GALICIA,
      zoom: point ? this.zoom() : 8,
      tileUrl: this.tileUrl(),
      pannable: this.markers() !== null,
    });
    map.on('zoomend', () => this.zoomLevel.set(map.getZoom()));
    if (editable) {
      map.on('click', (event: LeafletMouseEvent) => this.onMapClick(event.latlng));
    }
    const resize = () => map.invalidateSize();
    stylesheet.addEventListener('load', resize);
    destroyRef.onDestroy(() => {
      stylesheet.removeEventListener('load', resize);
      map.remove();
    });
    this.L = L;
    this.map = map;
    this.zoomLevel.set(map.getZoom());
    this.ready.set(true);
  }

  private syncMarker(point: MapPoint | null, ready: boolean): void {
    if (!ready || !this.map || !this.L) {
      return;
    }
    if (!point) {
      this.marker?.remove();
      return;
    }
    const marker = (this.marker ??= this.createMarker(this.L, point));
    marker.setLatLng([point.latitude, point.longitude]);
    if (!this.map.hasLayer(marker)) {
      marker.addTo(this.map);
    }
    if (!this.editable()) {
      this.map.setView([point.latitude, point.longitude], this.map.getZoom(), { animate: false });
    }
  }

  private syncMarkers(markers: readonly MapMarker[] | null, ready: boolean): void {
    if (!ready || !this.map || !this.L || markers === null) return;
    this.markerGroup ??= this.L.layerGroup().addTo(this.map);
    placeMarkers(this.L, this.document, this.map, this.markerGroup, markers, this.zoom(), (id) =>
      this.markerSelected.emit(id),
    );
  }

  private createMarker(L: Leaflet, point: MapPoint): Marker {
    const editable = this.editable();
    const marker = createPin(L, this.document, point, editable);
    if (editable) {
      marker.on('add', () => {
        marker.getElement()?.addEventListener('keydown', (event) => this.onMarkerKeydown(event));
        this.describeMarker(this.value(), this.isDisabled(), true, this.lang());
      });
      marker.on('dragend', () => {
        const at = marker.getLatLng();
        this.place(at.lat, at.lng, false);
        this.onTouched();
      });
    }
    return marker;
  }

  /** Nome accesible da chincheta (co punto) e, se o formulario a desactiva, fóra do tabulador. */
  private describeMarker(point: MapPoint | null, disabled: boolean, ready: boolean, lang: unknown): void {
    const element = this.marker?.getElement();
    if (!ready || !point || !element || !this.editable() || lang === undefined) {
      return;
    }
    const { latitude, longitude } = this.coordinates();
    element.setAttribute('aria-label', this.transloco.translate('ui.map.marker', { latitude, longitude }));
    element.setAttribute('aria-describedby', `${this.idPrefix()}-hint`);
    element.tabIndex = disabled ? -1 : 0;
    if (disabled) {
      this.marker?.dragging?.disable();
    } else {
      this.marker?.dragging?.enable();
    }
  }

  private onMarkerKeydown(event: KeyboardEvent): void {
    const offset = arrowOffset(event);
    if (!offset || !this.map || !this.marker || this.isDisabled()) {
      return;
    }
    event.preventDefault();
    const zoom = this.map.getZoom();
    const target = this.map.unproject(
      this.map.project(this.marker.getLatLng(), zoom).add([offset[0], offset[1]]),
      zoom,
    );
    this.place(target.lat, target.lng, true);
    this.map.panInside(target, { animate: false });
  }

  private onMapClick(at: LatLng): void {
    if (this.isDisabled()) {
      return;
    }
    this.place(at.lat, at.lng, false);
    this.onTouched();
  }

  private place(latitude: number, longitude: number, announce: boolean): void {
    const point = roundPoint(latitude, longitude);
    this.pending = {};
    this.errors.set({});
    this.value.set(point);
    this.onChange(point);
    if (announce) {
      const { latitude: lat, longitude: lon } = this.coordinates();
      this.announcement.set(this.transloco.translate('ui.map.moved', { latitude: lat, longitude: lon }));
    }
  }

  private reducedMotion(): boolean {
    return this.document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }
}
