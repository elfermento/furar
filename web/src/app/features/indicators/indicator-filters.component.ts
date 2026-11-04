// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { CalendarComponent } from '../../shared/ui/calendar/calendar.component';
import type { ExportAction } from '../../shared/ui/export-menu/export-menu.component';
import { ExportMenuComponent } from '../../shared/ui/export-menu/export-menu.component';
import { FieldPopoverComponent } from '../../shared/ui/field-popover/field-popover.component';
import { FilterBarComponent, FilterFieldComponent } from '../../shared/ui/filter-bar/filter-bar.component';
import { SegmentedDirective } from '../../shared/ui/segmented/segmented.directive';
import type { SelectOption } from '../../shared/ui/select/select.component';
import { SelectComponent } from '../../shared/ui/select/select.component';
import type { IndicatorFilters, PeriodKey } from './indicator-filters';
import { MAX_CUSTOM_DAYS, PERIOD_KEYS, rangeDays } from './indicator-filters';
import type { IndicatorFiltersResponse } from './indicators.store';

let nextId = 0;

/** Os textos que pon a páxina (xa traducidos): o mes en curso, as opcións e o rango escollido. */
export interface FilterTexts {
  readonly month: string;
  readonly range: string;
  readonly facilities: readonly SelectOption<string>[];
  readonly uses: readonly SelectOption<string>[];
}

/**
 * Fila de filtros dos indicadores (maqueta 31), na fila común (`furar-filter-bar`): período (`[furarSegmented]`: Esta semana · o mes
 * en curso polo seu nome · Trimestre · Ano · Datas…), instalación, uso e «Exportar» do panel.
 * «Datas…» abre un calendario de rango nun `furar-field-popover` (primeiro clic, o inicio; o
 * segundo, o fin) e «Aplicar» cámbiao; se se pecha sen aplicar, segue o período de antes. Non
 * garda nada: emite os filtros novos e a páxina lévaos ao URL.
 */
@Component({
  selector: 'furar-indicator-filters',
  imports: [
    TranslocoPipe,
    ButtonDirective,
    CalendarComponent,
    ExportMenuComponent,
    FieldPopoverComponent,
    FilterBarComponent,
    FilterFieldComponent,
    SegmentedDirective,
    SelectComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-indicators__filters' },
  template: `
    @let t = 'indicators.filters.';
    <furar-filter-bar [label]="label()">
      <furar-filter-field #periodField [label]="t + 'period' | transloco">
        <div furarSegmented role="group" data-period-group [attr.aria-labelledby]="periodField.labelId">
          @for (key of periods; track key) {
            <button
              type="button"
              [attr.data-period]="key"
              [attr.aria-pressed]="filters().period === key"
              (click)="choose(key)"
            >
              {{ key === 'mes' ? texts().month : (t + 'periods.' + key | transloco) }}
            </button>
          }
        </div>
      </furar-filter-field>
      @if (datesShown()) {
        <furar-filter-field [label]="t + 'dates' | transloco" [controlId]="datesId">
          <furar-field-popover
            #dates
            data-dates
            look="filter"
            [triggerId]="datesId"
            [label]="t + 'dates' | transloco"
            [value]="texts().range"
            [placeholder]="t + 'datesEmpty' | transloco"
            (closed)="datesClosed()"
          >
            <div class="f-indicators__dates">
              <furar-calendar
                range
                [value]="draftFrom()"
                [rangeEnd]="draftTo()"
                [today]="today()"
                (picked)="pick($event)"
              />
              <p class="f-indicators__dates-hint" aria-live="polite" [attr.data-error]="tooLong() || null">
                {{ t + datesHint() | transloco: { max: maxDays } }}
              </p>
              <button
                type="button"
                furarButton="primary"
                size="sm"
                data-dates-apply
                [disabled]="!canApply()"
                (click)="apply()"
              >
                {{ t + 'apply' | transloco }}
              </button>
            </div>
          </furar-field-popover>
        </furar-filter-field>
      }
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
  private readonly injector = inject(Injector);
  private readonly dates = viewChild<FieldPopoverComponent>('dates');
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

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

  protected readonly periods = PERIOD_KEYS;
  private readonly ids = nextId++;
  protected readonly datesId = `indicators-dates-${this.ids}`;
  protected readonly facilityId = `indicators-facility-${this.ids}`;
  protected readonly useId = `indicators-use-${this.ids}`;
  protected readonly maxDays = String(MAX_CUSTOM_DAYS);
  /** Escollendo datas aínda sen aplicar (o campo aparece ao premer «Datas…»). */
  private readonly picking = signal(false);
  /** «Aplicar» pecha o panel antes de que cheguen os filtros novos. */
  private applying = false;
  protected readonly draftFrom = signal<IsoDate | null>(null);
  protected readonly draftTo = signal<IsoDate | null>(null);

  protected readonly datesShown = computed(() => this.filters().period === 'datas' || this.picking());
  protected readonly tooLong = computed(() => {
    const from = this.draftFrom();
    const to = this.draftTo();
    return !!from && !!to && rangeDays(from, to) > MAX_CUSTOM_DAYS;
  });
  /** A pista segue o paso: o primeiro día, o último, aplicar ou o rango longo de máis. */
  protected readonly datesHint = computed(() => {
    if (this.tooLong()) return 'tooLong';
    if (!this.draftFrom()) return 'datesFirst';
    return this.draftTo() ? 'datesReady' : 'datesLast';
  });
  protected readonly canApply = computed(() => !!this.draftFrom() && !!this.draftTo() && !this.tooLong());

  protected choose(period: PeriodKey): void {
    if (period !== 'datas') {
      this.picking.set(false);
      if (period !== this.filters().period) this.emit({ period, from: null, to: null });
      return;
    }
    this.draftFrom.set(this.filters().from);
    this.draftTo.set(this.filters().to);
    this.picking.set(true);
    afterNextRender(() => this.dates()?.open(), { injector: this.injector });
  }

  protected pick(day: IsoDate): void {
    const from = this.draftFrom();
    if (!from || this.draftTo() || day < from) {
      this.draftFrom.set(day);
      this.draftTo.set(null);
    } else {
      this.draftTo.set(day);
    }
  }

  protected apply(): void {
    const from = this.draftFrom();
    const to = this.draftTo();
    if (!from || !to || this.tooLong()) return;
    this.applying = true;
    this.emit({ period: 'datas', from, to });
    this.dates()?.close();
    this.applying = false;
  }

  /** Pechado sen aplicar e sen datas de antes: volve o período que había e o foco a «Datas…». */
  protected datesClosed(): void {
    if (this.applying || this.filters().period === 'datas') return;
    this.picking.set(false);
    const button = this.host.nativeElement.querySelector<HTMLButtonElement>('[data-period="datas"]');
    afterNextRender(() => button?.focus(), { injector: this.injector });
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
