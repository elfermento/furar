// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import {
  SegmentedRadioDirective,
  SegmentedOptionDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import type { DecisionForm, DecisionValue } from './affected-bookings-form';
import { channelsFor, problemOf } from './affected-bookings-form';
import type { AffectedBooking } from './affected-bookings.store';
import { formatBookingDay, formatBookingTime } from './booking-format';

/**
 * Unha reserva afectada (D1-19): `fieldset` cuxa lenda é o titular e «Lun 3 nov · 8:00–9:30 ·
 * Fútbol 11», casilla para as accións en bloque, cancelar/manter (segmentado radio) e, só ao
 * cancelar, as canles (correo e aviso na web) como casillas. O erro vese despois de «Confirmar».
 */
@Component({
  selector: 'furar-affected-booking',
  imports: [ReactiveFormsModule, TranslocoPipe, SegmentedDirective, SegmentedRadioDirective, SegmentedOptionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.affectedBookings.';
    <fieldset
      class="gap-unit grid px-item py-item"
      [formGroup]="form()"
      [attr.data-booking]="booking().bookingId"
      [attr.data-invalid]="error() ? '' : null"
      [attr.aria-describedby]="error() ? errorId() : null"
    >
      <legend class="float-left w-full">
        <label class="gap-item flex cursor-pointer items-start">
          <input
            type="checkbox"
            class="mt-px"
            formControlName="marked"
            [attr.aria-label]="t + 'mark' | transloco: { name: booking().holderName }"
          />
          <span class="grid min-w-0">
            <strong class="[overflow-wrap:anywhere]">{{ booking().holderName }}</strong>
            <span class="text-support text-ink-soft">{{ when() }}</span>
          </span>
        </label>
      </legend>
      <div class="gap-unit flex flex-wrap items-center sm:ps-10">
        <div
          furarSegmented
          mode="radio"
          formControlName="action"
          [attr.aria-label]="t + 'action' | transloco"
          [attr.aria-invalid]="error() === 'action' ? 'true' : null"
        >
          <button type="button" furarSegmentedOption="CANCEL">{{ t + 'cancel' | transloco }}</button>
          <button type="button" furarSegmentedOption="KEEP">{{ t + 'keep' | transloco }}</button>
        </div>
        @if (value().action === 'CANCEL') {
          <div
            class="gap-unit flex flex-wrap"
            role="group"
            formGroupName="channels"
            [attr.aria-label]="t + 'channels' | transloco"
          >
            @for (channel of offered(); track channel) {
              <label
                class="gap-unit inline-flex min-h-11 cursor-pointer items-center rounded-pill border border-line px-item has-checked:border-primary has-checked:bg-tint"
              >
                <input
                  type="checkbox"
                  class="size-5"
                  data-channel
                  [formControlName]="channel"
                  [attr.aria-invalid]="error() === 'channel' ? 'true' : null"
                />
                {{ t + 'channel.' + channel | transloco }}
              </label>
            }
          </div>
        } @else if (value().action === 'KEEP') {
          <p class="text-support text-ink-soft">{{ t + 'keepNoNotice' | transloco }}</p>
        }
      </div>
      @if (error(); as problem) {
        <p class="f-field-error sm:ps-10" [id]="errorId()">{{ t + 'error.' + problem | transloco }}</p>
      }
    </fieldset>
  `,
})
export class AffectedBookingComponent {
  private readonly language = inject(LanguageService);

  readonly booking = input.required<AffectedBooking>();
  readonly form = input.required<DecisionForm>();
  /** Valor actual do formulario (o diálogo pásao en cada cambio: as filas son OnPush). */
  readonly value = input.required<DecisionValue>();
  /** Despois do primeiro «Confirmar» vense os erros. */
  readonly submitted = input(false);

  protected readonly offered = computed(() => channelsFor(this.booking()));
  protected readonly errorId = computed(() => `affected-${this.booking().bookingId}-error`);
  protected readonly error = computed(() => (this.submitted() ? problemOf(this.value()) : null));
  protected readonly when = computed(() => {
    const b = this.booking();
    const lang = this.language.lang();
    return `${formatBookingDay(b.date, lang)} · ${formatBookingTime(b.startMinute, b.endMinute, lang)} · ${b.useName}`;
  });
}
