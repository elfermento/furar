// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldGroupComponent } from '../../../shared/ui/field-group/field-group.component';
import { FieldComponent } from '../../../shared/ui/field.component';
import { FocusErrorDirective } from '../../../shared/ui/focus-error/focus-error.directive';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { joinRequestBody, joinRequestForm } from './join-request.form';
import type { JoinRequestForm } from './join-request.form';
import { JoinRequestStore } from './join-request.store';

type Field = Exclude<keyof JoinRequestForm['controls'], 'website'>;

/**
 * «¿Es un concello?» (`/alta-concello`, ADR-0042): un concello pide darse de alta e a solicitude
 * vai por correo a quen opera o servizo (se o correo non sae, a API gárdaa e reinténtaa, e aquí
 * vese igual: «Recibimos a solicitude»). Concello ou entidade, nome, cargo e, nunha fila, correo e
 * teléfono nunha cápsula; a mensaxe debaixo; a frase de privacidade coa ligazón á súa páxina
 * (`/alta-concello/privacidade`); e «Enviar solicitude». O erro, debaixo de cada campo, e o foco
 * ao primeiro ao enviar. Enviada, na mesma páxina e sen a introdución: só a confirmación co correo
 * ao que se contestará. Un campo trampa, escondido ás persoas, para os programas; nada de CAPTCHA
 * nin de terceiros.
 */
@Component({
  selector: 'furar-join-request-page',
  imports: [
    ButtonDirective,
    CalloutComponent,
    FieldComponent,
    FieldGroupComponent,
    FocusErrorDirective,
    InputDirective,
    PageHeaderComponent,
    ProblemAlertComponent,
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './join-request.page.html',
})
export class JoinRequestPage {
  private readonly store = inject(JoinRequestStore);
  private readonly injector = inject(Injector);
  private readonly done = viewChild<ElementRef<HTMLElement>>('done');

  protected readonly id = 'join-request';
  protected readonly form = joinRequestForm();
  protected readonly submitted = signal(false);
  protected readonly sending = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  /** O correo ao que se contestará, unha vez enviada. */
  protected readonly sentTo = signal<string | null>(null);

  protected error(name: Field): ControlError | null {
    const control = this.form.controls[name];
    const error = fieldError(control, this.submitted());
    // O correo mal escrito, co seu texto propio.
    if (name === 'email' && control.hasError('pattern')) {
      return error && { key: 'joinRequest.emailInvalid', params: {} };
    }
    return error;
  }

  protected described(name: Field): string | null {
    return describedBy(`${this.id}-${name}`, false, this.error(name));
  }

  protected async submit(): Promise<void> {
    if (this.sending()) return;
    this.submitted.set(true);
    this.problem.set(null);
    this.form.markAllAsTouched();
    // O foco ao primeiro erro pono `furarFocusError`.
    if (this.form.invalid) return;
    this.sending.set(true);
    const body = joinRequestBody(this.form);
    try {
      await this.store.submit(body);
      this.sentTo.set(body.email);
      afterNextRender(() => this.done()?.nativeElement.focus(), { injector: this.injector });
    } catch (error) {
      const problem = asProblem(error);
      const fields = problem.fieldErrors.filter((e) => e.field in this.form.controls && e.field !== 'website');
      if (fields.length) {
        for (const e of fields) {
          this.form.controls[e.field as Field].setErrors({ [e.code]: e.params });
        }
      } else {
        this.problem.set(problem);
      }
    } finally {
      this.sending.set(false);
    }
  }
}
