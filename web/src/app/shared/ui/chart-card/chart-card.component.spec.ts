// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { CHART_ENGINE } from '../chart/chart-engine';
import type { ChartSpec } from '../chart/chart-options';
import { ChartComponent } from '../chart/chart.component';
import { DataTableComponent } from '../data-table/data-table.component';
import { ExportMenuComponent } from '../export-menu/export-menu.component';
import type { ChartLegendItem } from './chart-card.component';
import { ChartCardComponent } from './chart-card.component';

@Component({
  imports: [ChartCardComponent, ChartComponent, DataTableComponent, ExportMenuComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-chart-card
      [heading]="'Cancelacións'"
      [conclusion]="'Case todas en prazo'"
      [legend]="legend"
      [(table)]="table"
    >
      <furar-export-menu furarChartCardActions [subject]="'Cancelacións'" [pdf]="noop" [csv]="noop" />
      <furar-chart [chart]="spec" />
      <furar-data-table
        [caption]="'Cancelacións'"
        [columns]="[{ label: 'Mes' }, { label: 'En prazo', numeric: true }]"
        [rows]="[['Outubro', '12']]"
      />
    </furar-chart-card>
  `,
})
class HostComponent {
  readonly spec: ChartSpec = () => ({});
  readonly noop = () => undefined;
  readonly table = signal(false);
  readonly legend: ChartLegendItem[] = [
    { label: 'En prazo', tone: 'success' },
    { label: 'Fóra de prazo', tone: 'danger', pattern: 'stripes' },
  ];
}

describe('furar-chart-card', () => {
  let fixture: ComponentFixture<HostComponent>;
  const el = () => fixture.nativeElement as HTMLElement;
  const toggle = () => el().querySelector<HTMLButtonElement>('.f-chart-card__toggle')!;
  const chart = () => el().querySelector<HTMLElement>('.f-chart-card__chart')!;
  const table = () => el().querySelector<HTMLElement>('.f-chart-card__table')!;

  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({
      providers: [provideTestI18n(lang), { provide: CHART_ENGINE, useValue: () => new Promise(() => undefined) }],
    });
    fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
  }

  it('rexión co título; figure role=img co título e a conclusión; lenda en texto; accións no seu sitio', async () => {
    await render();
    const card = el().querySelector('furar-chart-card')!;
    const title = card.querySelector('h3')!;
    expect(card.getAttribute('role')).toBe('region');
    expect(card.getAttribute('aria-labelledby')).toBe(title.id);
    expect(card.querySelector('.f-chart-card__conclusion')?.textContent?.trim()).toBe('Case todas en prazo');
    const figure = card.querySelector('figure')!;
    expect(figure.getAttribute('role')).toBe('img');
    expect(figure.getAttribute('aria-label')).toBe('Cancelacións. Case todas en prazo');
    expect(figure.querySelector('furar-chart')).not.toBeNull();
    expect([...card.querySelectorAll('.f-chart-card__legend li')].map((li) => li.textContent?.trim())).toEqual([
      'En prazo',
      'Fóra de prazo',
    ]);
    expect(card.querySelector('.f-chart-card__swatch[data-tone="danger"]')?.getAttribute('data-pattern')).toBe(
      'stripes',
    );
    expect(card.querySelector('.f-chart-card__swatch[data-tone="success"]')?.hasAttribute('data-pattern')).toBe(false);
    expect(card.querySelector('.f-chart-card__actions furar-export-menu')).not.toBeNull();
    expect(table().querySelector('furar-data-table')).not.toBeNull();
    expect(table().hidden).toBe(true);
    await expectNoAxeViolations(el());
  });

  it('«Táboa» conmuta gráfico e táboa con aria-pressed e aria-controls, e o foco queda no botón', async () => {
    await render();
    expect(toggle().getAttribute('aria-pressed')).toBe('false');
    expect(toggle().getAttribute('aria-controls')).toBe(table().id);
    expect(toggle().getAttribute('aria-label')).toBe('Táboa: Cancelacións');
    expect(toggle().textContent?.trim()).toBe('Táboa');

    toggle().focus();
    toggle().click();
    await fixture.whenStable();
    expect(toggle().getAttribute('aria-pressed')).toBe('true');
    expect(fixture.componentInstance.table()).toBe(true);
    expect(chart().hidden).toBe(true);
    expect(table().hidden).toBe(false);
    expect(el().querySelector('furar-chart-card')?.getAttribute('data-view')).toBe('table');
    expect(document.activeElement).toBe(toggle());
    await expectNoAxeViolations(el());

    toggle().click();
    await fixture.whenStable();
    expect(toggle().getAttribute('aria-pressed')).toBe('false');
    expect(chart().hidden).toBe(false);
    expect(document.activeElement).toBe(toggle());
  });

  it('en castelán', async () => {
    await render('es');
    expect(toggle().textContent?.trim()).toBe('Tabla');
    expect(toggle().getAttribute('aria-label')).toBe('Tabla: Cancelacións');
    await expectNoAxeViolations(el());
  });
});
