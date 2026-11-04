// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  model,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { CalendarComponent } from '../../../shared/ui/calendar/calendar.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { DatePreset } from './manage-bookings';
import { dateFilterText, pickDay, presetRange } from './manage-bookings';

let nextId = 0;

/**
 * «Datas» nos filtros (maqueta 26): sen datas, a ligazón «Seleccionar data»; con elas, un chip co
 * rango («Hoxe», «Do 26/10 ao 31/10») que volve abrir o panel e unha ✕ para quitalo. O panel ten
 * os atallos (Hoxe · Mañá · Esta semana), o calendario de rango (primeiro clic, o inicio; o
 * segundo, o fin) e «Aplicar». Pecha con «Aplicar», Esc (só el, non o diálogo) ou picando fóra; o
 * foco volve ao campo.
 */
@Component({
  selector: 'furar-booking-date-filter',
  imports: [ButtonDirective, CalendarComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-mdate' },
  template: `
    @let t = 'booking.manage.filters.dates.';
    <div class="f-mdate__row">
      @if (text(); as label) {
        <span class="f-mdate__chip" data-date-chip>
          <button
            #trigger
            type="button"
            class="f-mdate__chip-open"
            [attr.aria-expanded]="open()"
            [attr.aria-controls]="panelId"
            (click)="toggle()"
          >
            <furar-icon name="calendar-month" />
            <span>{{ t + label.key | transloco: label.params }}</span>
          </button>
          <button
            type="button"
            class="f-mdate__chip-clear"
            data-date-clear
            [attr.aria-label]="t + 'clear' | transloco"
            (click)="clear()"
          >
            <furar-icon name="close" />
          </button>
        </span>
      } @else {
        <button
          #trigger
          type="button"
          class="f-mdate__add"
          data-date-add
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="panelId"
          (click)="toggle()"
        >
          <furar-icon name="calendar-month" />
          {{ t + 'add' | transloco }}
        </button>
      }
    </div>
    @if (open()) {
      <div
        class="f-mdate__panel"
        role="group"
        data-date-panel
        [id]="panelId"
        [attr.aria-label]="t + 'panel' | transloco"
        (keydown.escape)="escape($event)"
      >
        <div class="f-mdate__presets" role="group" [attr.aria-label]="t + 'presets' | transloco">
          @for (preset of presets; track preset) {
            <button
              type="button"
              class="f-mdate__preset"
              [attr.data-preset]="preset"
              [attr.aria-pressed]="isPreset(preset)"
              (click)="applyPreset(preset)"
            >
              {{ t + 'preset.' + preset | transloco }}
            </button>
          }
        </div>
        <furar-calendar range [value]="from()" [rangeEnd]="to()" [today]="today()" (picked)="pick($event)" />
        <div class="f-mdate__ok">
          <button type="button" furarButton="primary" size="sm" data-date-apply (click)="close()">
            {{ t + 'apply' | transloco }}
          </button>
        </div>
      </div>
    }
  `,
})
export class DateFilterComponent {
  readonly from = model<IsoDate | null>(null);
  readonly to = model<IsoDate | null>(null);
  readonly today = input.required<IsoDate>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');

  protected readonly presets: readonly DatePreset[] = ['today', 'tomorrow', 'week'];
  protected readonly panelId = `furar-date-filter-${nextId++}`;
  protected readonly open = signal(false);
  protected readonly text = computed(() => dateFilterText(this.from(), this.to(), this.today()));

  constructor() {
    // Picar fóra (no diálogo ou fóra del) pecha o panel, como toda capa.
    const document = inject(DOCUMENT);
    const onDown = (event: Event) => {
      if (this.open() && !this.host.nativeElement.contains(event.target as Node)) this.open.set(false);
    };
    document.addEventListener('mousedown', onDown, true);
    inject(DestroyRef).onDestroy(() => document.removeEventListener('mousedown', onDown, true));
  }

  protected toggle(): void {
    this.open.update((value) => !value);
  }

  protected isPreset(preset: DatePreset): boolean {
    const range = presetRange(preset, this.today());
    return this.from() === range.from && this.to() === range.to;
  }

  protected applyPreset(preset: DatePreset): void {
    const range = presetRange(preset, this.today());
    this.from.set(range.from);
    this.to.set(range.to);
  }

  protected pick(day: IsoDate): void {
    const range = pickDay({ from: this.from(), to: this.to() }, day);
    this.from.set(range.from);
    this.to.set(range.to);
  }

  protected clear(): void {
    this.from.set(null);
    this.to.set(null);
    this.open.set(false);
    afterNextRender(() => this.trigger()?.nativeElement.focus(), { injector: this.injector });
  }

  /** «Aplicar»: pecha e o foco volve ao campo (as datas xa están postas). */
  protected close(): void {
    this.open.set(false);
    afterNextRender(() => this.trigger()?.nativeElement.focus(), { injector: this.injector });
  }

  /** Esc pecha só o panel, non o diálogo dos filtros. */
  protected escape(event: Event): void {
    event.stopPropagation();
    event.preventDefault();
    this.close();
  }
}
