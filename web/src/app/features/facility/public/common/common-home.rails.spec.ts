// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, provideTestApi } from '../../../../../testing/api';
import { expectNoAxeViolations } from '../../../../../testing/axe';
import { NO_MAP, provideDeploymentSettings } from '../../../../../testing/deployment';
import { addDays } from '../../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../../shared/ui/calendar/madrid-time';
import { CATALOG_SLOTS, catalogAvailabilityPage, catalogPage } from './common-home.fixture';
import { CommonHomePage } from './common-home.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

const FACILITIES = (r: { url: string }) => r.url === '/api/v1/catalog/facilities';
const AVAILABILITY = (r: { url: string }) => r.url === '/api/v1/catalog/availability';

describe('Portada común sen filtros: carruseis por concello e «Mapa»', () => {
  let narrow = false;

  beforeEach(() => {
    narrow = false;
    // jsdom non ten matchMedia: escritorio (a barra co día, a lista e o mapa lado a lado) ou móbil.
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.startsWith('(min-width') ? !narrow : false,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    TestBed.inject(OverlayContainer).ngOnDestroy();
    backend().verify();
  });

  async function render(url = '/', extra: unknown[] = []) {
    TestBed.configureTestingModule({
      deferBlockBehavior: DeferBlockBehavior.Manual,
      providers: [
        ...provideTestApi(),
        ...extra,
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
      // Sen filtros, os carruseis; cun filtro, os resultados.
      expect(el.querySelector('[data-rail], .f-plist__count')).not.toBeNull();
    });
    return { el, settle };
  }

  const cards = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('.f-plist__grid furar-facility-card')];
  const markers = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('.leaflet-container .f-map__marker')];

  async function withMarkers(el: HTMLElement, settle: () => Promise<void>, count: number) {
    await vi.waitFor(
      async () => {
        await settle();
        expect(markers(el)).toHaveLength(count);
      },
      { timeout: 5000 },
    );
  }

  it('sen filtros: un carrusel por concello coas tarxetas da portada, sen Lista · Mapa; pasa axe', async () => {
    const { el } = await render('/');
    expect(el.querySelector('.f-plist__count')).toBeNull();
    expect(el.querySelector('[furarSegmented]')).toBeNull();
    expect(cards(el)).toHaveLength(0);
    const rails = [...el.querySelectorAll<HTMLElement>('[data-rail]')];
    expect(rails.map((r) => r.querySelector('h3')?.textContent?.trim())).toEqual(['Cambre', 'Narón']);
    expect(rails.map((r) => r.querySelector('.f-carousel__strip')?.getAttribute('aria-label'))).toEqual([
      'Cambre',
      'Narón',
    ]);
    expect(rails.map((r) => r.querySelectorAll('furar-facility-card').length)).toEqual([1, 3]);
    const cambre = rails[0].querySelector('furar-facility-card')!;
    expect(cambre.querySelector('h3')?.textContent?.trim()).toBe('Pista de pádel de Cambre');
    expect(cambre.querySelector('a')?.getAttribute('href')).toBe('/cambre/instalacions/c-pista');
    expect(cambre.querySelector('furar-tag[cardBadge]')?.textContent?.trim()).toBe('1 oco hoxe');
    expect(rails[1].querySelector('furar-facility-card a')?.getAttribute('href')).toBe('/naron/instalacions/f-campo');
    // O mapa non se pinta ata pulsar «Mapa».
    expect(el.querySelector('furar-map')).toBeNull();
    await expectNoAxeViolations(el);
  }, 20_000);

  it('escritorio: «Mapa» pon a grella con todas á esquerda e o mapa á dereita; «Lista» volve aos carruseis', async () => {
    const { el, settle } = await render('/');
    const toggle = el.querySelector<HTMLButtonElement>('[data-map-toggle]')!;
    expect(toggle.textContent?.trim()).toBe('Mapa');
    toggle.click();
    await settle();
    expect(toggle.textContent?.trim()).toBe('Lista');
    // Sen carruseis: todas as instalacións en grella, coas tarxetas do listado, e o mapa ao lado.
    expect(el.querySelectorAll('[data-rail]')).toHaveLength(0);
    expect(cards(el)).toHaveLength(4);
    expect(el.querySelector('.f-plist__split > .f-plist__map')?.getAttribute('aria-label')).toBe(
      'Mapa das instalacións',
    );
    expect(el.querySelector('[data-home-map]')).toBeNull();
    expect(el.querySelector('[furarSegmented]')).toBeNull();
    await withMarkers(el, settle, 2);
    toggle.click();
    await settle();
    expect(el.querySelector('furar-map')).toBeNull();
    expect(el.querySelectorAll('[data-rail]')).toHaveLength(2);
    expect(toggle.textContent?.trim()).toBe('Mapa');
    // Nada disto vai no URL.
    expect(TestBed.inject(Router).url).toBe('/');
  }, 20_000);

  it('móbil: «Mapa» pon o mapa a toda a pantalla coas mesmas marcas; «Lista» e Esc volven', async () => {
    narrow = true;
    const { el, settle } = await render('/');
    const toggle = el.querySelector<HTMLButtonElement>('[data-map-toggle]')!;
    expect(toggle.textContent?.trim()).toBe('Mapa');
    toggle.click();
    await settle();
    const map = el.querySelector<HTMLElement>('[data-home-map]')!;
    expect(map.getAttribute('aria-label')).toBe('Mapa das instalacións');
    expect(toggle.textContent?.trim()).toBe('Lista');
    await withMarkers(el, settle, 2);
    expect(map.querySelectorAll('.leaflet-container .f-map__marker')).toHaveLength(2);
    // Os carruseis seguen debaixo: o mapa vai por riba.
    expect(el.querySelectorAll('[data-rail]')).toHaveLength(2);
    toggle.click();
    await settle();
    expect(el.querySelector('[data-home-map]')).toBeNull();
    expect(toggle.textContent?.trim()).toBe('Mapa');

    toggle.click();
    await settle();
    el.querySelector('[data-home-map]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(el.querySelector('[data-home-map]')).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/');
  }, 20_000);

  it('escritorio: a barra sae arriba ao pulsar «Buscar», co foco no texto; o ✕ do texto baléirao', async () => {
    const { el, settle } = await render('/');
    const wrap = el.querySelector<HTMLElement>('.f-hero__search')!;
    expect(wrap.hasAttribute('data-open')).toBe(false);
    el.querySelector<HTMLButtonElement>('[data-open-search]')!.click();
    await settle();
    expect(wrap.hasAttribute('data-open')).toBe(true);
    const field = el.querySelector<HTMLInputElement>('input[role="combobox"]')!;
    await vi.waitFor(() => expect(document.activeElement).toBe(field));
    // Sen texto, sen ✕; ao escribir sen coincidencias, sen panel baleiro.
    expect(el.querySelector('[data-clear="text"]')).toBeNull();
    field.value = 'zzzz';
    field.dispatchEvent(new Event('input', { bubbles: true }));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-clear="text"]')).not.toBeNull();
    });
    await new Promise((resolve) => setTimeout(resolve, 200));
    await settle();
    expect(document.querySelector('.f-combobox__panel')).toBeNull();
    expect(field.getAttribute('aria-expanded')).toBe('false');
    const clear = el.querySelector<HTMLButtonElement>('[data-clear="text"] button')!;
    expect(clear.getAttribute('aria-label')).toBe('Quitar o texto');
    clear.click();
    await settle();
    expect(field.value).toBe('');
    expect(document.activeElement).toBe(field);
    expect(el.querySelector('[data-clear="text"]')).toBeNull();
    // A barra segue fóra.
    expect(wrap.hasAttribute('data-open')).toBe(true);
    await expectNoAxeViolations(el.querySelector('.f-hero')!);
  }, 20_000);

  it('móbil: «Buscar» abre a folla de busca (sen barra); «Buscar» nela aplica o texto, o día e o tipo', async () => {
    narrow = true;
    const { el, settle } = await render('/');
    const open = el.querySelector<HTMLButtonElement>('[data-open-search]')!;
    // O mesmo tamaño ca o botón «Mapa» e o «Instalar» do aviso.
    expect(open.getAttribute('data-size')).toBe(el.querySelector('[data-map-toggle]')?.getAttribute('data-size'));
    open.click();
    const sheet = () => document.querySelector<HTMLElement>('furar-search-sheet');
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).not.toBeNull();
    });
    expect(el.querySelector('furar-hero-search')?.hasAttribute('data-open')).toBe(false);
    const field = sheet()!.querySelector<HTMLInputElement>('.f-ssheet__input')!;
    expect(field.placeholder).toBe('Instalación, deporte ou concello');
    field.value = 'pista';
    field.dispatchEvent(new Event('input', { bubbles: true }));
    sheet()!.querySelector<HTMLButtonElement>('[data-search-apply]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(TestBed.inject(Router).url).toBe('/?busca=pista');
    });
    expect(sheet()).toBeNull();
  }, 20_000);

  it('móbil, cun filtro: sen cabeceira, a pílula co resumo volve abrir a folla; o seu ✕ quítao todo', async () => {
    narrow = true;
    const tomorrow = addDays(madridToday(), 1);
    const { el, settle } = await render(`/?deporte=padel&data=${tomorrow}`);
    expect(el.querySelector('.f-hero')?.hasAttribute('data-filtered')).toBe(true);
    const pill = el.querySelector<HTMLButtonElement>('[data-search-summary]')!;
    expect(pill.textContent?.trim()).toBe('Pádel · Mañá');
    pill.click();
    const sheet = () => document.querySelector<HTMLElement>('furar-search-sheet');
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).not.toBeNull();
    });
    // Coa data e o deporte postos.
    expect(sheet()!.querySelector('[data-section="when"]')?.textContent).toContain('Mañá');
    expect(sheet()!.querySelector('[data-section="sport"]')?.textContent).toContain('Pádel');
    sheet()!.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).toBeNull();
    });
    el.querySelector<HTMLButtonElement>('[data-clear="all"] button')!.click();
    (await expectRequest(AVAILABILITY)).flush(catalogAvailabilityPage(madridToday()));
    await vi.waitFor(async () => {
      await settle();
      expect(TestBed.inject(Router).url).toBe('/');
    });
    expect(el.querySelector('.f-hero')?.hasAttribute('data-filtered')).toBe(false);
    expect(el.querySelectorAll('[data-rail]')).toHaveLength(2);
  }, 20_000);

  it('cada tarxeta dos carruseis con ocos leva as súas horas (ata tres), que levan a reservar ese día', async () => {
    const { el } = await render('/');
    const railCards = [...el.querySelectorAll<HTMLElement>('[data-rail] furar-facility-card')];
    expect(railCards.length).toBeGreaterThan(0);
    for (const card of railCards) {
      const id = card.querySelector('h3 a')?.getAttribute('href')?.split('/').pop() ?? '';
      const slot = CATALOG_SLOTS.find((s) => s.facilityId === id);
      const times = [...card.querySelectorAll<HTMLAnchorElement>('.f-slot-links a')];
      // As que teñen horas libres levan as súas (ata tres); as outras, ningunha.
      expect(times.length).toBe(slot && slot.freeCount > 0 ? Math.min(3, slot.firstStarts.length) : 0);
      for (const time of times)
        expect(time.getAttribute('href')).toMatch(/\/reservar\?data=\d{4}-\d{2}-\d{2}&hora=\d{2}:\d{2}$/);
    }
    const cambre = el.querySelector('[data-rail="cambre"] furar-facility-card')!;
    expect(cambre.querySelector('.f-slot-links a')?.getAttribute('href')).toMatch(
      /^\/cambre\/instalacions\/c-pista\/reservar\?data=.*&hora=20:00$/,
    );
    // No carrusel o concello xa vai no título: na tarxeta, só a rúa.
    expect(cambre.querySelector('.f-media-card__place')?.textContent?.trim()).toBe('Rúa do Río, 30');
  });

  it('sen filtros e sen mapa no despregue: só os carruseis, sen «Mapa»', async () => {
    const { el } = await render('/', [provideDeploymentSettings(NO_MAP)]);
    expect(el.querySelectorAll('[data-rail]')).toHaveLength(2);
    expect(el.querySelector('[data-map-toggle]')).toBeNull();
  });
});
