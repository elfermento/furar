// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { MultiComboboxComponent } from '../../../shared/ui/multi-combobox/multi-combobox.component';
import { FilterChipDirective } from '../../../shared/ui/filter-chip/filter-chip.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { DateFilterComponent } from '../../../shared/ui/date-filter/date-filter.component';
import type { BookingFilters, BookingSort, FacilityChoice, PaymentStatus, StateFilter } from './manage-bookings';
import { NO_FILTERS, PAYMENT_OPTIONS, STATE_OPTIONS, facilityPicker, toggle } from './manage-bookings';

export interface BookingFiltersData {
  readonly filters: BookingFilters;
  readonly facilities: readonly FacilityChoice[];
  readonly today: IsoDate;
}

let nextId = 0;

/**
 * «Filtros» das reservas: Ordenar (segmentado), Datas (`furar-date-filter`), Instalación
 * (`furar-multi-combobox`: busca polo nome e as escollidas en chips que se quitan), e Pago e Estado
 * con `[furarFilterChip]` (sen marcar ao abrir, co ton da paleta); en Pago, «Marcar todos»/«Desmarcar
 * todos», que segue o estado dos chips. «Quitar filtros»
 * baléirao todo; «Ver reservas» pecha cos filtros escollidos. No móbil, folla desde abaixo
 * (`sheet`). Pechar doutro xeito non cambia nada.
 */
@Component({
  selector: 'furar-booking-filters-dialog',
  imports: [
    ButtonDirective,
    DateFilterComponent,
    DialogComponent,
    FilterChipDirective,
    MultiComboboxComponent,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.manage.filters.';
    <furar-dialog [heading]="t + 'title' | transloco">
      <div class="f-mfilters">
        <!-- Todos os grupos co mesmo rótulo e a mesma aliñación: o rótulo enriba, o control debaixo. -->
        <div class="f-mfilters__group">
          <p class="f-mfilters__label" [id]="ids.sort">{{ t + 'sort' | transloco }}</p>
          <div furarSegmented mode="radio" [attr.aria-labelledby]="ids.sort" [(value)]="sort">
            <button type="button" furarSegmentedOption="RECENT">{{ t + 'sortRecent' | transloco }}</button>
            <button type="button" furarSegmentedOption="START">{{ t + 'sortStart' | transloco }}</button>
          </div>
        </div>

        <div class="f-mfilters__group">
          <p class="f-mfilters__label">{{ t + 'dates.label' | transloco }}</p>
          <furar-date-filter [(from)]="from" [(to)]="to" [today]="data.today" />
        </div>

        @if (data.facilities.length) {
          <furar-multi-combobox
            class="f-mfilters__group"
            data-filter="facility"
            highlight
            debounce="0"
            [fieldId]="ids.facility"
            [label]="t + 'facility' | transloco"
            [chosenLabel]="t + 'facilityChosen' | transloco"
            [source]="facilities.source"
            [key]="facilities.key"
            [display]="facilities.display"
            [countText]="facilities.countText"
            [(selected)]="facilityIds"
          />
        }

        <div class="f-mfilters__group">
          <p class="f-mfilters__label" [id]="ids.payment">{{ t + 'payment' | transloco }}</p>
          <div class="f-mfilters__chips" role="group" [attr.aria-labelledby]="ids.payment" data-filter="payment">
            @for (option of paymentOptions; track option.value) {
              <button
                type="button"
                [furarFilterChip]="option.tone"
                [attr.data-value]="option.value"
                [pressed]="payments().includes(option.value)"
                (pressedChange)="togglePayment(option.value)"
              >
                {{ t + 'paymentOption.' + option.value | transloco }}
              </button>
            }
            <button type="button" class="f-mfilters__all" data-all="payment" (click)="allPayments()">
              {{ t + (allPaymentsOn() ? 'noneMale' : 'allMale') | transloco }}
            </button>
          </div>
        </div>

        <div class="f-mfilters__group">
          <p class="f-mfilters__label" [id]="ids.state">{{ t + 'state' | transloco }}</p>
          <div class="f-mfilters__chips" role="group" [attr.aria-labelledby]="ids.state" data-filter="state">
            @for (option of stateOptions; track option.value) {
              <button
                type="button"
                [furarFilterChip]="option.tone"
                [attr.data-value]="option.value"
                [pressed]="states().includes(option.value)"
                (pressedChange)="toggleState(option.value)"
              >
                {{ t + 'stateOption.' + option.value | transloco }}
              </button>
            }
          </div>
        </div>
      </div>

      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-filters-clear (click)="clear()">
          {{ t + 'clear' | transloco }}
        </button>
        <button type="button" furarButton="primary" data-filters-apply (click)="apply()">
          {{ t + 'apply' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class BookingFiltersDialogComponent {
  protected readonly data = inject<BookingFiltersData>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<BookingFilters>>(DialogRef);

  private readonly id = nextId++;
  private readonly transloco = inject(TranslocoService);
  protected readonly ids = {
    sort: `furar-filters-${this.id}-sort`,
    facility: `furar-filters-${this.id}-facility`,
    payment: `furar-filters-${this.id}-payment`,
    state: `furar-filters-${this.id}-state`,
  };
  protected readonly facilities = facilityPicker(this.data.facilities, (key, params) =>
    this.transloco.translate(key, params),
  );
  protected readonly paymentOptions = PAYMENT_OPTIONS;
  protected readonly stateOptions = STATE_OPTIONS;
  private readonly paymentOrder = PAYMENT_OPTIONS.map((o) => o.value);
  private readonly stateOrder = STATE_OPTIONS.map((o) => o.value);

  protected readonly sort = signal<BookingSort>(this.data.filters.sort);
  protected readonly from = signal<IsoDate | null>(this.data.filters.from);
  protected readonly to = signal<IsoDate | null>(this.data.filters.to);
  protected readonly facilityIds = signal<readonly string[]>(this.data.filters.facilityIds);
  protected readonly payments = signal<readonly PaymentStatus[]>(this.data.filters.payments);
  protected readonly states = signal<readonly StateFilter[]>(this.data.filters.states);

  protected readonly allPaymentsOn = computed(() => this.payments().length === this.paymentOrder.length);

  protected togglePayment(value: PaymentStatus): void {
    this.payments.update((list) => toggle(list, value, this.paymentOrder));
  }

  protected toggleState(value: StateFilter): void {
    this.states.update((list) => toggle(list, value, this.stateOrder));
  }

  protected allPayments(): void {
    this.payments.set(this.allPaymentsOn() ? [] : this.paymentOrder);
  }

  protected clear(): void {
    this.sort.set(NO_FILTERS.sort);
    this.from.set(null);
    this.to.set(null);
    this.facilityIds.set([]);
    this.payments.set([]);
    this.states.set([]);
  }

  protected apply(): void {
    this.ref.close({
      sort: this.sort(),
      from: this.from(),
      to: this.from() ? this.to() : null,
      facilityIds: this.facilityIds(),
      payments: this.payments(),
      states: this.states(),
    });
  }
}
