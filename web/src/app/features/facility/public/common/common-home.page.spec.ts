// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../../testing/api';
import { expectNoAxeViolations } from '../../../../../testing/axe';
import { IGN_MAP, NO_MAP, provideDeploymentSettings } from '../../../../../testing/deployment';
import { addDays } from '../../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../../shared/ui/calendar/madrid-time';
import { CATALOG_SLOTS, catalogAvailabilityPage, catalogPage } from './common-home.fixture';
import { CommonHomePage } from './common-home.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

const FACILITIES = (r: { url: string }) => r.url === '/api/v1/catalog/facilities';
const AVAILABILITY = (r: { url: string }) => r.url === '/api/v1/catalog/availability';
/** Os ocos que se piden de novo ao cambiar os deportes: os deportes que van á API. */
async function flushSportSlots(): Promise<string[]> {
  const free = await expectRequest(AVAILABILITY);
  free.flush(catalogAvailabilityPage(free.request.params.get('date') ?? ''));
  return free.request.params.getAll('sport') ?? [];
}

/** Cun filtro (aquí, mañá): os resultados en lista e mapa, coma sempre. */
const LIST = `/?data=${addDays(madridToday(), 1)}`;

describe('Portada común', () => {
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

  async function render(url = LIST, extra: unknown[] = []) {
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
      expect(el.querySelector(url === '/' ? '[data-rail]' : '.f-plist__count')).not.toBeNull();
    });
    return { harness, el, date, settle };
  }

  const cards = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('.f-plist__grid furar-facility-card')];
  const titles = (el: HTMLElement) => cards(el).map((c) => c.querySelector('h3')?.textContent?.trim());
  const toggle = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('[data-map-toggle]')!;
  const query = () => new URLSearchParams(TestBed.inject(Router).url.split('?')[1] ?? '');
  const options = () => [...document.querySelectorAll<HTMLElement>('[role="option"]')];
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

  it('«¿Es un concello?»: a franxa do final só se o despregue ten a función, cara á solicitude; pasa axe', async () => {
    const none = await render();
    expect(none.el.querySelector('[data-join-band]')).toBeNull();
    TestBed.resetTestingModule();

    const { el } = await render(LIST, [provideDeploymentSettings(IGN_MAP, null, true)]);
    const band = el.querySelector<HTMLElement>('[data-join-band]');
    expect(band?.querySelector('h2')?.textContent?.trim()).toBe('¿Es un concello?');
    expect(band?.querySelector('p')?.textContent?.trim()).toBe(
      'Pon as túas instalacións deportivas nesta web, para que a veciñanza as reserve en liña.',
    );
    const action = band?.querySelector<HTMLAnchorElement>('a');
    expect(action?.textContent?.trim()).toBe('Solicitar a alta');
    expect(action?.getAttribute('href')).toBe('/alta-concello');
    // Ao final: despois das instalacións.
    expect(el.querySelector('.f-home')?.lastElementChild).toBe(band);
    await expectNoAxeViolations(band!);
  });

  it('«Reserva a túa pista», «Buscar», a barra con «Filtros» e as instalacións de todos os concellos; pasa axe', async () => {
    const { el, date } = await render();
    expect(date).toBe(addDays(madridToday(), 1));
    expect(el.querySelector('h1')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Reserva a túa pista');
    // «Buscar» vai fóra do encabezado, despois del; cun filtro, a barra xa está fóra.
    const open = el.querySelector<HTMLButtonElement>('[data-open-search]')!;
    expect(open.closest('h1')).toBeNull();
    expect(open.textContent?.trim()).toBe('Buscar');
    expect(el.querySelector('.f-hero__search')?.hasAttribute('data-open')).toBe(true);
    expect(el.querySelector<HTMLInputElement>('input[role="combobox"]')?.placeholder).toBe('Instalación ou concello');
    // Os deportes van na folla «Filtros»: na barra, só o botón (sen número nin ✕ sen filtros).
    expect(el.querySelector('.f-plist__filters')).toBeNull();
    expect(el.querySelector('.f-search furar-multi-select')).toBeNull();
    const filters = el.querySelector<HTMLButtonElement>('.f-search [data-search-filters]')!;
    expect(filters.textContent?.trim()).toBe('Filtros');
    expect(filters.getAttribute('aria-label')).toBe('Filtros');
    expect(el.querySelector('.f-search [data-clear="filters"]')).toBeNull();
    expect(el.querySelector('[furarSegmented]')).toBeNull();

    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('4 instalacións');
    expect(titles(el)).toEqual([
      'Pista de pádel de Cambre',
      'Campo de fútbol 1 de O Alto',
      'Pista de pádel 1 de O Alto',
      'Pavillón de Piñeiros',
    ]);
    // O concello diante do enderezo; a tarxeta e as horas levan ao concello de cada unha.
    const [cambre, campo] = cards(el);
    expect(cambre.querySelector('.f-media-card__place')?.textContent?.trim()).toBe('Cambre · Rúa do Río, 30');
    expect(cambre.querySelector('h3 a')?.getAttribute('href')).toBe('/cambre/instalacions/c-pista');
    expect(cambre.querySelector('.f-media-card__image furar-tag')?.textContent?.trim()).toBe('1 oco mañá');
    expect(cambre.querySelector('.f-slot-links a')?.getAttribute('href')).toBe(
      `/cambre/instalacions/c-pista/reservar?data=${date}&hora=20:00`,
    );
    expect(campo.querySelector('.f-media-card__place')?.textContent?.trim()).toBe('Narón · Rúa Buxo, 16');
    expect(campo.querySelector('h3 a')?.getAttribute('href')).toBe('/naron/instalacions/f-campo');
    expect(campo.querySelector('.f-slot-links a')?.getAttribute('href')).toBe(
      `/naron/instalacions/f-campo/reservar?data=${date}&hora=19:00`,
    );
    await expectNoAxeViolations(el);
  }, 20_000);

  it('escritorio: sen «Lista / Mapa»; «Mapa» pon o mapa ao lado da grella; picar unha marca leva á súa ficha', async () => {
    const { el, settle } = await render();
    // De entrada, a grella sen mapa e o botón flotante «Mapa».
    expect(el.querySelector('[furarSegmented]')).toBeNull();
    expect(el.querySelector('furar-map')).toBeNull();
    expect(cards(el)).toHaveLength(4);
    expect(toggle(el).textContent?.trim()).toBe('Mapa');
    toggle(el).click();
    await settle();
    expect(toggle(el).textContent?.trim()).toBe('Lista');
    expect(el.querySelector('.f-plist__split')).not.toBeNull();
    expect(el.querySelector('[data-home-map]')).toBeNull();
    expect(cards(el)).toHaveLength(4);
    const section = el.querySelector('.f-plist__map');
    expect(section?.getAttribute('aria-label')).toBe('Mapa das instalacións');
    // As tres de Narón están no mesmo sitio: un grupo co número, que se abre ao picalo.
    await withMarkers(el, settle, 2);
    const group = el.querySelector<HTMLElement>('.f-map__marker--cluster')!;
    expect(group.textContent?.trim()).toBe('3');
    group.click();
    await withMarkers(el, settle, 4);
    expect(
      markers(el)
        .map((m) => m.getAttribute('title'))
        .sort(),
    ).toEqual([
      'Campo de fútbol 1 de O Alto · Narón',
      'Pavillón de Piñeiros · Narón',
      'Pista de pádel 1 de O Alto · Narón',
      'Pista de pádel de Cambre · Cambre',
    ]);

    // Pasar o rato (ou o foco) por unha tarxeta marca o seu punto; ao saír, ningún.
    const item = cards(el)[0].closest('li')!;
    item.dispatchEvent(new MouseEvent('mouseenter'));
    await settle();
    const cambre = markers(el).find((m) => m.title.endsWith('Cambre'))!;
    expect(cambre.hasAttribute('data-active')).toBe(true);
    item.dispatchEvent(new MouseEvent('mouseleave'));
    await settle();
    expect(cambre.hasAttribute('data-active')).toBe(false);
    item.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    await settle();
    expect(cambre.hasAttribute('data-active')).toBe(true);
    // Nada disto vai no URL.
    expect(TestBed.inject(Router).url).toBe(LIST);

    // «Lista» volve á grella a todo o ancho.
    toggle(el).click();
    await settle();
    expect(el.querySelector('furar-map')).toBeNull();
    expect(el.querySelector('.f-plist__split')).toBeNull();
    expect(cards(el)).toHaveLength(4);

    // Picar unha marca soa leva á ficha da instalación, no seu concello.
    toggle(el).click();
    await withMarkers(el, settle, 2);
    el.querySelector<HTMLElement>('.f-map__marker--cluster')!.click();
    await withMarkers(el, settle, 4);
    markers(el)
      .find((m) => m.title.startsWith('Pavillón'))!
      .click();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/naron/instalacions/f-pavillon'));
  }, 20_000);

  it('móbil: «Mapa» pon o mapa a toda a pantalla coa folla dos resultados asomando; estendida, «Mapa» vólvea pregar', async () => {
    narrow = true;
    const { el, settle } = await render();
    expect(el.querySelector('[furarSegmented]')).toBeNull();
    expect(el.querySelector('furar-map')).toBeNull();
    expect(cards(el)).toHaveLength(4);
    toggle(el).click();
    await settle();
    expect(el.querySelector('.f-plist__split')).toBeNull();
    expect(el.querySelector('[data-home-map] furar-map')).not.toBeNull();
    // A folla asoma co número de resultados; o listado vai nela (pregada, inerte), non na páxina.
    const sheet = el.querySelector<HTMLElement>('[data-home-map] .f-peek')!;
    const head = sheet.querySelector<HTMLButtonElement>('[data-peek-toggle]')!;
    expect(sheet.getAttribute('aria-label')).toBe('4 resultados');
    expect(head.getAttribute('aria-expanded')).toBe('false');
    expect(cards(el)).toHaveLength(4);
    expect(cards(el).every((c) => c.closest('.f-peek__body[inert]'))).toBe(true);
    expect(toggle(el).textContent?.trim()).toBe('Lista');
    await expectNoAxeViolations(el.querySelector('[data-home-map]')!);

    // Picala estende a folla por riba do mapa; o botón pasa a «Mapa», que a prega.
    head.click();
    await settle();
    expect(sheet.hasAttribute('data-expanded')).toBe(true);
    expect(head.getAttribute('aria-expanded')).toBe('true');
    expect(sheet.querySelector('.f-peek__body')?.hasAttribute('inert')).toBe(false);
    expect(toggle(el).textContent?.trim()).toBe('Mapa');
    toggle(el).click();
    await settle();
    expect(sheet.hasAttribute('data-expanded')).toBe(false);
    expect(el.querySelector('[data-home-map]')).not.toBeNull();

    // Esc prega primeiro a folla e despois pecha o mapa.
    head.click();
    await settle();
    sheet.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(sheet.hasAttribute('data-expanded')).toBe(false);
    expect(el.querySelector('[data-home-map]')).not.toBeNull();
    el.querySelector('[data-home-map]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(el.querySelector('[data-home-map]')).toBeNull();
    // A páxina volve ter a grella.
    expect(cards(el).some((c) => c.closest('.f-peek'))).toBe(false);
  }, 20_000);

  it('móbil: picar unha marca soa leva á ficha da instalación', async () => {
    narrow = true;
    const { el, settle } = await render();
    toggle(el).click();
    await settle();
    await withMarkers(el, settle, 2);
    markers(el)
      .find((m) => m.title.endsWith('Cambre'))!
      .click();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/cambre/instalacions/c-pista'));
  }, 20_000);

  it('ao escribir, o deporte propio de dous concellos suxírese unha soa vez; escollelo xa filtra os de todos', async () => {
    const { el, settle } = await render();
    const field = el.querySelector<HTMLInputElement>('input[role="combobox"]')!;
    field.focus();
    field.value = 'badm';
    field.dispatchEvent(new Event('input', { bubbles: true }));
    const suggested = () => options().filter((o) => o.querySelector('img') === null);
    await vi.waitFor(async () => {
      await settle();
      expect(suggested()).toHaveLength(1);
    });
    expect(suggested()[0].textContent?.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()).toContain('badminton');
    // O deporte é a primeira suxestión: frecha abaixo e Intro.
    for (const key of ['ArrowDown', 'Enter']) {
      field.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
      await settle();
    }
    await flushSportSlots();
    await vi.waitFor(async () => {
      await settle();
      expect(query().getAll('deporte')).toEqual(['~badminton']);
    });
    expect(titles(el)).toEqual(['Pista de pádel de Cambre', 'Pavillón de Piñeiros']);
  });

  it('un concello no URL deixa só as súas, co nome escrito no buscador; «Quitar os filtros» volve a todas', async () => {
    const tomorrow = addDays(madridToday(), 1);
    const { el, date, settle } = await render(`/?concello=cambre&deporte=football&data=${tomorrow}`);
    expect(date).toBe(tomorrow);
    expect(el.querySelector<HTMLInputElement>('input[role="combobox"]')?.value).toBe('Cambre');
    expect(el.textContent).toContain('Ningunha instalación con estes filtros');
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Quitar os filtros'))!.click();
    expect(await flushSportSlots()).toEqual([]);
    await vi.waitFor(async () => {
      await settle();
      expect(cards(el)).toHaveLength(4);
    });
    expect(query().has('concello')).toBe(false);
    expect(query().get('data')).toBe(tomorrow);
    expect(cards(el)[0].querySelector('furar-tag[cardBadge]')?.textContent?.trim()).toBe('1 oco mañá');
  });

  it('escoller «Mañá» na barra, sen «Buscar», pide os ocos de mañá; o ✕ do día volve a hoxe', async () => {
    const { el, settle } = await render('/?concello=naron');
    const day = el.querySelector<HTMLButtonElement>('.f-search .f-date-field__button')!;
    expect(el.querySelector('.f-search [data-clear="day"]')).toBeNull();
    day.click();
    await settle();
    [...document.querySelectorAll<HTMLButtonElement>('.f-date-field__shortcut')][1].click();
    const tomorrow = addDays(madridToday(), 1);
    const free = await expectRequest(AVAILABILITY);
    expect(free.request.params.get('date')).toBe(tomorrow);
    free.flush(catalogAvailabilityPage(tomorrow));
    await vi.waitFor(async () => {
      await settle();
      expect(query().get('data')).toBe(tomorrow);
      expect(cards(el)[0].querySelector('furar-tag[cardBadge]')?.textContent).toContain('mañá');
    });
    const time = cards(el)[0].querySelector<HTMLAnchorElement>('.f-slot-links a');
    expect(time?.getAttribute('href')).toContain(`data=${tomorrow}`);

    const clear = el.querySelector<HTMLButtonElement>('.f-search [data-clear="day"] button')!;
    expect(clear.getAttribute('aria-label')).toBe('Quitar a data');
    clear.click();
    (await expectRequest(AVAILABILITY)).flush(catalogAvailabilityPage(madridToday()));
    await vi.waitFor(async () => {
      await settle();
      expect(day.getAttribute('aria-label')).toBe('Día, Hoxe');
    });
    // Hoxe non vai no URL.
    expect(query().has('data')).toBe(false);
    expect(document.activeElement).toBe(day);
    expect(el.querySelector('.f-search [data-clear="day"]')).toBeNull();
  });

  it('sen mapa no despregue: sen «Mapa» nin mapa, só as tarxetas', async () => {
    const { el } = await render(LIST, [provideDeploymentSettings(NO_MAP)]);
    expect(el.querySelector('[data-map-toggle]')).toBeNull();
    expect(el.querySelector('furar-map')).toBeNull();
    expect(el.querySelector('.f-plist__split')).toBeNull();
    expect(cards(el)).toHaveLength(4);
    await expectNoAxeViolations(el);
  });

  it('pide todas as páxinas de concellos; sen ningún publicado, avísao', async () => {
    TestBed.configureTestingModule({
      deferBlockBehavior: DeferBlockBehavior.Manual,
      providers: [...provideTestApi(), provideRouter([{ path: '', pathMatch: 'full', component: CommonHomePage }])],
    });
    const harness = await RouterTestingHarness.create('/');
    const el = harness.routeNativeElement!;
    const free = await expectRequest(AVAILABILITY);
    free.flush(catalogAvailabilityPage(free.request.params.get('date') ?? '', []));
    (await expectRequest((r) => FACILITIES(r) && r.params.get('page') === '0')).flush({
      page: 0,
      size: 50,
      hasNext: true,
      items: [],
    });
    (await expectRequest((r) => FACILITIES(r) && r.params.get('page') === '1')).flush({
      page: 1,
      size: 50,
      hasNext: false,
      items: [],
    });
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      expect(el.textContent).toContain('Aínda non hai instalacións publicadas');
    });
    expect(el.textContent).toContain('Cando os concellos publiquen as súas instalacións');
    expect(el.querySelector('h1')?.textContent).toContain('Reserva a túa');
  });

  it('se falla a carga, erro con «Tentar de novo»; se fallan os ocos, tarxetas sen chip', async () => {
    TestBed.configureTestingModule({
      deferBlockBehavior: DeferBlockBehavior.Manual,
      providers: [...provideTestApi(), provideRouter([{ path: '', pathMatch: 'full', component: CommonHomePage }])],
    });
    const harness = await RouterTestingHarness.create('/');
    const el = harness.routeNativeElement!;
    flushProblem(await expectRequest(AVAILABILITY), 500, 'common.internal');
    flushProblem(await expectRequest(FACILITIES), 500, 'common.internal');
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      expect(el.textContent).toContain('Non se puideron cargar as instalacións');
    });
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Tentar de novo'))!.click();
    (await expectRequest(FACILITIES)).flush(catalogPage());
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      expect(el.querySelectorAll('furar-facility-card')).toHaveLength(4);
    });
    expect(el.querySelector('furar-tag[cardBadge]')).toBeNull();
    expect(CATALOG_SLOTS).not.toHaveLength(0);
  });
});
