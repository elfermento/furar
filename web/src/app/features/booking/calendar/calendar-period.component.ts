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
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { CalendarComponent } from '../../../shared/ui/calendar/calendar.component';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays, startOfWeek } from '../../../shared/ui/calendar/local-date';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { CalendarMode } from './bookings-calendar';
import { step } from './bookings-calendar';

let nextId = 0;

/**
 * Barra do período (maqueta 27): frechas sen círculo a cada lado e, no medio, o texto do período
 * (día, semana ou mes) que abre un calendario en capa coas súas propias frechas de mes. No día
 * márcase o día; na semana, a semana enteira; no mes, ningún (ábrese nese mes). Escoller un día
 * leva a el (a súa semana, o seu mes); a capa pecha ao escoller, con Esc (o foco volve ao texto) e
 * picando fóra. Sen límites: calquera día, semana ou mes.
 */
@Component({
  selector: 'furar-calendar-period',
  imports: [CalendarComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-bcal__bar' },
  template: `
    @let t = 'booking.calendar.';
    <button
      type="button"
      class="f-bcal__arr"
      data-period-prev
      [attr.aria-label]="t + 'previous.' + mode() | transloco"
      (click)="picked.emit(move(-1))"
    >
      <furar-icon name="arrow-left" />
    </button>
    <button
      #trigger
      type="button"
      class="f-bcal__label"
      data-period-label
      aria-haspopup="dialog"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="panelId"
      (click)="toggle()"
    >
      <furar-icon name="calendar-month" />
      <span>{{ label() }}</span>
    </button>
    <button
      type="button"
      class="f-bcal__arr"
      data-period-next
      [attr.aria-label]="t + 'next.' + mode() | transloco"
      (click)="picked.emit(move(1))"
    >
      <furar-icon name="arrow-right" />
    </button>
    @if (open()) {
      <div
        class="f-bcal__pop"
        role="dialog"
        data-period-pop
        [id]="panelId"
        [attr.aria-label]="t + 'pick' | transloco"
        (keydown.escape)="escape($event)"
      >
        @if (mode() === 'WEEK') {
          <furar-calendar
            range
            [value]="week().from"
            [rangeEnd]="week().to"
            [today]="today()"
            (picked)="pick($event)"
          />
        } @else {
          <furar-calendar
            [value]="mode() === 'DAY' ? date() : null"
            [month]="date()"
            [today]="today()"
            (picked)="pick($event)"
          />
        }
      </div>
    }
  `,
})
export class CalendarPeriodComponent {
  readonly mode = input.required<CalendarMode>();
  readonly date = input.required<IsoDate>();
  readonly today = input.required<IsoDate>();
  /** O texto do período («luns, 26 de outubro»). */
  readonly label = input.required<string>();
  /** A data nova: unha frecha ou un día do calendario. */
  readonly picked = output<IsoDate>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly calendar = viewChild(CalendarComponent);

  protected readonly panelId = `furar-calendar-period-${nextId++}`;
  protected readonly open = signal(false);
  protected readonly week = computed(() => {
    const from = startOfWeek(this.date());
    return { from, to: addDays(from, 6) };
  });

  constructor() {
    const document = inject(DOCUMENT);
    const onDown = (event: Event) => {
      if (this.open() && !this.host.nativeElement.contains(event.target as Node)) this.open.set(false);
    };
    document.addEventListener('mousedown', onDown, true);
    inject(DestroyRef).onDestroy(() => document.removeEventListener('mousedown', onDown, true));
  }

  protected move(delta: number): IsoDate {
    return step(this.mode(), this.date(), delta);
  }

  protected toggle(): void {
    this.open.update((value) => !value);
    if (this.open()) afterNextRender(() => this.calendar()?.focusActive(), { injector: this.injector });
  }

  protected pick(day: IsoDate): void {
    this.picked.emit(day);
    this.close();
  }

  protected escape(event: Event): void {
    event.stopPropagation();
    event.preventDefault();
    this.close();
  }

  private close(): void {
    this.open.set(false);
    afterNextRender(() => this.trigger().nativeElement.focus(), { injector: this.injector });
  }
}
