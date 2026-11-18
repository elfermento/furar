// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import type { PeriodValue } from '../../shared/ui/date-filter/period-filter';
import { PeriodFilterComponent } from '../../shared/ui/date-filter/period-filter.component';
import type { ExportAction } from '../../shared/ui/export-menu/export-menu.component';
import { ExportMenuComponent } from '../../shared/ui/export-menu/export-menu.component';
import { FilterBarComponent, FilterFieldComponent } from '../../shared/ui/filter-bar/filter-bar.component';
import type { SelectOption } from '../../shared/ui/select/select.component';
import { SelectComponent } from '../../shared/ui/select/select.component';
import type { IndicatorFilters } from './indicator-filters';
import { MAX_CUSTOM_DAYS } from './indicator-filters';
import type { IndicatorFiltersResponse } from './indicators.store';

let nextId = 0;

/** Os textos que pon a páxina (xa traducidos): o mes en curso e as opcións. */
export interface FilterTexts {
  readonly month: string;
  readonly facilities: readonly SelectOption<string>[];
  readonly uses: readonly SelectOption<string>[];
}

/**
 * Fila de filtros dos indicadores, na fila común (`furar-filter-bar`): período co
 * chip de data de toda a app (`furar-period-filter`: atallos Esta semana · Este mes · Trimestre ·
 * Ano e calendario dun rango ata un ano), instalación, uso e «Exportar» do panel. Non
 * garda nada: emite os filtros novos e a páxina lévaos ao URL.
 */
@Component({
  selector: 'furar-indicator-filters',
  imports: [
    TranslocoPipe,
    ExportMenuComponent,
    FilterBarComponent,
    FilterFieldComponent,
    PeriodFilterComponent,
    SelectComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-indicators__filters' },
  template: `
    @let t = 'indicators.filters.';
    <furar-filter-bar [label]="label()">
      <furar-filter-field [label]="t + 'period' | transloco">
        <furar-period-filter
          [value]="filters()"
          [today]="today()"
          [month]="texts().month"
          [maxDays]="maxDays"
          [label]="t + 'period' | transloco"
          (changed)="setPeriod($event)"
        />
      </furar-filter-field>
      <furar-filter-field [label]="t + 'facility' | transloco" [controlId]="facilityId">
        <furar-select
          data-facility
          [selectId]="facilityId"
          [label]="t + 'facility' | transloco"
          [options]="[{ value: '', label: (t + 'allFacilities' | transloco) }].concat(texts().facilities)"
          [value]="filters().facilityId ?? ''"
          (changed)="setFacility($event)"
        />
      </furar-filter-field>
      <furar-filter-field [label]="t + 'use' | transloco" [controlId]="useId">
        <furar-select
          data-use
          [selectId]="useId"
          [label]="t + 'use' | transloco"
          [options]="[{ value: '', label: (t + 'allUses' | transloco) }].concat(texts().uses)"
          [value]="filters().use ?? ''"
          (changed)="setUse($event)"
        />
      </furar-filter-field>
      <furar-export-menu
        class="f-filter-bar__end"
        size="md"
        [pdfHint]="t + 'pdfHint' | transloco"
        [csvHint]="t + 'csvHint' | transloco"
        [pdf]="pdf()"
        [csv]="csv()"
      />
    </furar-filter-bar>
  `,
})
export class IndicatorFiltersComponent {
  readonly filters = input.required<IndicatorFilters>();
  readonly options = input<IndicatorFiltersResponse | null>(null);
  readonly texts = input.required<FilterTexts>();
  readonly today = input.required<IsoDate>();
  /** Nome do grupo de filtros. */
  readonly label = input.required<string>();

  readonly changed = output<IndicatorFilters>();
  /** PDF do panel (o «Exportar» dos filtros). */
  readonly pdf = input.required<ExportAction>();
  /** CSV do panel. */
  readonly csv = input.required<ExportAction>();

  private readonly ids = nextId++;
  protected readonly facilityId = `indicators-facility-${this.ids}`;
  protected readonly useId = `indicators-use-${this.ids}`;
  protected readonly maxDays = MAX_CUSTOM_DAYS;

  protected setPeriod(period: PeriodValue): void {
    this.emit({ period: period.period, from: period.from, to: period.to });
  }

  protected setFacility(value: string): void {
    const facilityId = value || null;
    if (facilityId === this.filters().facilityId) return;
    // O uso queda se a nova instalación ten un co mesmo nome.
    const keeps = this.options()?.uses.some(
      (u) => u.name === this.filters().use && (!facilityId || u.facilityId === facilityId),
    );
    this.emit({ facilityId, use: keeps ? this.filters().use : null });
  }

  protected setUse(value: string): void {
    const use = value || null;
    if (use !== this.filters().use) this.emit({ use });
  }

  private emit(change: Partial<IndicatorFilters>): void {
    this.changed.emit({ ...this.filters(), ...change });
  }
}
