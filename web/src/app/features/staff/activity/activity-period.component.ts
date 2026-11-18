// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { DateFieldComponent } from '../../../shared/ui/date-field/date-field.component';
import { focusFirstError } from '../../../shared/ui/focus-error/focus-error';
import { MAX_RANGE_DAYS } from './activity';
import { rangeDays } from './activity-filters';

/** O período escollido á man: os dous días, incluídos. */
export interface ActivityPeriod {
  readonly from: IsoDate;
  readonly to: IsoDate;
}

let nextId = 0;

/**
 * «Desde» e «Ata» dentro do filtro da columna Data: dous campos de data e «Aplicar»,
 * que aplica cos dous días e un período de como moito {@link MAX_RANGE_DAYS} días. Sempre activo:
 * premido sen un día ou cun período longo de máis, o erro sae debaixo do campo e o foco vai a el. O
 * calendario non vai antes do primeiro día visible (`min`) nin despois de hoxe.
 */
@Component({
  selector: 'furar-activity-period',
  imports: [ButtonDirective, DateFieldComponent, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-activity__period' },
  template: `
    @let t = 'activity.filters.';
    <div class="f-activity__period-fields">
      <div class="grid content-start">
        <furar-date-field
          data-period-from
          [formControl]="fromControl"
          [label]="t + 'from' | transloco"
          [min]="min()"
          [max]="today()"
          [today]="today()"
          [invalid]="missingFrom()"
          [describedBy]="missingFrom() ? ids + '-from-error' : null"
        />
        @if (missingFrom()) {
          <span class="f-field-error" data-period-from-error [id]="ids + '-from-error'">{{
            t + 'fromRequired' | transloco
          }}</span>
        }
      </div>
      <div class="grid content-start">
        <furar-date-field
          data-period-to
          [formControl]="toControl"
          [label]="t + 'to' | transloco"
          [min]="fromValue() ?? min()"
          [max]="today()"
          [today]="today()"
          [invalid]="missingTo() || tooLong()"
          [describedBy]="missingTo() ? ids + '-to-error' : tooLong() ? ids + '-too-long' : null"
        />
        @if (missingTo()) {
          <span class="f-field-error" data-period-to-error [id]="ids + '-to-error'">{{
            t + 'toRequired' | transloco
          }}</span>
        }
      </div>
    </div>
    @if (tooLong()) {
      <p class="f-activity__period-hint" data-error aria-live="polite" [id]="ids + '-too-long'">
        {{ t + 'tooLong' | transloco: { max: maxDays } }}
      </p>
    }
    <!-- Sempre activo: sen un día, o erro sae debaixo do campo e o foco vai a el. -->
    <button type="button" furarButton size="sm" data-period-apply (click)="apply()">
      {{ t + 'apply' | transloco }}
    </button>
  `,
})
export class ActivityPeriodComponent {
  readonly from = input<IsoDate | null>(null);
  readonly to = input<IsoDate | null>(null);
  readonly today = input.required<IsoDate>();
  readonly min = input<IsoDate | null>(null);

  readonly changed = output<ActivityPeriod>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  protected readonly ids = `furar-activity-period-${nextId++}`;
  protected readonly maxDays = String(MAX_RANGE_DAYS);
  /** Premeuse «Aplicar»: os días que faltan vense como erro. */
  private readonly attempted = signal(false);
  protected readonly fromControl = new FormControl<IsoDate | null>(null);
  protected readonly toControl = new FormControl<IsoDate | null>(null);
  protected readonly fromValue = toSignal(this.fromControl.valueChanges, { initialValue: null });
  private readonly toValue = toSignal(this.toControl.valueChanges, { initialValue: null });

  protected readonly tooLong = computed(() => {
    const from = this.fromValue();
    const to = this.toValue();
    return !!from && !!to && (to < from || rangeDays(from, to) > MAX_RANGE_DAYS);
  });
  protected readonly missingFrom = computed(() => this.attempted() && !this.fromValue());
  protected readonly missingTo = computed(() => this.attempted() && !this.toValue());

  constructor() {
    // Os campos parten do período que hai.
    effect(() => {
      this.fromControl.setValue(this.from());
      this.toControl.setValue(this.to());
    });
  }

  protected apply(): void {
    const from = this.fromControl.value;
    const to = this.toControl.value;
    if (from && to && !this.tooLong()) {
      this.changed.emit({ from, to });
      return;
    }
    this.attempted.set(true);
    afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
  }
}
