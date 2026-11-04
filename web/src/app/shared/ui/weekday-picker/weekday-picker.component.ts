// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, forwardRef, inject, input, signal } from '@angular/core';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { weekdayNames } from '../calendar/local-date';

/** Días da semana como máscara de bits, luns = 1 … domingo = 64 (o `weekdays` da API). */
export type WeekdayMask = number;

export const ALL_WEEKDAYS: WeekdayMask = 0b1111111;
export const MONDAY_TO_FRIDAY: WeekdayMask = 0b0011111;
export const WEEKEND: WeekdayMask = 0b1100000;

const SHORTCUTS = [
  { key: 'all', mask: ALL_WEEKDAYS },
  { key: 'workdays', mask: MONDAY_TO_FRIDAY },
  { key: 'weekend', mask: WEEKEND },
] as const;

/**
 * Días da semana (D2-10; maqueta 21): sete botóns de luns a domingo coa inicial por `Intl` no
 * idioma activo («L M M X V S D») e, enriba, os atallos «Todos», «Luns a venres» e «Fins de
 * semana» (marcado o que coincide). Cada día é un `role="checkbox"` co nome enteiro («luns»).
 * O valor (ControlValueAccessor) é a máscara de bits; non valida: «ningún día» é un erro que pon
 * quen o usa. Estilos en `styles/xestion.css` (só o usa a xestión).
 */
@Component({
  selector: 'furar-weekday-picker',
  imports: [TranslocoPipe],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => WeekdayPickerComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-weekdays', role: 'group', '[attr.aria-label]': 'label()' },
  template: `
    <div class="f-weekdays__shortcuts" role="group" [attr.aria-label]="'ui.weekdayPicker.shortcuts' | transloco">
      @for (shortcut of shortcuts; track shortcut.key) {
        <button
          type="button"
          class="f-weekdays__shortcut"
          [attr.aria-pressed]="mask() === shortcut.mask"
          [disabled]="isDisabled()"
          (click)="set(shortcut.mask)"
        >
          {{ 'ui.weekdayPicker.' + shortcut.key | transloco }}
        </button>
      }
    </div>
    <div class="f-weekdays__days">
      @for (day of days(); track $index) {
        <button
          type="button"
          role="checkbox"
          class="f-weekdays__day"
          [attr.aria-checked]="isOn($index)"
          [attr.aria-label]="day.long"
          [disabled]="isDisabled()"
          (click)="toggle($index)"
        >
          {{ day.short }}
        </button>
      }
    </div>
  `,
})
export class WeekdayPickerComponent implements ControlValueAccessor {
  private readonly language = inject(LanguageService);

  /** Nome do grupo («Días»). */
  readonly label = input.required<string>();

  protected readonly shortcuts = SHORTCUTS;
  protected readonly days = computed(() => weekdayNames(this.language.lang()));
  protected readonly mask = signal<WeekdayMask>(0);
  private readonly disabledByForm = signal(false);
  protected readonly isDisabled = this.disabledByForm.asReadonly();

  private onChange: (value: WeekdayMask) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: unknown): void {
    this.mask.set(typeof value === 'number' && Number.isInteger(value) ? value & ALL_WEEKDAYS : 0);
  }

  registerOnChange(fn: (value: WeekdayMask) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabledByForm.set(disabled);
  }

  protected isOn(index: number): boolean {
    return (this.mask() & (1 << index)) !== 0;
  }

  protected toggle(index: number): void {
    this.set(this.mask() ^ (1 << index));
  }

  protected set(mask: WeekdayMask): void {
    this.onTouched();
    if (mask !== this.mask()) {
      this.mask.set(mask);
      this.onChange(mask);
    }
  }
}
