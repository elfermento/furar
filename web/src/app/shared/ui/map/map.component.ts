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
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { LatLng, LeafletMouseEvent, Map as LeafletMap, Marker } from 'leaflet';
import { DeploymentSettings } from '../../../core/deployment/deployment-settings';
import { LanguageService } from '../../../core/i18n/language.service';
import { FieldComponent } from '../field.component';
import { IconComponent } from '../icon/icon.component';
import { InputDirective } from '../input/input.directive';
import type { Leaflet } from './leaflet-map';
import { MAX_ZOOM, MIN_ZOOM, createMap, createPin, loadLeaflet, markActive, nudgedPoint } from './leaflet-map';
import { MarkerLayer } from './map-markers';
import { reducedMotion } from '../motion/reduced-motion';
import { GALICIA, focusPoint, frameArea, watchSize, watchView } from './map-view';
import type { MapAxis, MapBounds, MapMarker, MapPoint } from './map-point';
import { formatCoordinate, isMapPoint, roundPoint, samePoint, typeCoordinate } from './map-point';

let nextId = 0;

/**
 * Mapa con Leaflet 1.9.4 (ADR-0023). As teselas pídense á mesma orixe
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
 *   Cando o punto chega de fóra (o formulario, p. ex. o buscador de enderezos), o mapa vai a el
 *   con zoom de rúa; ao movelo a man, non. Sen punto, encadra `area` se a hai.
 *
 * - **Varias marcas** (`markers`, listado): unha chincheta por elemento, que se pica co rato
 *   (`markerSelected` co id) e mostra o nome ao pasar; o mapa arrástrase e encadra todas. Non é
 *   parada de tabulador: quen o usa ofrece a lista como alternativa. `activeMarker` resalta unha
 *   (a da tarxeta sinalada ou escollida na lista) sen mover o mapa.
 *
 * - **Agrupadas** (`cluster`, con `markers`): as marcas que quedarían unha enriba doutra van nun
 *   círculo co número; picalo achega ata separalas ou, se xa non se separan (o mesmo punto, o
 *   zoom máximo), ábreas no sitio. Ao achegar ou afastar, reagrúpanse.
 * - **Estás aquí** (`here`, coas agrupadas): un punto coa posición de quen mira, e o mapa céntrase nel; sen el,
 *   volve encadrar as marcas. Os controis propios de quen o usa («Preto de min») proxéctanse con
 *   `[mapControl]` na esquina de arriba á esquerda.
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
  /**
   * A persoa moveu ou achegou o mapa (arrastre, roda, botóns, teclado, un grupo): o rectángulo á
   * vista, 300 ms despois de parar. O encadre automático ao abrir non conta.
   */
  readonly viewMoved = output<MapBounds>();
  /** A marca de `markers` que se resalta (a da tarxeta sinalada ou escollida na lista); sen ela, ningunha. */
  readonly activeMarker = input<string | null>(null);
  /** Sen punto, o mapa encadra estes puntos (as instalacións do concello) en vez de Galicia. */
  readonly area = input<readonly MapPoint[]>([]);
  /** Agrupa as `markers` que quedarían unha enriba doutra (fíxase ao crear o mapa). */
  readonly cluster = input(false, { transform: booleanAttribute });
  /** Onde está quen mira («Preto de min»): un punto e o mapa centrado nel (só con `cluster`). */
  readonly here = input<MapPoint | null>(null);
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
  private markerLayer?: MarkerLayer;
  /** As chinchetas de `markers`, polo seu id. */
  private readonly pins = signal<ReadonlyMap<string, Marker>>(new Map());
  /** Xa se encadrou a `area` (só unha vez: despois manda quen move o mapa). */
  private framed = false;
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
    effect(() => this.syncMarkers(this.markers(), this.here(), this.ready()));
    effect(() => markActive(this.pins(), this.activeMarker()));
    effect(() => this.frameArea(this.area(), this.ready()));
    effect(() => this.describeMarker(this.value(), this.isDisabled(), this.ready(), this.lang()));
  }

  writeValue(value: unknown): void {
    const point = isMapPoint(value) ? roundPoint(value.latitude, value.longitude) : null;
    const before = this.value();
    this.value.set(point);
    this.pending = {};
    this.errors.set({});
    if (point && this.map && this.editable() && !samePoint(before, point)) {
      focusPoint(this.map, point, !reducedMotion(this.document.defaultView));
    }
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
    this.map?.setZoom((this.map.getZoom() ?? MIN_ZOOM) + delta, { animate: !reducedMotion(this.document.defaultView) });
  }

  /** Campo de latitude ou lonxitude: aplica ao saír (evento `change`) se o número é válido. */
  protected onCoordinate(axis: MapAxis, event: Event): void {
    const typed = typeCoordinate(this.pending, this.value(), axis, (event.target as HTMLInputElement).value);
    this.errors.update((e) => ({ ...e, [axis]: typed.error ? `ui.map.invalid.${axis}` : undefined }));
    if (typed.point) {
      this.place(typed.point.latitude, typed.point.longitude, false);
      this.map?.panInside([typed.point.latitude, typed.point.longitude], { animate: false });
    } else if (!typed.error) {
      this.pending = typed.pending;
    }
  }

  private async create(element: HTMLElement, destroyRef: DestroyRef): Promise<void> {
    const { L, stylesheet } = await loadLeaflet(this.document);
    if (destroyRef.destroyed) return;
    const editable = this.editable();
    const point = this.value();
    const map = createMap(L, element, {
      editable,
      animate: !reducedMotion(this.document.defaultView),
      center: point ?? GALICIA,
      zoom: point ? this.zoom() : 8,
      tileUrl: this.tileUrl(),
      pannable: this.markers() !== null,
    });
    map.on('zoomend', () => this.zoomLevel.set(map.getZoom()));
    watchView(element, map, (bounds) => this.viewMoved.emit(bounds), destroyRef);
    if (editable) map.on('click', (event: LeafletMouseEvent) => this.onMapClick(event.latlng));
    const resize = () => map.invalidateSize();
    stylesheet.addEventListener('load', resize);
    const stopWatch = watchSize(this.document.defaultView, element, resize);
    destroyRef.onDestroy(() => {
      stylesheet.removeEventListener('load', resize);
      stopWatch();
      map.remove();
    });
    this.L = L;
    this.map = map;
    this.zoomLevel.set(map.getZoom());
    this.ready.set(true);
  }

  /** Sen punto, unha vez: encadra a área (despois manda quen move o mapa). */
  private frameArea(area: readonly MapPoint[], ready: boolean): void {
    if (!ready || !this.map || !this.L || this.framed || !area.length || untracked(this.value)) return;
    this.framed = true;
    frameArea(this.L, this.map, area, () => !untracked(this.value));
  }

  private syncMarker(point: MapPoint | null, ready: boolean): void {
    if (!ready || !this.map || !this.L) return;
    if (!point) {
      this.marker?.remove();
      return;
    }
    const marker = (this.marker ??= this.createMarker(this.L, point));
    marker.setLatLng([point.latitude, point.longitude]);
    if (!this.map.hasLayer(marker)) marker.addTo(this.map);
    if (!this.editable()) {
      this.map.setView([point.latitude, point.longitude], this.map.getZoom(), { animate: false });
    }
  }

  private syncMarkers(markers: readonly MapMarker[] | null, here: MapPoint | null, ready: boolean): void {
    if (ready && this.map && this.L && markers !== null) this.layer(this.L, this.map).update(markers, here);
  }

  /** As marcas (agrupadas ou non), creadas a primeira vez que fan falta. */
  private layer(L: Leaflet, map: LeafletMap): MarkerLayer {
    return (this.markerLayer ??= new MarkerLayer(L, this.document, map, {
      cluster: untracked(this.cluster),
      zoom: untracked(this.zoom),
      select: (id) => this.markerSelected.emit(id),
      title: (count) => this.transloco.translate('ui.map.cluster', { count }),
      hereTitle: () => this.transloco.translate('ui.map.here'),
      animate: () => !reducedMotion(this.document.defaultView),
      drawn: (pins) => this.pins.set(pins),
    }));
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
    if (!ready || !point || !element || !this.editable() || lang === undefined) return;
    const { latitude, longitude } = this.coordinates();
    element.setAttribute('aria-label', this.transloco.translate('ui.map.marker', { latitude, longitude }));
    element.setAttribute('aria-describedby', `${this.idPrefix()}-hint`);
    element.tabIndex = disabled ? -1 : 0;
    this.marker?.dragging?.[disabled ? 'disable' : 'enable']();
  }

  private onMarkerKeydown(event: KeyboardEvent): void {
    const target = this.map && this.marker && !this.isDisabled() ? nudgedPoint(this.map, this.marker, event) : null;
    if (!target) return;
    event.preventDefault();
    this.place(target.lat, target.lng, true);
    this.map?.panInside(target, { animate: false });
  }

  private onMapClick(at: LatLng): void {
    if (this.isDisabled()) return;
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
}
