// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { SearchBarComponent } from './search-bar.component';

@Component({
  imports: [SearchBarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-search-bar [filters]="chips" [filterCount]="count()" [doneLabel]="'Ver ' + shown() + ' incidencias'">
      <input type="search" data-q aria-label="Buscar incidencias" />
    </furar-search-bar>
    <ng-template #chips>
      <button type="button" data-chip (click)="shown.set(shown() - 1)">{{ 'Estado' }}</button>
    </ng-template>
  `,
})
class HostComponent {
  readonly count = signal(0);
  readonly shown = signal(12);
}

describe('SearchBarComponent', () => {
  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('o campo da páxina dentro da cápsula e «Filtros» co número de filtros postos; axe', async () => {
    const { fixture, el } = await render();
    expect(el.querySelector('.f-search-bar__query [data-q]')).not.toBeNull();
    const open = el.querySelector<HTMLButtonElement>('[data-filters-open]')!;
    expect(open.getAttribute('aria-label')).toBe('Filtros');
    expect(open.getAttribute('aria-haspopup')).toBe('dialog');
    expect(el.querySelector('[data-filters-count]')).toBeNull();
    fixture.componentInstance.count.set(2);
    await fixture.whenStable();
    expect(open.getAttribute('aria-label')).toBe('Filtros postos: 2');
    expect(el.querySelector('[data-filters-count]')?.textContent?.trim()).toBe('2');
    await expectNoAxeViolations(el);
  });

  it('«Filtros» abre unha capa cos chips da páxina, vivos, e «Ver …» pecha', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('[data-filters-open]')!.click();
    await fixture.whenStable();
    const sheet = document.querySelector<HTMLElement>('[data-filters-sheet]')!;
    expect(sheet.closest('[role="dialog"]')).not.toBeNull();
    const done = () => document.querySelector<HTMLButtonElement>('[data-filters-done]')!;
    expect(done().textContent?.trim()).toBe('Ver 12 incidencias');
    sheet.querySelector<HTMLButtonElement>('[data-chip]')!.click();
    await fixture.whenStable();
    expect(done().textContent?.trim()).toBe('Ver 11 incidencias');
    done().click();
    await fixture.whenStable();
    expect(document.querySelector('[data-filters-sheet]')).toBeNull();
  });
});
