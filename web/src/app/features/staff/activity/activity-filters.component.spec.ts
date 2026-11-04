// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideTestI18n } from '../../../../testing/i18n';
import { pickOption, selectText } from '../../../../testing/select';
import type { ActivityFilters, ActivityScope } from './activity';
import { NO_FILTERS } from './activity';
import { ActivityFiltersComponent } from './activity-filters.component';

const AREAS = [
  { value: '', label: 'Todas' },
  { value: 'staff', label: 'Persoal' },
  { value: 'facility', label: 'Instalacións' },
];
const PEOPLE = [
  { value: '', label: 'Todas' },
  { value: 'm1', label: 'Xestora Narón' },
];

describe('ActivityFiltersComponent: filtros do rexistro', () => {
  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  async function render(filters: Partial<ActivityFilters> = {}, scope: ActivityScope = 'municipality') {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(ActivityFiltersComponent);
    const set = (name: string, value: unknown) => fixture.componentRef.setInput(name, value);
    set('filters', { ...NO_FILTERS, ...filters });
    set('scope', scope);
    set('areas', AREAS);
    set('people', PEOPLE);
    set('today', '2026-10-28');
    set('label', 'Filtros do rexistro');
    fixture.autoDetectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const emitted: ActivityFilters[] = [];
    fixture.componentInstance.changed.subscribe((value) => emitted.push(value));
    const settle = () => fixture.whenStable();
    const panel = () => document.querySelector<HTMLElement>('.f-activity__dates');
    const hint = () => panel()?.querySelector('.f-activity__dates-hint')?.textContent?.trim();
    const applyButton = () => panel()!.querySelector<HTMLButtonElement>('[data-period-apply]')!;
    /** Pica un día do calendario do período. */
    const pick = async (day: string) => {
      fixture.debugElement.query(By.css('furar-calendar')).triggerEventHandler('picked', day);
      await settle();
    };
    const openPeriod = async () => {
      el.querySelector<HTMLButtonElement>('#activity-period')!.click();
      await settle();
    };
    return { fixture, el, emitted, settle, panel, hint, applyButton, pick, openPeriod };
  }

  it('área, persoa e «Só fallidos» emiten os filtros novos; escoller o que xa hai non emite', async () => {
    const { el, emitted, settle } = await render();
    expect(el.querySelector('[data-reset]')).toBeNull();
    expect(selectText(el.querySelector('[data-area]')!)).toBe('Todas');

    await pickOption(el.querySelector('[data-area]')!, 'Persoal', settle);
    await pickOption(el.querySelector('[data-area]')!, 'Todas', settle);
    await pickOption(el.querySelector('[data-person]')!, 'Xestora Narón', settle);
    await pickOption(el.querySelector('[data-person]')!, 'Todas', settle);
    el.querySelector<HTMLButtonElement>('[data-failed]')!.click();
    await settle();
    expect(emitted).toEqual([
      { ...NO_FILTERS, area: 'staff' },
      { ...NO_FILTERS, memberId: 'm1' },
      { ...NO_FILTERS, failedOnly: true },
    ]);
  });

  it('con algún filtro, «Quitar os filtros» volve a ningún', async () => {
    const { el, emitted, settle } = await render({ area: 'facility', memberId: 'm1' });
    await pickOption(el.querySelector('[data-area]')!, 'Instalacións', settle);
    await pickOption(el.querySelector('[data-person]')!, 'Xestora Narón', settle);
    expect(emitted).toEqual([]);
    el.querySelector<HTMLButtonElement>('[data-reset]')!.click();
    expect(emitted).toEqual([NO_FILTERS]);
  });

  it('período: primeiro clic o inicio, o segundo o fin (un día anterior volve empezar); «Aplicar» emite e pecha', async () => {
    const { emitted, hint, applyButton, pick, openPeriod, panel } = await render();
    await openPeriod();
    expect(hint()).toBe('Escolle o primeiro día.');
    expect(applyButton().disabled).toBe(true);
    // Sen período escollido, non hai «Ver o último ano».
    expect(panel()!.querySelector('[data-period-clear]')).toBeNull();
    await pick('2026-10-10');
    expect(hint()).toBe('Agora, o último día.');
    await pick('2026-10-01');
    expect(hint()).toBe('Agora, o último día.');
    await pick('2026-10-20');
    expect(hint()).toBe('Preme «Aplicar» para ver ese período.');
    expect(applyButton().disabled).toBe(false);
    // Un terceiro clic empeza outro período.
    await pick('2026-10-05');
    expect(hint()).toBe('Agora, o último día.');
    await pick('2026-10-25');
    applyButton().click();
    expect(emitted).toEqual([{ ...NO_FILTERS, from: '2026-10-05', to: '2026-10-25' }]);
  });

  it('período de máis dun ano: avisa e non deixa aplicar; «Ver o último ano» quita o período', async () => {
    const { emitted, hint, applyButton, pick, openPeriod, panel, settle } = await render({
      from: '2025-09-01',
      to: '2025-09-30',
    });
    await openPeriod();
    expect(hint()).toBe('Preme «Aplicar» para ver ese período.');
    await pick('2025-09-01');
    await pick('2026-10-20');
    expect(hint()).toBe('Como moito 365 días: escolle un último día máis cedo.');
    expect(panel()!.querySelector('.f-activity__dates-hint')?.hasAttribute('data-error')).toBe(true);
    expect(applyButton().disabled).toBe(true);
    panel()!.querySelector<HTMLButtonElement>('[data-period-clear]')!.click();
    await settle();
    expect(emitted).toEqual([{ ...NO_FILTERS, from: null, to: null }]);
    expect(panel()).toBeNull();
  });

  it('na plataforma, só o período', async () => {
    const { el } = await render({}, 'platform');
    expect(el.querySelector('#activity-period')).not.toBeNull();
    expect(el.querySelector('[data-area]')).toBeNull();
    expect(el.querySelector('[data-person]')).toBeNull();
    expect(el.querySelector('[data-failed]')).toBeNull();
  });
});
