// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import type { SeriesRequestCardResponse } from './seasons.store';
import { SeasonsStore } from './seasons.store';

/** O máximo da mensaxe de rexeitamento (ADR-0039, punto 11). */
export const MESSAGE_MAX = 1000;

export interface RejectRequestData {
  readonly request: SeriesRequestCardResponse;
  readonly slug: string;
}

/**
 * «Rexeitar a solicitude»: a entidade e que lle chega por correo na cabeceira, a
 * mensaxe (obrigatoria) e «Rexeitar e avisar» en vermello. Pecha coa solicitude xa rexeitada; un
 * erro queda dentro.
 */
@Component({
  selector: 'furar-reject-request-dialog',
  imports: [
    ButtonDirective,
    DialogComponent,
    FieldComponent,
    InputDirective,
    ProblemAlertComponent,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  providers: [SeasonsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.seasons.reject.';
    <furar-dialog
      size="sm"
      [heading]="t + 'title' | transloco"
      [subheading]="t + 'subheading' | transloco: { name: data.request.organizationName }"
      [busy]="busy()"
    >
      <form novalidate class="gap-form grid" id="furar-reject-request" (submit)="$event.preventDefault(); confirm()">
        <furar-field fieldId="furar-reject-message" [label]="t + 'message' | transloco" required [error]="error()">
          <textarea
            furarInput
            rows="4"
            data-reject-message
            id="furar-reject-message"
            [formControl]="message"
            [maxlength]="max"
            [attr.aria-invalid]="error() ? 'true' : null"
            [attr.aria-describedby]="error() ? 'furar-reject-message-error' : null"
          ></textarea>
        </furar-field>
        <furar-problem-alert [problem]="problem()" />
      </form>
      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-reject-keep (click)="ref.close()">
          {{ 'common.cancel' | transloco }}
        </button>
        <button type="submit" form="furar-reject-request" furarButton="danger" data-reject-confirm [busy]="busy()">
          {{ t + 'confirm' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class RejectRequestDialogComponent {
  protected readonly data = inject<RejectRequestData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<SeriesRequestCardResponse>>(DialogRef);
  private readonly store = inject(SeasonsStore);

  protected readonly max = MESSAGE_MAX;
  protected readonly message = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(MESSAGE_MAX)],
  });
  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  protected error() {
    const blank = this.submitted() && !this.message.value.trim();
    return blank
      ? { key: 'errors.booking.series-request-message-required', params: {} }
      : fieldError(this.message, this.submitted());
  }

  protected async confirm(): Promise<void> {
    this.submitted.set(true);
    this.problem.set(null);
    const message = this.message.value.trim();
    if (!message || this.message.invalid || this.busy()) return;
    this.busy.set(true);
    try {
      this.ref.close(await this.store.reject(this.data.slug, this.data.request.id, message));
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }
}
