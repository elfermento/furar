// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../../core/errors/api-problem';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../../shared/ui/calendar/local-date';
import { formatShort } from '../../../../shared/ui/calendar/local-date';
import { DateFieldComponent } from '../../../../shared/ui/date-field/date-field.component';
import { DeleteButtonComponent } from '../../../../shared/ui/delete-button/delete-button.component';
import { DialogComponent } from '../../../../shared/ui/dialog/dialog.component';
import { focusFirstError } from '../../../../shared/ui/edit-section/section-errors';
import { describedBy, fieldError } from '../../../../shared/ui/field-errors';
import { FieldComponent } from '../../../../shared/ui/field.component';
import { InputDirective } from '../../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../../shared/ui/problem-alert.component';
import { SwitchComponent } from '../../../../shared/ui/switch/switch.component';
import { TimePickerComponent } from '../../../../shared/ui/time-picker/time-picker.component';
import { REASON_MAX, applyExceptionErrors, exceptionBody, exceptionForm } from './exception-form';
import { LanguageService } from '../../../../core/i18n/language.service';
import { CalloutComponent } from '../../../../shared/ui/callout/callout.component';
import type { HolidayName } from '../schedule.store';
import type { ExceptionBody, ScheduleException } from './exceptions.store';
import { RequiredMarkComponent, RequiredNoteComponent } from '../../../../shared/ui/required/required.component';

export interface ExceptionDialogData {
  /** A excepción tal como se ve agora (cos cambios sen gardar); sen ela, unha nova. */
  readonly exception: ScheduleException | null;
  /** Datas doutras excepcións: non se pode repetir. */
  readonly taken: ReadonlySet<IsoDate>;
  readonly today: IsoDate | null;
  readonly problem: ApiProblem | null;
  /** O festivo dese día, para avisar (sen bloquear) de que a excepción cambia o seu horario. */
  readonly holiday?: (date: IsoDate) => Promise<HolidayName | null>;
}

export type ExceptionDialogResult =
  { readonly kind: 'apply'; readonly body: ExceptionBody } | { readonly kind: 'remove' };

let nextId = 0;

/**
 * Modal dunha excepción no modo edición da ficha (D1-23b; especificación, bloque 2): data con
 * `furar-date-field`, interruptor aberto/pechado (á vista, o estado: «Pechado» ou «Aberto» e o
 * tramo; maqueta 54), o tramo se está aberta e o motivo. Se o día é festivo, unha nota debaixo
 * di que a excepción cambia o seu horario, sen bloquear. Valida igual
 * ca a API (data obrigatoria e sen repetir, fin despois do inicio, motivo de ata 200). «Aceptar»
 * só deixa o cambio na ficha; vai á API cando a barra garda.
 */
@Component({
  selector: 'furar-facility-exception-dialog',
  imports: [
    RequiredMarkComponent,
    RequiredNoteComponent,
    ButtonDirective,
    CalloutComponent,
    DateFieldComponent,
    DeleteButtonComponent,
    DialogComponent,
    FieldComponent,
    InputDirective,
    ProblemAlertComponent,
    ReactiveFormsModule,
    SwitchComponent,
    TimePickerComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './exception-dialog.component.html',
})
export class ExceptionDialogComponent {
  protected readonly data = inject<ExceptionDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<ExceptionDialogResult>>(DialogRef);
  private readonly transloco = inject(TranslocoService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly id = `furar-exception-${nextId++}`;
  protected readonly reasonMax = REASON_MAX;
  protected readonly describedBy = describedBy;
  protected readonly today = signal(this.data.today);
  protected readonly form = exceptionForm(this.data.exception, () => this.data.taken);
  protected readonly problem = signal<ApiProblem | null>(null);
  private readonly formEvents = signal(0);
  private readonly lang = inject(LanguageService).lang;
  private readonly holidayOf = signal<{ date: string; name: HolidayName } | null>(null);

  protected readonly value = computed(() => {
    this.formEvents();
    return this.form.getRawValue();
  });
  protected readonly errors = computed(() => {
    this.formEvents();
    const c = this.form.controls;
    return {
      date: fieldError(c.date, false),
      interval: fieldError(c.startMinute, false) ?? fieldError(c.endMinute, false) ?? fieldError(this.form, false),
      reason: fieldError(c.reason, false),
    };
  });
  /** Nome do festivo do día escollido, no idioma da web, ou `null`. */
  protected readonly holidayName = computed(() => {
    const date = this.value().date;
    const found = this.holidayOf();
    if (!date || !found || found.date !== date) return null;
    return this.lang() === 'es' ? found.name.es : found.name.gl;
  });
  protected readonly day = computed(() => (this.data.exception ? formatShort(this.data.exception.date) : ''));
  protected readonly heading = computed(() =>
    this.data.exception
      ? this.transloco.translate('facility.schedule.exceptions.edit', { day: this.day() })
      : this.transloco.translate('facility.schedule.exceptions.addTitle'),
  );

  constructor() {
    this.form.events.subscribe(() => this.formEvents.update((n) => n + 1));
    let asked: string | null = null;
    effect(() => {
      const date = this.value().date;
      const lookup = this.data.holiday;
      if (!date || !lookup || date === asked) return;
      asked = date;
      void lookup(date).then((name) => {
        if (this.form.controls.date.value === date) this.holidayOf.set(name ? { date, name } : null);
      });
    });
    // Os erros da API van aos campos xa ligados (ligalos revalida e borraría os postos antes).
    const problem = this.data.problem;
    if (problem) {
      afterNextRender(
        () => {
          if (!applyExceptionErrors(this.form, problem)) this.problem.set(problem);
          this.formEvents.update((n) => n + 1);
          focusFirstError(this.host.nativeElement);
        },
        { injector: this.injector },
      );
    }
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.problem.set(null);
    this.form.updateValueAndValidity();
    this.form.markAllAsTouched();
    this.formEvents.update((n) => n + 1);
    if (this.form.invalid) return this.focusError();
    this.ref.close({ kind: 'apply', body: exceptionBody(this.form) });
  }

  protected remove(): void {
    this.ref.close({ kind: 'remove' });
  }

  private focusError(): void {
    afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
  }
}
