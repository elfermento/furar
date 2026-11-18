// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, provideTestApi } from '../../../../../testing/api';
import { expectNoAxeViolations } from '../../../../../testing/axe';
import { addDays } from '../../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../../shared/ui/calendar/madrid-time';
import { provideTestI18n } from '../../../../../testing/i18n';
import type { MapBounds } from '../../../../shared/ui/map/map-point';
import { CatalogMapComponent } from './catalog-map.component';
import { catalogAvailabilityPage, catalogPage } from './common-home.fixture';
import { CommonHomePage } from './common-home.page';
import { MapZone } from './map-zone';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

const FACILITIES = (r: { url: string }) => r.url === '/api/v1/catalog/facilities';
const AVAILABILITY = (r: { url: string }) => r.url === '/api/v1/catalog/availability';
/** Só Narón (Cambre queda ao sur). */
const NARON: MapBounds = { south: 43.4, west: -8.5, north: 43.6, east: -8 };

describe('«Zona do mapa»', () => {
  it('sen zona, todas; con ela, só as que teñen punto dentro, e os seus ids', () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const zone = TestBed.runInInjectionContext(() => new MapZone());
    const at = (id: string, latitude?: number, longitude?: number) => ({ id, address: { latitude, longitude } });
    const all = [at('n', 43.5, -8.2), at('c', 43.29, -8.34), at('x')];
    expect(zone.keep(all).map((f) => f.id)).toEqual(['n', 'c', 'x']);
    zone.bounds.set(NARON);
    expect(zone.keep(all).map((f) => f.id)).toEqual(['n']);
    zone.clear();
    expect(zone.bounds()).toBeNull();
  });
});

describe('Portada común: «Zona do mapa»', () => {
  let narrow = false;

  beforeEach(() => {
    narrow = false;
    Element.prototype.scrollIntoView = vi.fn();
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.startsWith('(min-width') ? !narrow : false,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    backend().verify();
  });

  async function render(url: string) {
    TestBed.configureTestingModule({
      deferBlockBehavior: DeferBlockBehavior.Manual,
      providers: [
        ...provideTestApi(),
        provideRouter([
          { path: '', pathMatch: 'full', component: CommonHomePage },
          { path: ':municipality/instalacions/:id', component: StubPage },
        ]),
      ],
    });
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement!;
    const free = await expectRequest(AVAILABILITY);
    free.flush(catalogAvailabilityPage(free.request.params.get('date') ?? ''));
    (await expectRequest(FACILITIES)).flush(catalogPage());
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-rail], .f-plist__count')).not.toBeNull();
    });
    const toggle = () => el.querySelector<HTMLButtonElement>('[data-map-toggle]')!;
    /** O mapa avisa de que a persoa o moveu (o rectángulo á vista). */
    const move = async (bounds: MapBounds) => {
      harness.fixture.debugElement.query(By.directive(CatalogMapComponent)).componentInstance.moved.emit(bounds);
      await settle();
    };
    return { el, settle, toggle, move };
  }

  const titles = (el: HTMLElement) =>
    [...el.querySelectorAll('furar-facility-card h3')].map((h) => h.textContent?.trim());

  it('escritorio: abrir o mapa non filtra; movelo deixa só as da zona, que quedan ao volver á lista co chip', async () => {
    const { el, settle, toggle, move } = await render(`/?data=${addDays(madridToday(), 1)}`);
    toggle().click();
    await settle();
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('4 instalacións');
    await move(NARON);
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('3 instalacións nesta zona');
    expect(titles(el)).not.toContain('Pista de pádel de Cambre');
    // A zona conta coma filtro: sen cabeceira.
    expect(el.querySelector('.f-hero')?.hasAttribute('data-filtered')).toBe(true);
    // As marcas seguen sendo todas as dos filtros.
    expect(el.querySelectorAll('.f-plist__grid furar-facility-card')).toHaveLength(3);

    // «Lista»: a zona segue, cun chip para quitala.
    toggle().click();
    await settle();
    expect(el.querySelector('furar-map')).toBeNull();
    expect(titles(el)).toHaveLength(3);
    const chip = el.querySelector<HTMLButtonElement>('[data-search-text] [data-chip-remove]')!;
    expect(chip.getAttribute('aria-label')).toBe('Quitar Zona do mapa');
    await expectNoAxeViolations(el.querySelector('.f-plist')!);
    chip.click();
    await settle();
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('4 instalacións');
    expect(titles(el)).toHaveLength(4);
  }, 20_000);

  it('sen filtros, os carruseis tamén só levan as da zona; unha zona baleira avisa', async () => {
    const { el, settle, toggle, move } = await render('/');
    toggle().click();
    await settle();
    await move(NARON);
    toggle().click();
    await settle();
    expect([...el.querySelectorAll('[data-rail]')].map((r) => r.getAttribute('data-rail'))).toEqual(['naron']);
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('3 instalacións nesta zona');
    // Unha zona sen nada: o aviso, sen carruseis.
    toggle().click();
    await settle();
    await move({ south: 10, west: 10, north: 11, east: 11 });
    expect(el.textContent).toContain('Ningunha instalación nesta zona do mapa');
  }, 20_000);

  it('móbil: a folla do mapa leva o número da zona', async () => {
    narrow = true;
    const { el, settle, toggle, move } = await render('/');
    toggle().click();
    await settle();
    expect(el.querySelector('.f-peek')?.getAttribute('aria-label')).toBe('4 instalacións');
    await move(NARON);
    expect(el.querySelector('.f-peek')?.getAttribute('aria-label')).toBe('3 instalacións nesta zona');
  }, 20_000);
});
