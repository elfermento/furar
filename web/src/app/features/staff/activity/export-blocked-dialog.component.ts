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
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays, formatShort } from '../../../shared/ui/calendar/local-date';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DateFieldComponent } from '../../../shared/ui/date-field/date-field.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { countInvalid, describedBy, fieldError, focusFirstInvalid } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { FormErrorsSummaryComponent } from '../../../shared/ui/form-errors-summary.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { OptionsPipe } from '../../../shared/ui/select/options.pipe';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import { MAX_RANGE_DAYS, madridMidnight, rangeDays } from './activity';
import type { DisclosureReason } from './activity-retention';
import { DISCLOSURE_REASONS, REFERENCE_MAX, validReference } from './activity-retention';
import type { BlockedActivityExportResponse, ExportBlockedActivityRequest } from './activity.store';
import { RequiredMarkComponent, RequiredNoteComponent } from '../../../shared/ui/required/required.component';

export interface ExportBlockedData {
  /** O primeiro e o último día que se poden escoller (o tramo bloqueado, Europe/Madrid). */
  readonly first: IsoDate;
  readonly last: IsoDate;
  /** Exporta; resolve cos eventos e rexeita co erro da API. */
  readonly export: (body: ExportBlockedActivityRequest) => Promise<BlockedActivityExportResponse>;
}

/** Os eventos exportados, ou `undefined` se se pechou sen exportar. */
export type ExportBlockedResult = BlockedActivityExportResponse | undefined;

/** Erros da API que van no seu campo; o resto, no aviso do diálogo. */
const REFERENCE_INVALID = 'audit.export-reference-invalid';
const RANGE_INVALID = 'audit.range-invalid';

function reference(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value : '';
  if (!value.trim()) return { required: true };
  return validReference(value) ? null : { [REFERENCE_INVALID]: { max: REFERENCE_MAX } };
}

/** O último día non antes do primeiro e como moito {@link MAX_RANGE_DAYS} días. */
function range(group: AbstractControl): ValidationErrors | null {
  const from = group.get('from')?.value as IsoDate | null;
  const to = group.get('to')?.value as IsoDate | null;
  if (!from || !to) return null;
  return from <= to && rangeDays(from, to) <= MAX_RANGE_DAYS ? null : { [RANGE_INVALID]: { maxDays: MAX_RANGE_DAYS } };
}

/**
 * «Exportar bloqueados» (ADR-0020, revisión do 2026-10-29; LOPDGDD art. 32): o período (dous
 * `furar-date-field` limitados ao tramo bloqueado), o motivo (`furar-select`: requirimento
 * xudicial, Ministerio Fiscal, autoridade de protección de datos ou expediente aberto) e a
 * referencia. Todo obrigatorio. Formulario accesible coma «Invitar»: resumo de erros, cada erro no
 * seu campo e foco ao primeiro. A exportación queda no rexistro co motivo e a referencia; o CSV
 * faino a páxina.
 */
@Component({
  selector: 'furar-export-blocked-dialog',
  imports: [
    RequiredMarkComponent,
    RequiredNoteComponent,
    ButtonDirective,
    CalloutComponent,
    DateFieldComponent,
    DialogComponent,
    FieldComponent,
    FormErrorsSummaryComponent,
    InputDirective,
    OptionsPipe,
    ReactiveFormsModule,
    SelectComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './export-blocked-dialog.component.html',
})
export class ExportBlockedDialogComponent {
  protected readonly data = inject<ExportBlockedData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<ExportBlockedResult>>(DialogRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly transloco = inject(TranslocoService);

  protected readonly formId = 'export-blocked-form';
  protected readonly reasons = DISCLOSURE_REASONS;
  protected readonly referenceMax = REFERENCE_MAX;
  protected readonly maxDays = MAX_RANGE_DAYS;
  protected readonly firstText = formatShort(this.data.first);
  protected readonly lastText = formatShort(this.data.last);
  protected readonly describedBy = describedBy;
  protected readonly lang = toSignal(this.transloco.langChanges$, { initialValue: this.transloco.getActiveLang() });
  protected readonly form = new FormGroup(
    {
      // Por defecto, o último mes bloqueado: o máis habitual é un requirimento recente.
      from: new FormControl<IsoDate | null>(this.defaultFrom(), Validators.required),
      to: new FormControl<IsoDate | null>(this.data.last, Validators.required),
      reason: new FormControl<DisclosureReason | null>(null, Validators.required),
      reference: new FormControl('', { nonNullable: true, validators: [reference] }),
    },
    { validators: [range] },
  );
  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);
  /** Erro que non é dun campo, xa en texto. */
  protected readonly alert = signal<string | null>(null);

  private readonly formEvents = toSignal(this.form.events);
  protected readonly fromError = computed(() => {
    this.formEvents();
    return fieldError(this.form.controls.from, this.submitted());
  });
  /** O erro do último día: o seu ou, se os dous están, o do período. */
  protected readonly toError = computed(() => {
    this.formEvents();
    const own = fieldError(this.form.controls.to, this.submitted());
    if (own) return own;
    const touched = this.submitted() || this.form.controls.to.touched || this.form.controls.from.touched;
    const period = this.form.errors?.[RANGE_INVALID] as Record<string, unknown> | undefined;
    return touched && period ? { key: `errors.${RANGE_INVALID}`, params: period } : null;
  });
  protected readonly reasonError = computed(() => {
    this.formEvents();
    return fieldError(this.form.controls.reason, this.submitted());
  });
  protected readonly referenceError = computed(() => {
    this.formEvents();
    return fieldError(this.form.controls.reference, this.submitted());
  });
  protected readonly errorCount = computed(() => {
    this.formEvents();
    return this.submitted() ? countInvalid(this.form) : 0;
  });

  protected async submit(): Promise<void> {
    if (this.busy()) return;
    this.submitted.set(true);
    this.alert.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focusError();
      return;
    }
    const { from, to, reason, reference: text } = this.form.getRawValue();
    if (!from || !to || !reason) return;
    this.busy.set(true);
    try {
      const exported = await this.data.export({
        from: madridMidnight(from),
        to: madridMidnight(addDays(to, 1)),
        reason,
        reference: text.trim(),
      });
      this.busy.set(false);
      this.ref.close(exported);
    } catch (error) {
      this.busy.set(false);
      const problem = asProblem(error);
      if (problem.code === REFERENCE_INVALID) {
        const control = this.form.controls.reference;
        control.setErrors({ [problem.code]: problem.params });
        control.markAsTouched();
        this.focusError();
        return;
      }
      this.alert.set(problem.translated || this.transloco.translate(problem.messageKey, problem.params));
    }
  }

  private defaultFrom(): IsoDate {
    const monthBefore = addDays(this.data.last, -30);
    return monthBefore < this.data.first ? this.data.first : monthBefore;
  }

  private focusError(): void {
    afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
  }
}
