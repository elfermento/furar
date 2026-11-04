// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { addDays, localToday } from '../../../shared/ui/calendar/local-date';
import { FacilitySearchComponent } from '../../../shared/facility-search/facility-search.component';
import type { SearchCriteria } from '../../../shared/facility-search/facility-search';
import { HomeNavigation } from './home-navigation';
import { FACILITIES } from './home.fixture';

function key(target: Element, name: string, init: KeyboardEventInit = {}): void {
  target.dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true, ...init }));
}

describe('buscador da portada (furar-facility-search)', () => {
  let fixture: ComponentFixture<FacilitySearchComponent>;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    Element.prototype.scrollIntoView = () => undefined;
  });

  afterEach(() => {
    TestBed.inject(OverlayContainer).ngOnDestroy();
    vi.useRealTimers();
  });

  async function settle(ms = 0) {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
  }

  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n(), provideRouter([])] });
    fixture = TestBed.createComponent(FacilitySearchComponent);
    fixture.componentRef.setInput('facilities', FACILITIES);
    await settle();
    document.body.appendChild(fixture.nativeElement as HTMLElement);
    return fixture.nativeElement as HTMLElement;
  }

  const query = () => document.querySelector<HTMLInputElement>('input[role="combobox"]')!;

  it('cápsula de busca con rótulo, día e botón de buscar; pasa axe', async () => {
    const el = await render();
    const form = el.querySelector('form[role="search"]');
    expect(form?.getAttribute('aria-label')).toBe('Buscar instalacións');
    expect(el.querySelector(`label[for="${query().id}"]`)?.textContent).toContain('¿Que queres reservar?');
    expect(query().placeholder).toBe('¿Que queres reservar?');
    expect(query().classList.contains('f-input')).toBe(false);
    const day = el.querySelector('.f-date-field__button');
    expect(day?.getAttribute('aria-label')).toBe('Día, Hoxe');
    expect(day?.querySelector('furar-icon[name="calendar"]')?.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelector('button[type="submit"]')?.getAttribute('aria-label')).toBe('Buscar');
    vi.useRealTimers();
    await expectNoAxeViolations(el);
  });

  it('suxestións agrupadas con miniatura e coincidencia marcada; teclado e escolla', async () => {
    await render();
    query().focus();
    query().value = 'pad';
    query().dispatchEvent(new Event('input', { bubbles: true }));
    await settle(150);
    const listbox = document.querySelector('[role="listbox"]');
    expect(listbox?.getAttribute('aria-label')).toBe('Suxestións');
    const groups = [...document.querySelectorAll('[role="group"]')];
    expect(groups.map((g) => document.getElementById(g.getAttribute('aria-labelledby') ?? '')?.textContent)).toEqual([
      'Deportes',
      'Instalacións',
    ]);
    const options = [...document.querySelectorAll<HTMLElement>('[role="option"]')];
    expect(options.map((o) => o.querySelector('mark')?.textContent)).toEqual(['Pád', 'pád']);
    expect(options[1].querySelector('img')?.getAttribute('alt')).toBe('');
    expect(options[1].textContent).toContain('Rúa Buxo');

    key(query(), 'ArrowDown');
    key(query(), 'ArrowDown');
    await settle();
    expect(query().getAttribute('aria-activedescendant')).toBe(options[1].id);
    key(query(), 'Enter');
    await settle();
    expect(query().value).toBe('Pista de pádel 1 de O Alto');
    expect(query().getAttribute('aria-expanded')).toBe('false');

    const searched: SearchCriteria[] = [];
    fixture.componentInstance.searched.subscribe((criteria) => searched.push(criteria));
    (document.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await settle();
    expect(searched).toEqual([{ choice: { kind: 'facility', id: 'f-padel' }, date: localToday() }]);
  });

  it('enche os campos cos criterios que lle dan (o listado, desde o URL)', async () => {
    await render();
    fixture.componentRef.setInput('criteria', {
      choice: { kind: 'sport', key: 'futsal' },
      date: addDays(localToday(), 1),
    });
    await settle();
    expect(query().value).toBe('Fútbol sala');
    expect(document.querySelector('.f-date-field__button')?.getAttribute('aria-label')).toBe('Día, Mañá');
  });

  it('a portada leva á ficha (instalación) ou ao listado co deporte e o día no URL', async () => {
    await render();
    const navigation = TestBed.inject(HomeNavigation);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await navigation.search('naron', { choice: { kind: 'facility', id: 'f-padel' }, date: localToday() });
    expect(navigate).toHaveBeenLastCalledWith(['/', 'naron', 'instalacions', 'f-padel']);
    await navigation.search('naron', { choice: { kind: 'sport', key: 'padel' }, date: localToday() });
    expect(navigate).toHaveBeenLastCalledWith(['/', 'naron', 'instalacions'], { queryParams: { deporte: 'padel' } });
    const tomorrow = addDays(localToday(), 1);
    await navigation.search('naron', { choice: null, date: tomorrow });
    expect(navigate).toHaveBeenLastCalledWith(['/', 'naron', 'instalacions'], { queryParams: { data: tomorrow } });
  });

  it('o día: capa co calendario e os atallos «Hoxe» e «Mañá»', async () => {
    const el = await render();
    const day = el.querySelector<HTMLButtonElement>('.f-date-field__button')!;
    day.click();
    await settle();
    const shortcuts = [...document.querySelectorAll<HTMLButtonElement>('.f-date-field__shortcut')];
    expect(shortcuts.map((b) => [b.textContent?.trim(), b.getAttribute('aria-pressed')])).toEqual([
      ['Hoxe', 'true'],
      ['Mañá', 'false'],
    ]);
    expect(document.querySelector('furar-calendar')).not.toBeNull();
    // Os días pasados non se escollen (apagados, aria-disabled).
    const past = [...document.querySelectorAll<HTMLElement>('[data-date]')].filter(
      (cell) => (cell.dataset['date'] ?? '') < localToday(),
    );
    expect(past.every((cell) => cell.getAttribute('aria-disabled') === 'true')).toBe(true);
    expect(document.querySelector(`[data-date="${addDays(localToday(), 1)}"]`)?.hasAttribute('aria-disabled')).toBe(
      false,
    );
    shortcuts[1].click();
    await settle();
    expect(day.textContent).toContain('Mañá');
    expect(day.getAttribute('aria-label')).toBe('Día, Mañá');
  });
});
