// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import type { Map as LeafletMap } from 'leaflet';
import { expectNoAxeViolations } from '../../../../testing/axe';
import type { MapSettings } from '../../../core/deployment/deployment-settings';
import { IGN_MAP, NO_MAP, provideDeploymentSettings } from '../../../../testing/deployment';
import { provideTestI18n } from '../../../../testing/i18n';
import { LanguageService } from '../../../core/i18n/language.service';
import { LEAFLET_STYLESHEET } from './leaflet-map';
import { NEAR_ZOOM, STREET_ZOOM } from './map-view';
import type { MapPoint } from './map-point';
import { arrowOffset, formatCoordinate, parseCoordinate } from './map-point';
import { MapComponent } from './map.component';

const PINEIROS: MapPoint = { latitude: 43.5136, longitude: -8.1859 };

@Component({
  imports: [ReactiveFormsModule, MapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-map id="view" [point]="view()" />
    <furar-map id="edit" editable idPrefix="edit" [formControl]="point" [label]="'Punto'" />
  `,
})
class HostComponent {
  readonly view = signal<MapPoint | null>(PINEIROS);
  readonly point = new FormControl<MapPoint | null>(PINEIROS);
}

/** Mapa editable sen punto, coas instalacións do concello para encadrar. */
@Component({
  imports: [ReactiveFormsModule, MapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-map id="area" editable idPrefix="area" [formControl]="point" [area]="area" />`,
})
class AreaHostComponent {
  readonly point = new FormControl<MapPoint | null>(null);
  readonly area: readonly MapPoint[] = [PINEIROS, { latitude: 43.4834, longitude: -8.2333 }];
}

/** Mapa con varias marcas (un listado) e unha delas resaltada desde fóra. */
@Component({
  imports: [MapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-map [markers]="markers" [activeMarker]="active()" (markerSelected)="picked.set($event)" />`,
})
class MarkersHostComponent {
  readonly markers = [
    { id: 'a', point: PINEIROS, label: 'Pavillón' },
    { id: 'b', point: { latitude: 43.4834, longitude: -8.2333 }, label: 'Pista' },
  ];
  readonly active = signal<string | null>(null);
  readonly picked = signal<string | null>(null);
}

/** Mapa da portada común: marcas agrupadas, «Estás aquí» e un control proxectado. */
@Component({
  imports: [MapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-map
    cluster
    [markers]="markers"
    [here]="here()"
    [activeMarker]="active()"
    (markerSelected)="picked.set($event)"
    ><button mapControl type="button">Preto</button></furar-map
  >`,
})
class ClusterHostComponent {
  readonly markers = [
    { id: 'a', point: PINEIROS, label: 'Pavillón' },
    { id: 'b', point: PINEIROS, label: 'Pista' },
    { id: 'c', point: PINEIROS, label: 'Campo' },
    { id: 'd', point: { latitude: 43.2944, longitude: -8.3436 }, label: 'Cambre' },
  ];
  readonly here = signal<MapPoint | null>(null);
  readonly active = signal<string | null>(null);
  readonly picked = signal<string | null>(null);
}

describe('furar-map', () => {
  async function render(lang: 'gl' | 'es' = 'gl', map: MapSettings = IGN_MAP) {
    TestBed.configureTestingModule({ providers: [provideTestI18n(lang), provideDeploymentSettings(map)] });
    const fixture = TestBed.createComponent(HostComponent);
    await TestBed.inject(LanguageService).use(lang);
    if (map.enabled) {
      await settle(fixture);
    } else {
      await fixture.whenStable();
    }
    return fixture;
  }

  /** Leaflet cárgase con `import()`: agarda a que os dous mapas existan e se pinten (a suite en paralelo vai lenta). */
  async function settle(fixture: ComponentFixture<HostComponent>) {
    const ready = () =>
      (fixture.nativeElement as HTMLElement).querySelectorAll('.leaflet-container .f-map__marker').length >= 2;
    for (let i = 0; i < 300 && !ready(); i++) {
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    await fixture.whenStable();
  }

  const el = (fixture: ComponentFixture<HostComponent>) => fixture.nativeElement as HTMLElement;
  const marker = (fixture: ComponentFixture<HostComponent>, id: string) =>
    el(fixture).querySelector<HTMLElement>(`#${id} .f-map__marker`)!;
  const field = (fixture: ComponentFixture<HostComponent>, axis: string) =>
    el(fixture).querySelector<HTMLInputElement>(`#edit-${axis}`)!;

  async function press(fixture: ComponentFixture<HostComponent>, target: Element, key: string, shiftKey = false) {
    const event = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true });
    target.dispatchEvent(event);
    await fixture.whenStable();
    return event;
  }

  async function type(fixture: ComponentFixture<HostComponent>, input: HTMLInputElement, text: string) {
    input.value = text;
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  it('carga a folla de Leaflet unha vez, á mesma orixe, e pide as teselas a /tiles', async () => {
    const fixture = await render();
    const links = document.querySelectorAll<HTMLLinkElement>('link#furar-leaflet-css');
    expect(links.length).toBe(1);
    expect(new URL(links[0].href).pathname).toBe(`/${LEAFLET_STYLESHEET}`);
    for (const tile of el(fixture).querySelectorAll<HTMLImageElement>('img.leaflet-tile')) {
      expect(new URL(tile.src).pathname).toMatch(/^\/tiles\/\d+\/\d+\/\d+\.png$/);
    }
  });

  it('só lectura: o mapa non é parada de tabulador e a atribución está á vista', async () => {
    const fixture = await render();
    const view = el(fixture).querySelector<HTMLElement>('#view')!;
    expect(view.querySelector('.f-map__canvas')?.getAttribute('aria-hidden')).toBe('true');
    expect(marker(fixture, 'view').hasAttribute('tabindex')).toBe(false);
    for (const button of view.querySelectorAll('button')) {
      expect(button.getAttribute('tabindex')).toBe('-1');
    }
    const tabbable = [...view.querySelectorAll<HTMLElement>('a[href], button, input, [tabindex="0"]')].filter(
      (node) => node.getAttribute('tabindex') !== '-1',
    );
    expect(tabbable.map((node) => node.textContent?.trim())).toEqual(['CC BY 4.0 scne.es (abre noutra xanela)']);
    expect(tabbable[0].getAttribute('href')).toBe('https://www.scne.es');
    expect(view.querySelector('input')).toBeNull();
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('só lectura: segue o punto que lle pasan', async () => {
    const fixture = await render();
    fixture.componentInstance.view.set({ latitude: 43.4834, longitude: -8.2333 });
    await fixture.whenStable();
    expect(marker(fixture, 'view')).not.toBeNull();
    fixture.componentInstance.view.set(null);
    await fixture.whenStable();
    expect(el(fixture).querySelector('#view .f-map__marker')).toBeNull();
  });

  it('editable: a chincheta enfocable leva nome coas coordenadas e a axuda', async () => {
    const fixture = await render();
    const pin = marker(fixture, 'edit');
    expect(pin.tabIndex).toBe(0);
    expect(pin.getAttribute('role')).toBe('button');
    expect(pin.getAttribute('aria-label')).toBe('Chincheta: 43,513600, -8,185900');
    expect(pin.getAttribute('aria-describedby')).toBe('edit-hint');
    expect(el(fixture).querySelector('#edit-hint')?.textContent).toContain('coas frechas');
    expect(el(fixture).querySelector('#edit [role="group"]')?.getAttribute('aria-label')).toBe('Punto');
    expect(field(fixture, 'latitude').value).toBe('43,513600');
    expect(field(fixture, 'longitude').value).toBe('-8,185900');
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('editable: as frechas moven a chincheta, actualizan o control e anúncianse', async () => {
    const fixture = await render();
    const control = fixture.componentInstance.point;
    const pin = marker(fixture, 'edit');
    pin.focus();

    const right = await press(fixture, pin, 'ArrowRight');
    expect(right.defaultPrevented).toBe(true);
    expect(control.value!.longitude).toBeGreaterThan(PINEIROS.longitude);
    expect(control.value!.latitude).toBeCloseTo(PINEIROS.latitude, 5);

    const before = control.value!.latitude;
    await press(fixture, pin, 'ArrowUp');
    const small = control.value!.latitude - before;
    expect(small).toBeGreaterThan(0);
    await press(fixture, pin, 'ArrowDown', true);
    expect(control.value!.latitude).toBeLessThan(before);
    expect(before + small - control.value!.latitude).toBeCloseTo(small * 5, 5);
    await press(fixture, pin, 'ArrowLeft');

    expect(field(fixture, 'longitude').value).toBe(formatCoordinate(control.value!.longitude, 'gl'));
    expect(el(fixture).querySelector('#edit [role="status"]')?.textContent).toContain('Chincheta en 43,');
    expect(pin.getAttribute('aria-label')).toBe(
      `Chincheta: ${formatCoordinate(control.value!.latitude, 'gl')}, ${formatCoordinate(control.value!.longitude, 'gl')}`,
    );
    expect((await press(fixture, pin, 'Home')).defaultPrevented).toBe(false);
  });

  it('editable: picar no mapa move a chincheta', async () => {
    const fixture = await render();
    const control = fixture.componentInstance.point;
    const canvas = el(fixture).querySelector<HTMLElement>('#edit .leaflet-container')!;
    canvas.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 80, clientY: 40 }));
    await fixture.whenStable();
    expect(control.value).not.toEqual(PINEIROS);
    expect(control.touched).toBe(true);
  });

  /** O mapa de Leaflet dun furar-map (privado no compoñente). */
  const leaflet = (fixture: ComponentFixture<unknown>, id: string) =>
    (fixture.debugElement.query(By.css(`#${id}`)).injector.get(MapComponent) as unknown as { map: LeafletMap }).map;

  it('editable: un punto que chega de fóra (o buscador) leva o mapa a el con zoom de rúa; movelo a man, non', async () => {
    const fixture = await render();
    const map = leaflet(fixture, 'edit');
    map.setZoom(12, { animate: false });
    const found = { latitude: 43.4834, longitude: -8.2333 };
    fixture.componentInstance.point.setValue(found);
    await fixture.whenStable();
    expect(map.getZoom()).toBe(STREET_ZOOM);
    expect(map.getCenter().lat).toBeCloseTo(found.latitude, 4);
    expect(map.getCenter().lng).toBeCloseTo(found.longitude, 4);

    map.setZoom(14, { animate: false });
    const canvas = el(fixture).querySelector<HTMLElement>('#edit .leaflet-container')!;
    canvas.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 80, clientY: 40 }));
    await fixture.whenStable();
    expect(fixture.componentInstance.point.value).not.toEqual(found);
    expect(map.getZoom()).toBe(14);
  });

  it('editable sen punto: encadra a área (as instalacións do concello) en vez de Galicia', async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n('gl'), provideDeploymentSettings(IGN_MAP)] });
    const fixture = TestBed.createComponent(AreaHostComponent);
    await TestBed.inject(LanguageService).use('gl');
    const root = fixture.nativeElement as HTMLElement;
    for (let i = 0; i < 300 && !root.querySelector('.leaflet-container'); i++) {
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    await fixture.whenStable();
    const map = leaflet(fixture, 'area');
    // O DOM dos tests non mide: o mapa nace sen tamaño e encadra cando o ten.
    expect(map.getZoom()).toBe(8);
    const { point } = await import('leaflet/dist/leaflet-src.esm.js');
    vi.spyOn(map, 'getSize').mockReturnValue(point(480, 320));
    map.fire('resize');
    // Centrado entre as dúas e tan preto como caben (sen pasar do zoom do concello).
    expect(map.getCenter().lat).toBeCloseTo((PINEIROS.latitude + 43.4834) / 2, 3);
    expect(map.getCenter().lng).toBeCloseTo((PINEIROS.longitude - 8.2333) / 2, 3);
    expect(map.getZoom()).toBeGreaterThan(8);
    expect(map.getZoom()).toBeLessThanOrEqual(15);
  });

  it('editable: os campos de coordenadas moven a chincheta e rexeitan o que non é un número', async () => {
    const fixture = await render();
    const control = fixture.componentInstance.point;
    await type(fixture, field(fixture, 'latitude'), ' 43,5 ');
    expect(control.value).toEqual({ latitude: 43.5, longitude: PINEIROS.longitude });
    await type(fixture, field(fixture, 'longitude'), '−8.2');
    expect(control.value).toEqual({ latitude: 43.5, longitude: -8.2 });

    await type(fixture, field(fixture, 'latitude'), '95');
    expect(control.value!.latitude).toBe(43.5);
    expect(field(fixture, 'latitude').getAttribute('aria-invalid')).toBe('true');
    expect(field(fixture, 'latitude').getAttribute('aria-describedby')).toBe('edit-latitude-error');
    expect(el(fixture).querySelector('#edit-latitude-error')?.textContent).toContain('entre −90 e 90');
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('editable sen punto: pídense as dúas coordenadas antes de pór a chincheta', async () => {
    const fixture = await render();
    const control = fixture.componentInstance.point;
    control.setValue(null);
    await fixture.whenStable();
    expect(el(fixture).querySelector('#edit .f-map__marker')).toBeNull();
    expect(el(fixture).querySelector('#edit-hint')?.textContent).toContain('Pica no mapa');
    await type(fixture, field(fixture, 'latitude'), '43,49');
    expect(control.value).toBeNull();
    await type(fixture, field(fixture, 'longitude'), '-8,19');
    expect(control.value).toEqual({ latitude: 43.49, longitude: -8.19 });
    expect(marker(fixture, 'edit')).not.toBeNull();
  });

  it('desactivado: sen tabulador nin movementos', async () => {
    const fixture = await render();
    const control = fixture.componentInstance.point;
    control.disable();
    await fixture.whenStable();
    const pin = marker(fixture, 'edit');
    expect(pin.tabIndex).toBe(-1);
    await press(fixture, pin, 'ArrowRight');
    expect(control.value).toEqual(PINEIROS);
    expect(field(fixture, 'latitude').disabled).toBe(true);
  });

  it('varias marcas: a activa resáltase (`data-active`, por riba das demais) sen recolocar o mapa; picar avisa do id', async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n('gl'), provideDeploymentSettings(IGN_MAP)] });
    const fixture = TestBed.createComponent(MarkersHostComponent);
    const host = fixture.nativeElement as HTMLElement;
    const pins = () => [...host.querySelectorAll<HTMLElement>('.leaflet-container .f-map__marker')];
    for (let i = 0; i < 300 && pins().length < 2; i++) {
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    const pin = (title: string) => pins().find((p) => p.title === title)!;
    expect(pins().some((p) => p.hasAttribute('data-active'))).toBe(false);
    const map = (
      fixture.debugElement.query(By.directive(MapComponent)).componentInstance as unknown as { map: LeafletMap }
    ).map;
    const before = { center: map.getCenter(), zoom: map.getZoom() };
    // Co dedo: arrástrase e achega onde se pellizca ou se toca dúas veces (no centro, a marca sairía do mapa).
    expect([map.dragging.enabled(), map.options.touchZoom, map.options.doubleClickZoom]).toEqual([true, true, true]);

    fixture.componentInstance.active.set('b');
    await fixture.whenStable();
    expect(pin('Pista').hasAttribute('data-active')).toBe(true);
    expect(pin('Pavillón').hasAttribute('data-active')).toBe(false);
    expect(Number(pin('Pista').style.zIndex)).toBeGreaterThan(Number(pin('Pavillón').style.zIndex));
    expect(map.getZoom()).toBe(before.zoom);
    expect(map.getCenter().equals(before.center)).toBe(true);

    fixture.componentInstance.active.set(null);
    await fixture.whenStable();
    expect(pins().some((p) => p.hasAttribute('data-active'))).toBe(false);

    pin('Pavillón').click();
    await fixture.whenStable();
    expect(fixture.componentInstance.picked()).toBe('a');
  });

  it('agrupadas: as do mesmo sitio, nun círculo co número que se abre ao picalo; «Estás aquí» centra o mapa', async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n('gl'), provideDeploymentSettings(IGN_MAP)] });
    const fixture = TestBed.createComponent(ClusterHostComponent);
    const host = fixture.nativeElement as HTMLElement;
    const settle = async () => {
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve, 10));
    };
    const shown = () => [...host.querySelectorAll<HTMLElement>('.leaflet-container .f-map__marker')];
    for (let i = 0; i < 300 && shown().length < 2; i++) await settle();
    // O control proxectado vai dentro do mapa.
    expect(host.querySelector('.f-map__frame .f-map__controls button')?.textContent).toBe('Preto');

    const bubble = () => host.querySelector<HTMLElement>('.f-map__marker--cluster');
    expect(shown()).toHaveLength(2);
    expect(bubble()?.textContent?.trim()).toBe('3');
    expect(bubble()?.title).toBe('3 instalacións');
    expect(shown().find((m) => !m.matches('.f-map__marker--cluster'))?.title).toBe('Cambre');
    // Unha marca do grupo resaltada resalta o grupo.
    fixture.componentInstance.active.set('b');
    await settle();
    expect(bubble()?.hasAttribute('data-active')).toBe(true);

    // O mesmo punto non se separa achegando: ábrese no sitio, cada unha coa súa chincheta.
    bubble()!.click();
    await settle();
    expect(bubble()).toBeNull();
    expect(
      shown()
        .map((m) => m.title)
        .sort(),
    ).toEqual(['Cambre', 'Campo', 'Pavillón', 'Pista']);
    shown()
      .find((m) => m.title === 'Pista')!
      .click();
    expect(fixture.componentInstance.picked()).toBe('b');

    const map = (
      fixture.debugElement.query(By.directive(MapComponent)).componentInstance as unknown as { map: LeafletMap }
    ).map;
    fixture.componentInstance.here.set({ latitude: 43.3, longitude: -8.34 });
    await settle();
    const here = host.querySelector<HTMLElement>('.leaflet-container .f-map__here');
    expect(here?.title).toBe('Estás aquí');
    expect(map.getCenter().lat).toBeCloseTo(43.3, 5);
    expect(map.getZoom()).toBe(NEAR_ZOOM);
    fixture.componentInstance.here.set(null);
    await settle();
    expect(host.querySelector('.f-map__here')).toBeNull();
  });

  it('o zoom ten botóns con nome e respecta os límites', async () => {
    const fixture = await render();
    const [zoomIn, zoomOut] = el(fixture).querySelectorAll<HTMLButtonElement>('#edit .f-map__zoom-button');
    expect(zoomIn.getAttribute('aria-label')).toBe('Achegar');
    expect(zoomOut.getAttribute('aria-label')).toBe('Afastar');
    for (let i = 0; i < 4; i++) {
      zoomIn.click();
      await fixture.whenStable();
    }
    expect(zoomIn.disabled).toBe(true);
    expect(zoomOut.disabled).toBe(false);
  });

  it('o recoñecemento é o do despregue; sen ligazón, só o texto', async () => {
    const fixture = await render('gl', { enabled: true, attribution: '© Teselas do concello' });
    const attribution = el(fixture).querySelector('#view .f-map__attribution')!;
    expect(attribution.textContent?.trim()).toBe('© Teselas do concello');
    expect(attribution.querySelector('a')).toBeNull();
  });

  it('sen mapa no despregue: nin Leaflet nin teselas; o editable queda nas coordenadas', async () => {
    const fixture = await render('gl', NO_MAP);
    expect(el(fixture).querySelector('.f-map__frame, .f-map__canvas, .leaflet-container')).toBeNull();
    expect(el(fixture).querySelector('img.leaflet-tile')).toBeNull();
    expect(el(fixture).querySelector('#view')?.textContent?.trim()).toBe('');
    const group = el(fixture).querySelector<HTMLElement>('#edit [role="group"]')!;
    expect(group.getAttribute('aria-label')).toBe('Punto');
    expect(el(fixture).querySelector('#edit-hint')?.textContent?.trim()).toBe(
      'Énchense ao buscar o enderezo; tamén se poden escribir.',
    );
    expect(field(fixture, 'latitude').value).toBe(formatCoordinate(PINEIROS.latitude, 'gl'));
    await type(fixture, field(fixture, 'latitude'), '43,5');
    expect(fixture.componentInstance.point.value).toEqual({ latitude: 43.5, longitude: PINEIROS.longitude });
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('en castelán, textos e coordenadas co idioma activo', async () => {
    const fixture = await render('es');
    expect(el(fixture).querySelector('#edit-hint')?.textContent).toContain('con las flechas');
    expect(el(fixture).querySelector('label[for="edit-longitude"]')?.textContent?.trim()).toBe('Longitud');
    expect(marker(fixture, 'edit').getAttribute('aria-label')).toBe('Chincheta: 43,513600, -8,185900');
    await expectNoAxeViolations(fixture.nativeElement);
  });
});

describe('map-point', () => {
  it('le coma ou punto, signo menos tipográfico e rexeita o que sae do rango', () => {
    expect(parseCoordinate('43,4914', 'latitude')).toBe(43.4914);
    expect(parseCoordinate('-8.1873001', 'longitude')).toBe(-8.1873);
    expect(parseCoordinate('−8,18', 'longitude')).toBe(-8.18);
    expect(parseCoordinate('91', 'latitude')).toBeNull();
    expect(parseCoordinate('179', 'longitude')).toBe(179);
    expect(parseCoordinate('43,49,1', 'latitude')).toBeNull();
    expect(parseCoordinate('', 'latitude')).toBeNull();
  });

  it('as frechas dan 10 px e Maiús 50 px', () => {
    expect(arrowOffset(new KeyboardEvent('keydown', { key: 'ArrowUp' }))).toEqual([0, -10]);
    expect(arrowOffset(new KeyboardEvent('keydown', { key: 'ArrowLeft', shiftKey: true }))).toEqual([-50, 0]);
    expect(arrowOffset(new KeyboardEvent('keydown', { key: 'Enter' }))).toBeNull();
  });
});
