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
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { countInvalid, describedBy, fieldError, focusFirstInvalid } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { FormErrorsSummaryComponent } from '../../../shared/ui/form-errors-summary.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { RadioCardsComponent } from '../../../shared/ui/radio-cards/radio-cards.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import type { MailLanguage, StaffMemberResponse, StaffRole } from './staff-members';
import { MAIL_LANGUAGES, RATE_LIMITED, retryWait, roleCards } from './staff-members';
import type { InviteStaffMemberRequest } from './staff-members.store';
import { RequiredNoteComponent } from '../../../shared/ui/required/required.component';

export interface InviteStaffData {
  /** Idioma do correo ao abrir: o idioma por defecto do concello. */
  readonly language: MailLanguage;
  /** Invita; resolve coa persoa nova e rexeita co erro da API. */
  readonly invite: (body: InviteStaffMemberRequest) => Promise<StaffMemberResponse>;
}

/** A persoa invitada, ou `undefined` se se pechou sen invitar. */
export type InviteStaffResult = StaffMemberResponse | undefined;

/** Erros da API que van no campo do correo. */
const EMAIL_ERRORS = new Set(['staff.already-member', 'staff.email-invalid']);

/** Obrigatorio e coa forma dun correo (o servidor normaliza e volve comprobar). */
function emailShape(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  if (!value) return { required: true };
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) ? null : { 'staff.email-invalid': {} };
}

/**
 * «Invitar» (D4-14): correo, rol (tarxetas co que pode facer cada un, `furar-radio-cards`; a
 * xestión e a plataforma, igual) e idioma
 * do correo (por defecto o do concello). Formulario accesible: resumo de erros arriba,
 * cada erro no seu campo por `aria-describedby` e foco ao primeiro. «Xa está no persoal» e «correo
 * non válido» van no campo; o 429 di canto esperar; o resto, no aviso do diálogo.
 */
@Component({
  selector: 'furar-invite-staff-dialog',
  imports: [
    RequiredNoteComponent,
    ButtonDirective,
    CalloutComponent,
    DialogComponent,
    FieldComponent,
    FormErrorsSummaryComponent,
    InputDirective,
    RadioCardsComponent,
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './invite-staff-dialog.component.html',
})
export class InviteStaffDialogComponent {
  protected readonly data = inject<InviteStaffData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<InviteStaffResult>>(DialogRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly transloco = inject(TranslocoService);

  protected readonly formId = 'staff-invite-form';
  private readonly lang = toSignal(this.transloco.langChanges$, { initialValue: this.transloco.getActiveLang() });
  protected readonly roleCards = computed(() => roleCards(this.transloco, this.lang()));
  protected readonly languages = MAIL_LANGUAGES;
  protected readonly describedBy = describedBy;
  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [emailShape] }),
    role: new FormControl<StaffRole>('MANAGER', { nonNullable: true }),
    language: new FormControl<MailLanguage>(this.data.language, { nonNullable: true }),
  });
  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);
  /** Erro que non é dun campo, xa en texto. */
  protected readonly alert = signal<string | null>(null);

  private readonly formEvents = toSignal(this.form.events);
  protected readonly emailError = computed(() => {
    this.formEvents();
    return fieldError(this.form.controls.email, this.submitted());
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
    const { email, role, language } = this.form.getRawValue();
    this.busy.set(true);
    try {
      const created = await this.data.invite({
        email: email.trim(),
        role,
        language,
      });
      this.busy.set(false);
      this.ref.close(created);
    } catch (error) {
      this.busy.set(false);
      this.showError(asProblem(error));
    }
  }

  private showError(problem: ApiProblem): void {
    if (EMAIL_ERRORS.has(problem.code)) {
      const email = this.form.controls.email;
      email.setErrors({ [problem.code]: problem.params });
      email.markAsTouched();
      this.focusError();
      return;
    }
    const wait = problem.code === RATE_LIMITED ? retryWait(problem) : null;
    if (wait) {
      this.alert.set(this.transloco.translate(`staff.members.rateLimited.${wait.key}`, { count: wait.count }));
      return;
    }
    this.alert.set(problem.translated || this.transloco.translate(problem.messageKey, problem.params));
  }

  private focusError(): void {
    afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
  }
}
