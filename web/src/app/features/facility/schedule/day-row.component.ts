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
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { startWith, switchMap } from 'rxjs';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { FieldComponent } from '../../../shared/ui/field.component';
import { FieldActionComponent } from '../../../shared/ui/field-group/field-action.component';
import { FieldGroupComponent } from '../../../shared/ui/field-group/field-group.component';
import { TimePickerComponent } from '../../../shared/ui/time-picker/time-picker.component';
import { formatMinutes } from '../../../shared/ui/time-picker/time-format';
import { LanguageService } from '../../../core/i18n/language.service';
import type { DayForm } from './week-form';
import { INTERVALS_MAX, addInterval, dayError, removeInterval } from './week-form';

/**
 * Un día da semana en edición (especificación, bloque 2, «Semana»), na tarxeta da tempada
 * (`furar-facility-season-list`): o nome e o interruptor aberto/pechado á dereita do todo e, só
 * co día aberto, os tramos nun grupo de campos, unha fila «Abre | Pecha» cada un, coa
 * papeleira dentro do grupo se hai máis dun, e «Engadir tramo» ao centro; o erro do día debaixo.
 */
@Component({
  selector: 'furar-facility-day-row',
  imports: [
    ButtonDirective,
    IconComponent,
    ReactiveFormsModule,
    SwitchComponent,
    DeleteButtonComponent,
    FieldActionComponent,
    FieldComponent,
    FieldGroupComponent,
    TimePickerComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './day-row.component.html',
})
export class DayRowComponent {
  readonly day = input.required<DayForm>();
  /** Posición na semana (0 = luns, 7 = festivos). */
  readonly index = input.required<number>();
  /** «Luns», «Festivos»… */
  readonly name = input.required<string>();
  /** Prefixo dos `id` (un por semana). */
  readonly idPrefix = input.required<string>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  protected readonly max = INTERVALS_MAX;
  private readonly events = toSignal(toObservable(this.day).pipe(switchMap((day) => day.events.pipe(startWith(null)))));

  protected readonly value = computed(() => {
    this.events();
    return this.day().getRawValue();
  });
  protected readonly problem = computed(() => {
    this.events();
    return dayError(this.day());
  });
  protected readonly errorId = computed(() => `${this.idPrefix()}-day-${this.index()}-error`);
  private readonly lang = inject(LanguageService).lang;

  /** «9:00–14:00»: as horas dun tramo, para o nome da súa papeleira. */
  protected hours(index: number): string {
    const interval = this.value().intervals[index];
    if (!interval) return '';
    const at = (minutes: number | null) => (minutes === null ? '…' : formatMinutes(minutes, this.lang()));
    return `${at(interval.startMinute)}–${at(interval.endMinute)}`;
  }

  protected add(): void {
    addInterval(this.day());
    // Ao primeiro campo do novo tramo.
    this.focus(() =>
      [...this.host.nativeElement.querySelectorAll<HTMLElement>('[data-interval]')].at(-1)?.querySelector('button'),
    );
  }

  /** Quita o tramo e deixa o foco no anterior (ou en «+ Tramo» se non queda ningún). */
  protected remove(index: number): void {
    removeInterval(this.day(), index);
    this.focus(() => {
      const rows = this.host.nativeElement.querySelectorAll<HTMLElement>('[data-interval]');
      return (
        rows.item(Math.max(0, index - 1))?.querySelector<HTMLElement>('button') ??
        this.host.nativeElement.querySelector<HTMLElement>('[data-interval-add]')
      );
    });
  }

  private focus(target: () => HTMLElement | null | undefined): void {
    afterNextRender(() => target()?.focus(), { injector: this.injector });
  }
}
