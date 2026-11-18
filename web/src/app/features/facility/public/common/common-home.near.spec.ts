// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, provideTestApi } from '../../../../../testing/api';
import { expectNoAxeViolations } from '../../../../../testing/axe';
import { matchCard } from '../../../../../testing/open-matches';
import { addDays } from '../../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../../shared/ui/calendar/madrid-time';
import { catalogAvailabilityPage, catalogPage } from './common-home.fixture';
import { CommonHomePage } from './common-home.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

const FACILITIES = (r: { url: string }) => r.url === '/api/v1/catalog/facilities';
const AVAILABILITY = (r: { url: string }) => r.url === '/api/v1/catalog/availability';

/** Cun filtro (aquí, mañá): os resultados en lista e mapa. */
const LIST = `/?data=${addDays(madridToday(), 1)}`;

describe('Portada común: «Preto de min» e a folla «Filtros» do móbil', () => {
  let narrow = false;

  beforeEach(() => {
    narrow = false;
    Element.prototype.scrollIntoView = vi.fn();
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
          { path: ':municipality/instalacions/:id/reservar', component: StubPage },
        ]),
      ],
    });
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement!;
    const free = await expectRequest(AVAILABILITY);
    const date = free.request.params.get('date') ?? '';
    expect(free.request.params.get('size')).toBe('50');
    free.flush(catalogAvailabilityPage(date));
    const list = await expectRequest(FACILITIES);
    expect(list.request.params.get('page')).toBe('0');
    list.flush(catalogPage());
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    await vi.waitFor(async () => {
      await settle();
      // Sen filtros, os carruseis; cun filtro, os resultados.
      expect(el.querySelector('[data-rail], .f-plist__count')).not.toBeNull();
    });
    return { harness, el, date, settle };
  }

  const cards = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('.f-plist__grid furar-facility-card')];
  const titles = (el: HTMLElement) => cards(el).map((c) => c.querySelector('h3')?.textContent?.trim());
  const query = () => new URLSearchParams(TestBed.inject(Router).url.split('?')[1] ?? '');

  describe('«Preto de min»', () => {
    let answer: (ok: (p: GeolocationPosition) => void, fail: (e: GeolocationPositionError) => void) => void;
    const getCurrentPosition = vi.fn<
      (
        ok: (p: GeolocationPosition) => void,
        fail: (e: GeolocationPositionError) => void,
        options?: PositionOptions,
      ) => void
    >((ok, fail) => answer(ok, fail));

    beforeEach(() => {
      getCurrentPosition.mockClear();
      Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition }, configurable: true });
    });
    afterEach(() => {
      Reflect.deleteProperty(navigator, 'geolocation');
    });

    const near = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('.f-map__controls [data-near]')!;
    /** Cun filtro e o mapa aberto co botón «Mapa» (no escritorio, ao lado da grella). */
    async function renderMap(url: string) {
      const rendered = await render(url);
      rendered.el.querySelector<HTMLButtonElement>('[data-map-toggle]')!.click();
      await rendered.settle();
      return rendered;
    }
    const places = (el: HTMLElement) =>
      cards(el).map((c) => c.querySelector('.f-media-card__place')?.textContent?.replace(/\s+/g, ' ').trim());

    it('dentro do mapa, con texto; ordena pola distancia, pon «a … km» e apágase', async () => {
      // Xusto ao lado da pista de Cambre.
      answer = (ok) => ok({ coords: { latitude: 43.3, longitude: -8.34 } } as GeolocationPosition);
      const { el, settle } = await renderMap(LIST);
      await vi.waitFor(async () => {
        await settle();
        expect(near(el)).not.toBeNull();
      });
      expect(near(el).textContent?.trim()).toBe('Preto de min');
      expect(near(el).getAttribute('aria-pressed')).toBe('false');
      const before = titles(el);

      near(el).click();
      await vi.waitFor(async () => {
        await settle();
        expect(near(el).getAttribute('aria-pressed')).toBe('true');
      });
      expect(getCurrentPosition).toHaveBeenCalledTimes(1);
      expect(getCurrentPosition.mock.calls[0][2]).toMatchObject({ enableHighAccuracy: false });
      expect(titles(el)[0]).toBe('Pista de pádel de Cambre');
      expect(places(el)[0]).toBe('a 700 m · Cambre · Rúa do Río, 30');
      expect(places(el)[1]).toMatch(/^a 2\d km · Narón · /);
      // Leaflet chega nun chunk á parte: o punto «Estás aquí» sae cando o mapa xa está.
      await vi.waitFor(
        async () => {
          await settle();
          expect(el.querySelector('.leaflet-container .f-map__here')).not.toBeNull();
        },
        { timeout: 5_000 },
      );
      // A posición non sae do navegador: ningunha petición nova.
      backend().expectNone(() => true);

      near(el).click();
      await settle();
      expect(near(el).getAttribute('aria-pressed')).toBe('false');
      expect(titles(el)).toEqual(before);
      expect(places(el)[0]).toBe('Cambre · Rúa do Río, 30');
      await expectNoAxeViolations(el);
    }, 20_000);

    it('sen permiso nada cambia e sae o aviso, que se pecha co foco de volta en «Preto de min»', async () => {
      answer = (_ok, fail) => fail({ code: 1 } as GeolocationPositionError);
      const { el, settle } = await renderMap(LIST);
      await vi.waitFor(async () => {
        await settle();
        expect(near(el)).not.toBeNull();
      });
      const before = titles(el);
      near(el).click();
      const notice = () => el.querySelector<HTMLElement>('[data-near-notice]');
      await vi.waitFor(async () => {
        await settle();
        expect(notice()).not.toBeNull();
      });
      expect(notice()?.textContent?.replace(/\s+/g, ' ')).toContain(
        'Non temos permiso para saber onde estás. Actívao nos axustes do navegador e volve pulsar «Preto de min».',
      );
      expect(notice()?.getAttribute('role')).toBe('status');
      expect(near(el).getAttribute('aria-pressed')).toBe('false');
      expect(titles(el)).toEqual(before);
      await expectNoAxeViolations(el);
      notice()!.querySelector<HTMLButtonElement>('[data-callout-close]')!.click();
      await settle();
      expect(notice()).toBeNull();
      expect(document.activeElement).toBe(near(el));
    }, 20_000);

    it('tamén no mapa a toda a pantalla do móbil, sen filtros: o aviso sae enriba do mapa', async () => {
      narrow = true;
      answer = (_ok, fail) => fail({ code: 1 } as GeolocationPositionError);
      const { el, settle } = await render();
      el.querySelector<HTMLButtonElement>('[data-map-toggle]')!.click();
      await vi.waitFor(async () => {
        await settle();
        expect(el.querySelector('[data-home-map] [data-near]')).not.toBeNull();
      });
      near(el).click();
      const notice = () => el.querySelector<HTMLElement>('[data-home-map] [data-near-notice]');
      await vi.waitFor(async () => {
        await settle();
        expect(notice()).not.toBeNull();
      });
      notice()!.querySelector<HTMLButtonElement>('[data-callout-close]')!.click();
      await settle();
      expect(notice()).toBeNull();
      expect(document.activeElement).toBe(near(el));
    }, 20_000);

    it('se o navegador non o sabe, outro aviso (sen falar de permisos)', async () => {
      answer = (_ok, fail) => fail({ code: 3 } as GeolocationPositionError);
      const { el, settle } = await renderMap(LIST);
      await vi.waitFor(async () => {
        await settle();
        expect(near(el)).not.toBeNull();
      });
      near(el).click();
      await vi.waitFor(async () => {
        await settle();
        expect(el.querySelector('[data-near-notice]')?.textContent).toContain('Non puidemos saber onde estás');
      });
    }, 20_000);
  });

  it('móbil: «Filtros» abre a folla con «Data» e «Deporte»; «Ver N instalacións» aplica e o botón di cantos', async () => {
    narrow = true;
    const { el, settle } = await render();
    const button = el.querySelector<HTMLButtonElement>('.f-search [data-search-filters]')!;
    expect(button.getAttribute('aria-label')).toBe('Filtros');
    expect(button.getAttribute('aria-haspopup')).toBe('dialog');
    button.click();
    const sheet = () => document.querySelector<HTMLElement>('furar-search-filters-dialog');
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).not.toBeNull();
    });
    expect(sheet()!.querySelector('h2')?.textContent?.trim()).toBe('Filtros');
    // Sen data por defecto: «Escoller data» (non «Hoxe»).
    const add = sheet()!.querySelector<HTMLButtonElement>('[data-date-add]')!;
    expect(add.textContent?.trim()).toBe('Escoller data');
    // «Deporte»: o mesmo despregable ca no escritorio, unha lista de texto que segue aberta ao marcar.
    const sport = sheet()!.querySelector<HTMLButtonElement>('[data-filter="sport"] .f-select__button')!;
    expect(sport.textContent?.trim()).toBe('Todos');
    sport.click();
    await settle();
    expect(sport.getAttribute('aria-expanded')).toBe('true');
    const options = () => [...document.querySelectorAll<HTMLElement>('.f-select__option')];
    expect(options().map((o) => o.textContent?.trim())).toEqual([
      'Badminton',
      'Fútbol 11',
      'Fútbol 7',
      'Fútbol sala',
      'Pádel',
      'Cuberta',
    ]);
    const apply = () => sheet()!.querySelector<HTMLButtonElement>('[data-filters-apply]')!;
    expect(apply().textContent?.trim()).toBe('Ver 4 instalacións');
    options()[4].click();
    await settle();
    expect(sport.getAttribute('aria-expanded')).toBe('true');
    expect(sport.textContent?.trim()).toBe('Pádel');
    expect(apply().textContent?.trim()).toBe('Ver 2 instalacións');
    // Até aquí, nada aplicado.
    expect(query().has('deporte')).toBe(false);

    add.click();
    await settle();
    const tomorrow = addDays(madridToday(), 1);
    sheet()!.querySelector<HTMLElement>(`[data-date="${tomorrow}"]`)!.click();
    await settle();
    expect(sheet()!.querySelector('[data-date-chip]')?.textContent).toContain('Mañá');

    apply().click();
    await vi.waitFor(async () => {
      await settle();
      expect(query().getAll('deporte')).toEqual(['padel']);
    });
    expect(query().get('data')).toBe(tomorrow);
    (await expectRequest(AVAILABILITY)).flush(catalogAvailabilityPage(tomorrow));
    expect(sheet()).toBeNull();
    expect(titles(el)).toEqual(['Pista de pádel de Cambre', 'Pista de pádel 1 de O Alto']);
    await vi.waitFor(() => expect(button.getAttribute('aria-label')).toBe('Filtros: 2 postos'));
    expect(button.querySelector('.f-search__count')?.textContent?.trim()).toBe('2');
  }, 20_000);

  it('móbil: a folla péchase sen cambiar nada con Esc; «Quitar filtros» baléiraa', async () => {
    narrow = true;
    const { el, settle } = await render(`/?deporte=padel&data=${madridToday()}`);
    const button = el.querySelector<HTMLButtonElement>('.f-search [data-search-filters]')!;
    // Hoxe non conta coma filtro.
    expect(button.getAttribute('aria-label')).toBe('Filtros: 1 postos');
    button.click();
    const sheet = () => document.querySelector<HTMLElement>('furar-search-filters-dialog');
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).not.toBeNull();
    });
    // Hoxe escollido: o chip co día e a súa ✕.
    expect(sheet()!.querySelector('[data-date-chip]')?.textContent).toContain('Hoxe');
    sheet()!.querySelector<HTMLButtonElement>('[data-filters-clear]')!.click();
    await settle();
    expect(sheet()!.querySelector('[data-date-add]')).not.toBeNull();
    expect(sheet()!.querySelector('[data-filter="sport"] .f-select__text')?.textContent?.trim()).toBe('Todos');
    const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
    Object.defineProperty(escape, 'keyCode', { get: () => 27 });
    sheet()!.dispatchEvent(escape);
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).toBeNull();
    });
    expect(query().getAll('deporte')).toEqual(['padel']);
  }, 20_000);

  it('móbil: «Tipo» leva de todo ás partidas abertas (só se hai algunha); «Quitar filtros» vai arriba', async () => {
    narrow = true;
    const { harness, el, settle } = await render();
    const button = el.querySelector<HTMLButtonElement>('.f-search [data-search-filters]')!;
    const sheet = () => document.querySelector<HTMLElement>('furar-search-filters-dialog');
    const open = async () => {
      button.click();
      await vi.waitFor(async () => {
        await settle();
        expect(sheet()).not.toBeNull();
      });
    };
    // Sen partidas (aínda non cargou a súa sección), a folla non leva «Tipo».
    await open();
    expect(sheet()!.querySelector('[data-filter="kind"]')).toBeNull();
    // «Quitar filtros» non vai no pé, coa acción de aplicar.
    expect(sheet()!.querySelector('.f-dialog__foot [data-filters-clear]')).toBeNull();
    expect(sheet()!.querySelector('[data-filters-clear]')).not.toBeNull();
    sheet()!.querySelector<HTMLButtonElement>('[data-filters-apply]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).toBeNull();
    });

    // Os eventos van antes; as partidas, no último bloque á parte.
    const matches = (await harness.fixture.getDeferBlocks()).at(-1)!;
    await matches.render(DeferBlockState.Complete);
    (await expectRequest((r) => r.url === '/api/v1/catalog/open-matches')).flush({
      page: 0,
      size: 20,
      hasNext: false,
      municipalities: [{ slug: 'naron', name: 'Narón', items: [matchCard()] }],
    });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-open-matches]')).not.toBeNull();
    });

    await open();
    const kind = sheet()!.querySelector<HTMLButtonElement>('[data-filter="kind"] .f-select__button')!;
    expect(sheet()!.querySelector('[data-filter="kind"] label')?.textContent?.trim()).toBe('Tipo');
    expect(kind.textContent?.trim()).toBe('Todo');
    // Con «Todo», o botón di resultados (instalacións, partidas e eventos).
    expect(sheet()!.querySelector('[data-filters-apply]')?.textContent?.trim()).toBe('Ver 4 resultados');
    kind.click();
    await settle();
    const options = () => [...document.querySelectorAll<HTMLElement>('.f-select__option')];
    expect(options().map((o) => o.textContent?.trim())).toEqual(['Todo', 'Instalacións', 'Partidas abertas']);
    options()[2].click();
    await settle();
    // «Deporte» filtra as instalacións: coas partidas non sae; «Data» vale para todo.
    expect(sheet()!.querySelector('[data-filter="sport"]')).toBeNull();
    expect(sheet()!.querySelector('[data-date-add]')).not.toBeNull();
    const apply = sheet()!.querySelector<HTMLButtonElement>('[data-filters-apply]')!;
    expect(apply.textContent?.trim()).toBe('Ver partidas');
    apply.click();
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).toBeNull();
    });
    expect(el.querySelector('.f-plist')).toBeNull();
    expect(el.querySelector('[data-open-matches]')).not.toBeNull();
    expect(button.getAttribute('aria-label')).toBe('Filtros: 1 postos');

    // «Quitar filtros» volve a «Todo».
    await open();
    sheet()!.querySelector<HTMLButtonElement>('[data-filters-clear]')!.click();
    await settle();
    expect(sheet()!.querySelector('[data-filter="kind"] .f-select__button')?.textContent?.trim()).toBe('Todo');
    sheet()!.querySelector<HTMLButtonElement>('[data-filters-apply]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('.f-plist')).not.toBeNull();
    });
    expect(button.getAttribute('aria-label')).toBe('Filtros');
  }, 20_000);
});
