// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, provideTestApi } from '../../../../../testing/api';
import { expectNoAxeViolations } from '../../../../../testing/axe';
import { addDays } from '../../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../../shared/ui/calendar/madrid-time';
import { catalogAvailabilityPage, catalogPage } from './common-home.fixture';
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

/** Cun filtro (aquí, mañá): os resultados en grella. */
const LIST = `/?data=${addDays(madridToday(), 1)}`;

describe('Portada común: o buscador (texto solto e suxestións)', () => {
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
  const options = () => [...document.querySelectorAll<HTMLElement>('[role="option"]')];
  const sheet = () => document.querySelector<HTMLElement>('furar-search-filters-dialog');
  async function openFilters(el: HTMLElement, settle: () => Promise<void>) {
    el.querySelector<HTMLButtonElement>('.f-search [data-search-filters]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(sheet()).not.toBeNull();
    });
    const sport = sheet()!.querySelector<HTMLButtonElement>('[data-filter="sport"] .f-select__button')!;
    sport.click();
    await settle();
    return sheet()!.querySelector<HTMLButtonElement>('[data-filters-apply]')!;
  }

  it('texto solto e buscar (botón ou Intro): filtra lista e mapa sen acentos, co chip para quitalo', async () => {
    const { el, settle } = await render();
    const field = el.querySelector<HTMLInputElement>('input[role="combobox"]')!;
    field.focus();
    field.value = 'PISTA';
    field.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
    el.querySelector<HTMLButtonElement>('.f-search button[type="submit"]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(query().get('busca')).toBe('PISTA');
    });
    expect(titles(el)).toEqual(['Pista de pádel de Cambre', 'Pista de pádel 1 de O Alto']);
    expect(el.querySelector('.f-plist__count')?.textContent?.trim()).toBe('2 instalacións');
    expect(field.value).toBe('PISTA');
    const remove = el.querySelector<HTMLButtonElement>('[data-search-text] [data-chip-remove]')!;
    expect(remove.getAttribute('aria-label')).toBe('Quitar «PISTA»');
    await expectNoAxeViolations(el);

    // Sen acentos: «padel O alto» atopa a de Narón polo nome e o concello non estorba.
    field.value = 'padel alto';
    field.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
    el.querySelector<HTMLFormElement>('form.f-search')!.requestSubmit();
    await vi.waitFor(async () => {
      await settle();
      expect(titles(el)).toEqual(['Pista de pádel 1 de O Alto']);
    });

    // Sen o texto, sen filtros: volven os carruseis.
    remove.click();
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('[data-rail]')).toHaveLength(2);
    });
    expect(query().has('busca')).toBe(false);
    expect(field.value).toBe('');
    expect(el.querySelector('[data-search-text] li')).toBeNull();
  });

  it('escoller unha instalación suxerida leva xa á súa ficha, no seu concello', async () => {
    const { el, settle } = await render();
    const field = el.querySelector<HTMLInputElement>('input[role="combobox"]')!;
    field.focus();
    field.value = 'cambre';
    field.dispatchEvent(new Event('input', { bubbles: true }));
    const facility = () => options().find((o) => o.textContent?.includes('Pista de pádel de Cambre'));
    await vi.waitFor(async () => {
      await settle();
      expect(facility()).toBeDefined();
    });
    facility()!.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 0 }));
    await vi.waitFor(async () => {
      await settle();
      expect(TestBed.inject(Router).url).toBe('/cambre/instalacions/c-pista');
    });
  });

  it('escritorio: «Filtros» abre a folla sen «Data» (vai na barra); «Deporte» aplícase con «Ver» e o ✕ quítao', async () => {
    const { el, settle } = await render(LIST);
    const apply = await openFilters(el, settle);
    expect(sheet()!.querySelector('[data-date-add], [data-date-chip]')).toBeNull();
    expect(options().map((o) => o.textContent?.trim())).toEqual([
      'Badminton',
      'Fútbol 11',
      'Fútbol 7',
      'Fútbol sala',
      'Pádel',
      'Cuberta',
    ]);
    options()[4].click();
    await settle();
    options()[5].click();
    await settle();
    expect(apply.textContent?.trim()).toBe('Ver 1 instalación');
    apply.click();
    expect(await flushSportSlots()).toEqual(['padel']);
    await vi.waitFor(async () => {
      await settle();
      expect(query().getAll('deporte')).toEqual(['padel']);
      expect(query().get('cuberta')).toBe('si');
    });
    expect(titles(el)).toEqual(['Pista de pádel de Cambre']);
    const button = el.querySelector<HTMLButtonElement>('.f-search [data-search-filters]')!;
    expect(button.getAttribute('aria-label')).toBe('Filtros: 2 postos');
    expect(button.querySelector('.f-search__count')?.textContent?.trim()).toBe('2');

    // O ✕ de «Filtros» quítaos (o día queda) e deixa o foco no botón.
    const clear = el.querySelector<HTMLButtonElement>('.f-search [data-clear="filters"] button')!;
    expect(clear.getAttribute('aria-label')).toBe('Quitar os filtros');
    clear.click();
    expect(await flushSportSlots()).toEqual([]);
    await vi.waitFor(async () => {
      await settle();
      expect(query().has('deporte')).toBe(false);
    });
    expect(query().has('cuberta')).toBe(false);
    expect(query().get('data')).toBe(addDays(madridToday(), 1));
    expect(document.activeElement).toBe(button);
    expect(el.querySelector('.f-search [data-clear="filters"]')).toBeNull();
    await expectNoAxeViolations(el);
  }, 20_000);

  it('un deporte propio co mesmo nome en dous concellos sae unha soa vez e filtra as instalacións de todos', async () => {
    const { el, settle } = await render(LIST);
    const apply = await openFilters(el, settle);
    const names = options().map((o) => o.textContent?.trim().toLowerCase());
    expect(names.filter((name) => name?.normalize('NFD').replace(/\p{M}/gu, '') === 'badminton')).toHaveLength(1);
    expect(names).toHaveLength(6);
    options()[0].click();
    await settle();
    apply.click();
    // Á API van os deportes de cada concello co seu id (ou o código, se o hai).
    expect((await flushSportSlots()).length).toBeGreaterThan(0);
    await vi.waitFor(async () => {
      await settle();
      expect(query().getAll('deporte')).toEqual(['~badminton']);
    });
    // A de Cambre (o seu «Badminton») e a de Narón (o seu «Bádminton»).
    expect(titles(el)).toEqual(['Pista de pádel de Cambre', 'Pavillón de Piñeiros']);
  });
});
