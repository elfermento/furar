// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import {
  backend,
  expectRequest,
  flushProblem,
  flushMunicipalityDirectory,
  provideTestApi,
} from '../../../../../testing/api';
import { expectNoAxeViolations } from '../../../../../testing/axe';
import { MADRID_DAY, fakeBrowserInNewYork } from '../../../../../testing/clock';
import { NO_MAP, provideDeploymentSettings } from '../../../../../testing/deployment';
import { municipality } from '../../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../../core/municipality/current-municipality';
import { addDays } from '../../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../../shared/ui/calendar/madrid-time';
import { LIST_FACILITIES, LIST_SLOTS, availabilityPage } from './public-list.fixture';
import { SPLIT_QUERY } from '../common/common-home';
import { PublicListPage } from './public-list.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

const LIST = '/api/v1/municipalities/naron/public/facilities';
const AVAILABILITY = (r: { url: string }) => r.url === '/api/v1/municipalities/naron/public/availability';

describe('Listado público de instalacións', () => {
  let split = true;

  beforeEach(() => {
    split = true;
    // jsdom non ten matchMedia: só a pregunta de se a lista e o mapa caben un ao lado do outro.
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query === SPLIT_QUERY ? split : false,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    TestBed.inject(OverlayContainer).ngOnDestroy();
    flushMunicipalityDirectory();
    backend().verify();
  });

  async function render(url = '/naron/instalacions', slots = LIST_SLOTS, extra: unknown[] = []) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(),
        ...extra,
        provideRouter([
          { path: 'naron/instalacions', component: PublicListPage },
          { path: 'naron/instalacions/:id', component: StubPage },
          { path: 'naron/instalacions/:id/reservar', component: StubPage },
        ]),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement!;
    const free = await expectRequest(AVAILABILITY);
    const date = free.request.params.get('date') ?? '';
    free.flush(availabilityPage(date, slots));
    (await expectRequest(LIST)).flush(LIST_FACILITIES);
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('.f-plist__count')).not.toBeNull();
    });
    return { harness, el, date, settle };
  }

  const cards = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('.f-plist__grid furar-facility-card')];
  /** «Filtros» da barra (o mesmo das portadas): abre a folla co «Deporte». */
  const filters = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('.f-search [data-search-filters]')!;
  const sheet = () => document.querySelector<HTMLElement>('furar-search-filters-dialog');
  /** Abre a folla «Filtros» e o seu «Deporte»: as opcións quedan na capa. */
  async function openSports(el: HTMLElement, settle: () => Promise<void>) {
    filters(el).click();
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).not.toBeNull();
    });
    sheet()!.querySelector<HTMLButtonElement>('[data-filter="sport"] .f-select__button')!.click();
    await settle();
  }
  const sportText = () => sheet()?.querySelector('[data-filter="sport"] .f-select__button')?.textContent?.trim();
  const apply = () => sheet()!.querySelector<HTMLButtonElement>('[data-filters-apply]')!.click();
  const options = () => [...document.querySelectorAll<HTMLElement>('[role="option"]')];
  const option = (name: string) => options().find((o) => o.textContent?.trim() === name)!;
  const query = () => new URLSearchParams(TestBed.inject(Router).url.split('?')[1] ?? '');

  it('buscador con «Filtros» dentro, recuento, «Mapa» flotante e tarxetas con ocos e horas; pasa axe', async () => {
    const { el, date, settle } = await render();
    expect(date).toBe(madridToday());
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Instalacións');
    expect(el.querySelector('form[role="search"]')).not.toBeNull();
    // O filtro vai dentro da barra, coma nas portadas: nada de chips debaixo dela.
    expect(el.querySelector('.f-plist__filters')).toBeNull();
    expect(filters(el).textContent?.trim()).toBe('Filtros');
    expect(el.querySelector('.f-search furar-multi-select')).toBeNull();
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('3 instalacións');
    // Coma nas portadas: sen «Lista / Mapa», o botón flotante «Mapa».
    expect(el.querySelector('[furarSegmented]')).toBeNull();
    expect(el.querySelector('[data-map-toggle]')?.textContent?.trim()).toBe('Mapa');
    // Tarxeta con ocos: chip enriba da imaxe e as tres primeiras horas, á páxina de reservar.
    const [campo, padel, pavillon] = cards(el);
    expect(campo.querySelector('.f-media-card__image furar-tag')?.textContent?.trim()).toBe('2 ocos hoxe');
    const times = [...campo.querySelectorAll<HTMLAnchorElement>('.f-slot-links a')];
    expect(times.map((a) => a.textContent?.trim())).toEqual(['19:00', '21:00']);
    expect(times[0].getAttribute('href')).toBe(`/naron/instalacions/f-campo/reservar?data=${date}&hora=19:00`);
    expect(campo.querySelector('h2 a')?.getAttribute('href')).toBe('/naron/instalacions/f-campo');
    expect(padel.querySelectorAll('.f-slot-links a')).toHaveLength(3);
    expect(padel.querySelector('.f-slot-links a')?.textContent?.trim()).toBe('09:30');
    // Sen ocos: sen chip nin horas.
    expect(pavillon.querySelector('furar-tag[cardBadge]')).toBeNull();
    expect(pavillon.querySelector('.f-slot-links')).toBeNull();
    await expectNoAxeViolations(el);
    // Os deportes das instalacións, sen repetir e en orde, e «Cuberta» ao final.
    await openSports(el, settle);
    expect(options().map((o) => o.textContent?.trim())).toEqual([
      'Bádminton',
      'Fútbol 11',
      'Fútbol 7',
      'Fútbol sala',
      'Pádel',
      'Cuberta',
    ]);
  }, 20_000);

  it('sen día no URL, pide os ocos do día de Madrid, aínda que o navegador estea noutra zona', async () => {
    const restore = fakeBrowserInNewYork();
    try {
      const { el, date } = await render();
      expect(date).toBe(MADRID_DAY);
      expect(el.querySelector('.f-date-field__button')?.getAttribute('aria-label')).toBe('Día, Hoxe');
    } finally {
      restore();
    }
  });

  it('os filtros viven no URL: deporte e día ao entrar, e «Deporte» da folla cámbiaos', async () => {
    const tomorrow = addDays(madridToday(), 1);
    const { el, date, settle } = await render(`/naron/instalacions?deporte=padel&data=${tomorrow}`);
    expect(date).toBe(tomorrow);
    expect(filters(el).getAttribute('aria-label')).toBe('Filtros: 2 postos');
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('1 instalación');
    expect(cards(el)[0].querySelector('furar-tag[cardBadge]')?.textContent?.trim()).toBe('4 ocos mañá');
    // O buscador abre co deporte e o día do URL.
    expect(el.querySelector<HTMLInputElement>('input[role="combobox"]')?.value).toBe('Pádel');
    expect(el.querySelector('.f-date-field__button')?.getAttribute('aria-label')).toBe('Día, Mañá');
    // Os ocos son os dos usos dese deporte, e a ficha e as horas levan o deporte para reservar nel.
    const padel = cards(el)[0];
    expect(padel.querySelector('h2 a')?.getAttribute('href')).toBe('/naron/instalacions/f-padel?deporte=padel');
    expect(padel.querySelector('.f-slot-links a')?.getAttribute('href')).toBe(
      `/naron/instalacions/f-padel/reservar?data=${tomorrow}&hora=09:30&deporte=padel`,
    );

    await openSports(el, settle);
    expect(sportText()).toBe('Pádel');
    option('Fútbol sala').click();
    await settle();
    apply();
    const both = await expectRequest(AVAILABILITY);
    expect(both.request.params.getAll('sport')).toEqual(['futsal', 'padel']);
    both.flush(availabilityPage(tomorrow, LIST_SLOTS));
    await vi.waitFor(async () => {
      await settle();
      expect(query().getAll('deporte')).toEqual(['futsal', 'padel']);
    });
    expect(query().get('data')).toBe(tomorrow);
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('2 instalacións');

    await openSports(el, settle);
    option('Pádel').click();
    await settle();
    apply();
    const futsal = await expectRequest(AVAILABILITY);
    expect(futsal.request.params.getAll('sport')).toEqual(['futsal']);
    futsal.flush(availabilityPage(tomorrow, LIST_SLOTS));
    await vi.waitFor(async () => {
      await settle();
      expect(query().getAll('deporte')).toEqual(['futsal']);
    });
    expect(cards(el).map((c) => c.querySelector('h2')?.textContent?.trim())).toEqual(['Pavillón de Piñeiros']);
  });

  it('escoller «Mañá» na barra, sen «Buscar», pide os ocos de mañá e vai no URL', async () => {
    const { el, settle } = await render();
    el.querySelector<HTMLButtonElement>('.f-search .f-date-field__button')!.click();
    await settle();
    [...document.querySelectorAll<HTMLButtonElement>('.f-date-field__shortcut')][1].click();
    const tomorrow = addDays(madridToday(), 1);
    const free = await expectRequest(AVAILABILITY);
    expect(free.request.params.get('date')).toBe(tomorrow);
    free.flush(availabilityPage(tomorrow, LIST_SLOTS));
    await vi.waitFor(async () => {
      await settle();
      expect(query().get('data')).toBe(tomorrow);
      expect(cards(el)[0].querySelector('furar-tag[cardBadge]')?.textContent?.trim()).toBe('2 ocos mañá');
    });
    expect(cards(el)[0].querySelector('.f-slot-links a')?.getAttribute('href')).toContain(`data=${tomorrow}`);
  });

  it('o texto buscado (`busca`, da portada do concello) filtra, vai escrito no buscador e quítase co seu chip', async () => {
    const { el, settle } = await render('/naron/instalacions?busca=PINEIROS');
    expect(cards(el).map((c) => c.querySelector('h2')?.textContent?.trim())).toEqual(['Pavillón de Piñeiros']);
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('1 instalación');
    expect(el.querySelector<HTMLInputElement>('input[role="combobox"]')?.value).toBe('PINEIROS');
    el.querySelector<HTMLButtonElement>('[data-search-text] [data-chip-remove]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(cards(el)).toHaveLength(3);
    });
    expect(query().has('busca')).toBe(false);

    // Outro texto desde o propio buscador: o enderezo tamén conta.
    const field = el.querySelector<HTMLInputElement>('input[role="combobox"]')!;
    field.value = 'castela';
    field.dispatchEvent(new Event('input', { bubbles: true }));
    el.querySelector<HTMLFormElement>('form.f-search')!.requestSubmit();
    await vi.waitFor(async () => {
      await settle();
      expect(query().get('busca')).toBe('castela');
    });
    expect(cards(el).map((c) => c.querySelector('h2')?.textContent?.trim())).toEqual(['Pavillón de Piñeiros']);
  });

  it('«Cuberta»: deixa só as que din que son cubertas, vive no URL e sae con «Quitar os filtros»', async () => {
    const { el, settle } = await render('/naron/instalacions?cuberta=si&deporte=padel');
    expect(filters(el).getAttribute('aria-label')).toBe('Filtros: 2 postos');
    expect(el.textContent).toContain('Ningunha instalación con estes filtros');
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Quitar os filtros'))!.click();
    // Sen deportes, os ocos pídense de novo para todos os usos.
    const all = await expectRequest(AVAILABILITY);
    expect(all.request.params.getAll('sport') ?? []).toEqual([]);
    all.flush(availabilityPage(all.request.params.get('date') ?? '', LIST_SLOTS));
    await vi.waitFor(async () => {
      await settle();
      expect(cards(el)).toHaveLength(3);
    });
    expect(query().has('cuberta')).toBe(false);
    await openSports(el, settle);
    option('Cuberta').click();
    await settle();
    apply();
    await vi.waitFor(async () => {
      await settle();
      expect(query().get('cuberta')).toBe('si');
    });
    expect(cards(el).map((c) => c.querySelector('h2')?.textContent?.trim())).toEqual(['Pavillón de Piñeiros']);
  });

  it('sen o dato de cuberta en ningunha, non hai chip «Cuberta»', async () => {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), provideRouter([{ path: 'naron/instalacions', component: PublicListPage }])],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create('/naron/instalacions');
    const el = harness.routeNativeElement!;
    const free = await expectRequest(AVAILABILITY);
    free.flush(availabilityPage(free.request.params.get('date') ?? ''));
    (await expectRequest(LIST)).flush(LIST_FACILITIES.map((f) => ({ ...f, covered: undefined })));
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      expect(el.querySelectorAll('.f-plist__grid furar-facility-card')).toHaveLength(3);
    });
    await openSports(el, async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    });
    expect(options().map((o) => o.textContent?.trim())).not.toContain('Cuberta');
    expect(options()).toHaveLength(5);
  });

  it('un deporte sen instalacións: aviso e «Quitar os filtros»', async () => {
    const { el, settle } = await render('/naron/instalacions?deporte=tenis');
    expect(el.textContent).toContain('Ningunha instalación con estes filtros');
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('0 instalacións');
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Quitar os filtros'))!.click();
    const all = await expectRequest(AVAILABILITY);
    all.flush(availabilityPage(all.request.params.get('date') ?? '', LIST_SLOTS));
    await vi.waitFor(async () => {
      await settle();
      expect(cards(el)).toHaveLength(3);
    });
    expect(query().has('deporte')).toBe(false);
  });

  it('«Mapa»: no escritorio ao lado da grella, unha marca por instalación, que leva á súa ficha', async () => {
    const { el, settle } = await render('/naron/instalacions?deporte=padel&deporte=football');
    el.querySelector<HTMLButtonElement>('[data-map-toggle]')!.click();
    await settle();
    // Nada disto vai no URL.
    expect(query().has('vista')).toBe(false);
    // O punto vén no enderezo da lista: o mapa non pide as fichas (`verify` no `afterEach`).
    // As dúas están no mesmo sitio: un grupo co número, que se abre ao picalo.
    const group = () => el.querySelector<HTMLElement>('.leaflet-container .f-map__marker--cluster');
    await vi.waitFor(
      async () => {
        await settle();
        expect(group()?.textContent?.trim()).toBe('2');
      },
      { timeout: 5000 },
    );
    group()!.click();
    await vi.waitFor(
      async () => {
        await settle();
        expect(el.querySelectorAll('.leaflet-container .f-map__marker')).toHaveLength(2);
      },
      { timeout: 5000 },
    );
    expect(cards(el)).toHaveLength(2);
    const section = el.querySelector('.f-plist__split > .f-plist__map');
    expect(section?.getAttribute('aria-label')).toBe('Mapa das instalacións');
    expect(section?.textContent).toContain('vista Lista');
    expect(el.querySelector('.f-map__canvas')?.getAttribute('aria-hidden')).toBe('true');
    const markers = [...el.querySelectorAll<HTMLElement>('.f-map__marker')];
    expect(markers.map((m) => m.getAttribute('title')).sort()).toEqual([
      'Campo de fútbol 1 de O Alto',
      'Pista de pádel 1 de O Alto',
    ]);
    // Non son parada de tabulador.
    expect(markers.every((m) => !m.hasAttribute('tabindex'))).toBe(true);
    markers.find((m) => m.title.startsWith('Pista'))!.click();
    await vi.waitFor(() =>
      expect(TestBed.inject(Router).url).toBe('/naron/instalacions/f-padel?deporte=padel&deporte=football'),
    );
  }, 20_000);

  it('móbil: «Mapa» a toda a pantalla coa folla do listado asomando; pasa axe', async () => {
    split = false;
    const { el, settle } = await render();
    el.querySelector<HTMLButtonElement>('[data-map-toggle]')!.click();
    await settle();
    const peek = el.querySelector<HTMLElement>('[data-home-map] .f-peek')!;
    expect(peek.getAttribute('aria-label')).toBe('3 instalacións');
    expect(cards(el).every((c) => c.closest('.f-peek'))).toBe(true);
    expect(el.querySelector('[data-map-toggle]')?.textContent?.trim()).toBe('Lista');
    await expectNoAxeViolations(el.querySelector('[data-home-map]')!);
  });

  it('sen mapa no despregue: sen «Mapa» e «vista=mapa» dá a lista', async () => {
    const { el } = await render('/naron/instalacions?vista=mapa', LIST_SLOTS, [provideDeploymentSettings(NO_MAP)]);
    expect(el.querySelector('[data-map-toggle]')).toBeNull();
    expect(el.querySelector('furar-map')).toBeNull();
    expect(cards(el)).toHaveLength(3);
    await expectNoAxeViolations(el);
  });

  it('se falla a carga, erro con «Tentar de novo»; se fallan os ocos, tarxetas sen chip', async () => {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), provideRouter([{ path: 'naron/instalacions', component: PublicListPage }])],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create('/naron/instalacions');
    const el = harness.routeNativeElement!;
    flushProblem(await expectRequest(AVAILABILITY), 500, 'common.internal');
    flushProblem(await expectRequest(LIST), 500, 'common.internal');
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      expect(el.textContent).toContain('Non se puideron cargar as instalacións');
    });
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Tentar de novo'))!.click();
    (await expectRequest(LIST)).flush(LIST_FACILITIES);
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      expect(el.querySelectorAll('.f-plist__grid furar-facility-card')).toHaveLength(3);
    });
    expect(el.querySelector('furar-tag[cardBadge]')).toBeNull();
  });
});
