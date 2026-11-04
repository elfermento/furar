// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input, output, signal, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { CalendarComponent } from '../../../shared/ui/calendar/calendar.component';
import { FieldPopoverComponent } from '../../../shared/ui/field-popover/field-popover.component';
import { FilterBarComponent, FilterFieldComponent } from '../../../shared/ui/filter-bar/filter-bar.component';
import { FilterChipDirective } from '../../../shared/ui/filter-chip/filter-chip.directive';
import type { SelectOption } from '../../../shared/ui/select/select.component';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import type { ActivityArea, ActivityFilters, ActivityScope } from './activity';
import { AREAS, MAX_RANGE_DAYS, NO_FILTERS, hasFilters, rangeDays } from './activity';

/**
 * Filtros do rexistro (D4-16), na fila de filtros común (`furar-filter-bar`): período (calendario
 * de rango nun `furar-field-popover`: primeiro clic, o inicio; o segundo, o fin; «Aplicar» cámbiao
 * e «Ver o último ano» volve ao de por defecto), área e persoa (`furar-select`) e «Só fallidos»
 * (`[furarFilterChip]` co ton `danger`, o do estado «Fallou»). A plataforma, só o período. Non
 * garda nada: emite os filtros novos e a páxina lévaos ao URL.
 */
@Component({
  selector: 'furar-activity-filters',
  imports: [
    TranslocoPipe,
    ButtonDirective,
    CalendarComponent,
    FieldPopoverComponent,
    FilterBarComponent,
    FilterChipDirective,
    FilterFieldComponent,
    SelectComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-activity__filters' },
  template: `
    @let t = 'activity.filters.';
    <furar-filter-bar [label]="label()">
      <furar-filter-field [label]="t + 'period' | transloco" controlId="activity-period">
        <furar-field-popover
          #dates
          data-period
          look="filter"
          triggerId="activity-period"
          [label]="t + 'period' | transloco"
          [value]="periodText()"
          [placeholder]="t + 'lastYear' | transloco"
          (opened)="startPicking()"
        >
          <div class="f-activity__dates">
            <furar-calendar
              range
              [value]="draftFrom()"
              [rangeEnd]="draftTo()"
              [today]="today()"
              [min]="min()"
              [max]="today()"
              (picked)="pick($event)"
            />
            <p class="f-activity__dates-hint" aria-live="polite" [attr.data-error]="tooLong() || null">
              {{ t + hint() | transloco: { max: maxDays } }}
            </p>
            <div class="f-activity__dates-actions">
              <button
                type="button"
                furarButton="primary"
                size="sm"
                data-period-apply
                [disabled]="!canApply()"
                (click)="apply()"
              >
                {{ t + 'apply' | transloco }}
              </button>
              @if (filters().from) {
                <button type="button" furarButton="quiet" size="sm" data-period-clear (click)="clearPeriod()">
                  {{ t + 'lastYearAction' | transloco }}
                </button>
              }
            </div>
          </div>
        </furar-field-popover>
      </furar-filter-field>
      @if (scope() === 'municipality') {
        <furar-filter-field [label]="t + 'area' | transloco" controlId="activity-area">
          <furar-select
            data-area
            selectId="activity-area"
            [label]="t + 'area' | transloco"
            [options]="areas()"
            [value]="filters().area ?? ''"
            (changed)="setArea($event)"
          />
        </furar-filter-field>
        <furar-filter-field [label]="t + 'person' | transloco" controlId="activity-person">
          <furar-select
            data-person
            selectId="activity-person"
            [label]="t + 'person' | transloco"
            [options]="people()"
            [value]="filters().memberId ?? ''"
            (changed)="setPerson($event)"
          />
        </furar-filter-field>
        <button
          type="button"
          furarFilterChip="danger"
          data-failed
          [pressed]="filters().failedOnly"
          (pressedChange)="emit({ failedOnly: $event })"
        >
          {{ t + 'failedOnly' | transloco }}
        </button>
      }
      @if (active()) {
        <button type="button" furarButton="quiet" size="sm" data-reset (click)="changed.emit(none)">
          {{ t + 'reset' | transloco }}
        </button>
      }
    </furar-filter-bar>
  `,
})
export class ActivityFiltersComponent {
  private readonly dates = viewChild.required<FieldPopoverComponent>('dates');

  readonly filters = input.required<ActivityFilters>();
  readonly scope = input.required<ActivityScope>();
  /** O período escollido en texto («01/10/2026 – 28/10/2026»), xa formatado pola páxina. */
  readonly periodText = input<string | null>(null);
  /** «Todas» e as áreas, xa traducidas. */
  readonly areas = input<readonly SelectOption<string>[]>([]);
  /** «Todas» e as persoas do persoal, xa traducidas. */
  readonly people = input<readonly SelectOption<string>[]>([]);
  readonly today = input.required<IsoDate>();
  /** O primeiro día que aínda se ve (o anterior está bloqueado): o calendario non vai máis atrás. */
  readonly min = input<IsoDate | null>(null);
  readonly label = input.required<string>();

  readonly changed = output<ActivityFilters>();

  protected readonly none = NO_FILTERS;
  protected readonly maxDays = String(MAX_RANGE_DAYS);
  protected readonly draftFrom = signal<IsoDate | null>(null);
  protected readonly draftTo = signal<IsoDate | null>(null);
  protected readonly active = computed(() => hasFilters(this.filters()));
  protected readonly tooLong = computed(() => {
    const from = this.draftFrom();
    const to = this.draftTo();
    return !!from && !!to && rangeDays(from, to) > MAX_RANGE_DAYS;
  });
  protected readonly hint = computed(() => {
    if (this.tooLong()) return 'tooLong';
    if (!this.draftFrom()) return 'datesFirst';
    return this.draftTo() ? 'datesReady' : 'datesLast';
  });
  protected readonly canApply = computed(() => !!this.draftFrom() && !!this.draftTo() && !this.tooLong());

  /** Ao abrir, o borrador parte do período que hai. */
  protected startPicking(): void {
    this.draftFrom.set(this.filters().from);
    this.draftTo.set(this.filters().to);
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
    this.emit({ from, to });
    this.dates().close();
  }

  protected clearPeriod(): void {
    this.emit({ from: null, to: null });
    this.dates().close();
  }

  protected setArea(value: string): void {
    const area = (AREAS.find((a) => a === value) ?? null) as ActivityArea | null;
    if (area !== this.filters().area) this.emit({ area });
  }

  protected setPerson(value: string): void {
    const memberId = value || null;
    if (memberId !== this.filters().memberId) this.emit({ memberId });
  }

  protected emit(change: Partial<ActivityFilters>): void {
    this.changed.emit({ ...this.filters(), ...change });
  }
}
