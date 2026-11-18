// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../../testing/axe';
import { provideTestI18n } from '../../../testing/i18n';
import { addDays } from '../ui/calendar/local-date';
import { madridToday } from '../ui/calendar/madrid-time';
import { DialogService } from '../ui/dialog/dialog.service';
import type { SearchFacility } from './facility-search';
import type { SearchSheetResult } from './search-sheet.component';
import { openSearchSheet } from './search-sheet.component';

const TODAY = madridToday();
const FACILITIES: SearchFacility[] = [
  {
    id: 'f-padel',
    name: 'Pista de pádel 1 de O Alto',
    address: { street: 'Rúa Buxo', streetNumber: '12' },
    sports: [{ code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } }],
    cover: { thumb: '/f-padel.jpg' },
  },
  {
    id: 'f-pavillon',
    name: 'Pavillón de Piñeiros',
    address: { street: 'Estrada de Castela' },
    sports: [{ code: 'futsal', label: { gl: 'Fútbol sala', es: 'Fútbol sala' } }],
    cover: { thumb: '/f-pavillon.jpg' },
  },
];
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim();

describe('folla «Buscar» do móbil', () => {
  let opener: HTMLButtonElement;

  beforeEach(() => {
    // Móbil: as capas saen como folla desde abaixo.
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('max-width'),
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
    // jsdom non despraza: o corpo da folla tampouco.
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', { value: () => undefined, configurable: true });
    TestBed.configureTestingModule({ providers: [provideTestI18n(), provideRouter([])] });
    opener = document.createElement('button');
    opener.textContent = 'Buscar';
    document.body.append(opener);
    opener.focus();
  });

  afterEach(() => {
    TestBed.inject(OverlayContainer).ngOnDestroy();
    opener.remove();
    vi.unstubAllGlobals();
  });

  async function stable() {
    await TestBed.inject(ApplicationRef).whenStable();
    TestBed.inject(ApplicationRef).tick();
    await TestBed.inject(ApplicationRef).whenStable();
  }

  async function open(kinds = true) {
    let result: SearchSheetResult | undefined | null = null;
    const event = { currentTarget: opener } as unknown as Event;
    void openSearchSheet(
      TestBed.inject(DialogService),
      {
        facilities: FACILITIES,
        criteria: { choice: null, date: TODAY, text: '' },
        options: [
          { value: 'padel', label: 'Pádel' },
          { value: 'futsal', label: 'Fútbol sala' },
        ],
        value: [],
        today: TODAY,
        ...(kinds ? { kinds: ['all', 'facilities', 'matches'] as const, kind: 'all' as const } : {}),
      },
      event,
    ).then((r) => (result = r));
    await stable();
    const sheet = document.querySelector<HTMLElement>('furar-search-sheet')!;
    const section = (name: string) => sheet.querySelector<HTMLButtonElement>(`[data-section="${name}"]`);
    const field = () => sheet.querySelector<HTMLInputElement>('.f-ssheet__input');
    return { sheet, section, field, result: () => result };
  }

  it('«Buscar» co ✕, «Onde» aberto co foco no texto e o resto en filas co escollido; pasa axe', async () => {
    const { sheet, section, field } = await open();
    expect(document.querySelector('.f-dialog-pane--sheet')).not.toBeNull();
    expect(sheet.querySelector('h2')?.textContent?.trim()).toBe('Buscar');
    await vi.waitFor(() => expect(document.activeElement).toBe(field()));
    expect(field()?.placeholder).toBe('Instalación ou deporte');
    expect(section('where')?.getAttribute('aria-expanded')).toBe('true');
    expect(['when', 'kind', 'sport'].map((s) => [section(s)?.getAttribute('aria-expanded'), text(section(s))])).toEqual(
      [
        ['false', 'Cando Hoxe'],
        ['false', 'Tipo Todo'],
        ['false', 'Deporte Todos'],
      ],
    );
    await expectNoAxeViolations(sheet);
  });

  it('suxestións dentro de «Onde»; desprazar a lista baixa o teclado; escoller unha pasa a «Cando»', async () => {
    const { sheet, section, field } = await open();
    await vi.waitFor(() => expect(document.activeElement).toBe(field()));
    field()!.value = 'pad';
    field()!.dispatchEvent(new Event('input', { bubbles: true }));
    await stable();
    const groups = [...sheet.querySelectorAll('.f-combobox__group-label')].map((g) => g.textContent?.trim());
    expect(groups).toEqual(['Deportes', 'Instalacións']);
    expect(sheet.querySelector('[role="status"]')?.textContent?.trim()).toBe('2 suxestións');
    // Desprazar a lista (non un toque) quita o foco do campo: baixa o teclado.
    sheet.querySelector('.f-ssheet__suggestions')!.dispatchEvent(new Event('touchmove', { bubbles: true }));
    expect(document.activeElement).not.toBe(field());
    const options = [...sheet.querySelectorAll<HTMLButtonElement>('.f-ssheet__options button')];
    expect(options[0].querySelector('mark')?.textContent).toBe('Pád');
    options[0].click();
    await stable();
    expect(text(section('where'))).toBe('Onde Pádel');
    expect(section('when')?.getAttribute('aria-expanded')).toBe('true');
    expect(sheet.querySelector('furar-calendar')).not.toBeNull();
    await expectNoAxeViolations(sheet);
  });

  it('«Mañá» pasa a «Tipo»; coas partidas non hai «Deporte»; «Buscar» pecha co escollido', async () => {
    const { sheet, section, result } = await open();
    section('when')!.click();
    await stable();
    [...sheet.querySelectorAll<HTMLButtonElement>('.f-date-field__shortcut')][1].click();
    await stable();
    expect(section('kind')?.getAttribute('aria-expanded')).toBe('true');
    const kinds = () => [...sheet.querySelectorAll<HTMLElement>('.f-select__option')];
    expect(kinds().map((o) => o.textContent?.trim())).toEqual(['Todo', 'Instalacións', 'Partidas abertas']);
    expect(kinds()[0].getAttribute('aria-selected')).toBe('true');
    kinds()[2].click();
    await stable();
    expect(section('sport')).toBeNull();
    sheet.querySelector<HTMLButtonElement>('[data-search-apply]')!.click();
    await stable();
    expect(result()).toEqual({
      criteria: { choice: null, date: addDays(TODAY, 1), text: '' },
      value: [],
      kind: 'matches',
    });
    expect(document.querySelector('furar-search-sheet')).toBeNull();
  });

  it('escribir (non o foco ao abrir) quita o pé, sen mover a folla', async () => {
    const { sheet, field } = await open();
    await vi.waitFor(() => expect(document.activeElement).toBe(field()));
    // O foco ao abrir non é escribir: o pé segue.
    expect(sheet.hasAttribute('data-typing')).toBe(false);
    // Teclear si.
    field()!.value = 'p';
    field()!.dispatchEvent(new Event('input', { bubbles: true }));
    await stable();
    expect(sheet.hasAttribute('data-typing')).toBe(true);
    expect(sheet.getAttribute('style') ?? '').toBe('');
    // Saír do campo con texto (desprazar as suxestións) non o volve amosar: o corpo non muda de alto.
    field()!.blur();
    await stable();
    expect(sheet.hasAttribute('data-typing')).toBe(true);
    // Saír co campo baleiro, si.
    field()!.focus();
    field()!.value = '';
    field()!.dispatchEvent(new Event('input', { bubbles: true }));
    field()!.blur();
    await stable();
    expect(sheet.hasAttribute('data-typing')).toBe(false);
  });

  it('o pé segue agochado con «Onde» aberto tras saír do campo; volve noutro apartado', async () => {
    const { sheet, section, field } = await open();
    field()!.value = 'p';
    field()!.dispatchEvent(new Event('input', { bubbles: true }));
    field()!.blur();
    await stable();
    expect(sheet.hasAttribute('data-typing')).toBe(true);
    section('when')!.click();
    await stable();
    expect(sheet.hasAttribute('data-typing')).toBe(false);
  });

  it('premer o campo (o dedo) tamén é escribir', async () => {
    const { sheet, field } = await open();
    field()!.blur();
    field()!.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    field()!.focus();
    await stable();
    expect(sheet.hasAttribute('data-typing')).toBe(true);
  });

  it('«Deporte» marca varios; «Borrar todo» baléirao todo e volve a «Onde»', async () => {
    const { sheet, section, field, result } = await open(false);
    expect(section('kind')).toBeNull();
    section('sport')!.click();
    await stable();
    const options = [...sheet.querySelectorAll<HTMLElement>('.f-select__option')];
    options[0].click();
    options[1].click();
    await stable();
    section('where')!.click();
    await stable();
    expect(text(section('sport'))).toBe('Deporte 2 filtros');
    field()!.value = 'torre';
    field()!.dispatchEvent(new Event('input', { bubbles: true }));
    sheet.querySelector<HTMLButtonElement>('[data-search-clear]')!.click();
    await stable();
    expect(field()?.value).toBe('');
    expect(text(section('sport'))).toBe('Deporte Todos');
    sheet.querySelector<HTMLButtonElement>('[data-search-apply]')!.click();
    await stable();
    expect(result()).toEqual({ criteria: { choice: null, date: TODAY, text: '' }, value: [], kind: 'all' });
  });
});
