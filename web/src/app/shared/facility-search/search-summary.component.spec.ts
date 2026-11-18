// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../testing/axe';
import { provideTestI18n } from '../../../testing/i18n';
import { addDays } from '../ui/calendar/local-date';
import { madridToday } from '../ui/calendar/madrid-time';
import type { SearchSummary } from './search-summary.component';
import { SearchSummaryComponent } from './search-summary.component';

const TODAY = madridToday();

@Component({
  imports: [SearchSummaryComponent],
  template: `<furar-search-summary
    [summary]="summary()"
    (opened)="opened.set(opened() + 1)"
    (cleared)="cleared.set(true)"
  />`,
})
class HostComponent {
  readonly summary = signal<SearchSummary>({ date: TODAY, today: TODAY, sports: ['Pádel'], kind: 'all' });
  readonly opened = signal(0);
  readonly cleared = signal(false);
}

describe('furar-search-summary', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, pill: () => el.querySelector<HTMLButtonElement>('[data-search-summary]')! };
  }

  it('resume o buscado (hoxe non se di); picala avisa; o ✕ quita todo; pasa axe', async () => {
    const { fixture, el, pill } = await render();
    expect(pill().textContent?.trim()).toBe('Pádel');
    expect(pill().getAttribute('aria-label')).toBe('Buscar: Pádel');
    fixture.componentInstance.summary.set({
      where: 'torre',
      date: addDays(TODAY, 1),
      today: TODAY,
      sports: ['Pádel', 'Cuberta'],
      kind: 'facilities',
      zone: true,
    });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(pill().textContent?.trim()).toBe('torre · Pádel · Cuberta · Mañá · Instalacións · Zona do mapa');
    pill().click();
    expect(fixture.componentInstance.opened()).toBe(1);
    const clear = el.querySelector<HTMLButtonElement>('[data-clear="all"] button')!;
    expect(clear.getAttribute('aria-label')).toBe('Quitar todos os filtros');
    clear.click();
    expect(fixture.componentInstance.cleared()).toBe(true);
    await expectNoAxeViolations(el);
  });
});
