// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { FieldComponent } from '../../../shared/ui/field.component';
import { describedBy } from '../../../shared/ui/field-errors';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { StarRatingComponent } from '../../../shared/ui/star-rating/star-rating.component';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { MyBookingsStore } from './my-bookings.store';

/** O que leva a capa: a reserva, o seu día xa escrito e o concello do URL. */
export interface ReviewDialogData {
  readonly booking: MyBookingResponse;
  readonly day: string;
  readonly slug: string;
}

/** Caracteres como moito do comentario (o mesmo límite ca a API). */
export const MAX_COMMENT = 300;

/**
 * «Valorar» unha reserva do historial (ADR-0037): a nota (`furar-star-rating`,
 * obrigatoria) e un comentario opcional cun «?» que avisa de que se publica sen nome e sen datos
 * persoais. Envía coa capa ocupada; ben, pecha con `true` (a páxina dá as grazas); mal, o erro
 * queda na capa. Sen nota, «Enviar» marca o erro no grupo das estrelas.
 */
@Component({
  selector: 'furar-review-dialog',
  imports: [
    ButtonDirective,
    DialogComponent,
    FieldComponent,
    InputDirective,
    ProblemAlertComponent,
    StarRatingComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'reviews.dialog.';
    <furar-dialog
      size="sm"
      [heading]="t + 'title' | transloco"
      [subheading]="data.booking.facilityName + ' · ' + data.day"
      [busy]="busy()"
    >
      <form class="f-review-form" id="furar-review-form" novalidate (submit)="send($event)">
        <furar-star-rating
          data-review-stars
          [label]="t + 'stars' | transloco"
          [required]="true"
          [error]="missing() ? (t + 'starsRequired' | transloco) : ''"
          [(value)]="stars"
        />
        <furar-field
          fieldId="furar-review-comment"
          [label]="t + 'comment' | transloco"
          [tip]="t + 'commentTip' | transloco"
        >
          <textarea
            #comment
            furarInput
            id="furar-review-comment"
            rows="4"
            data-review-comment
            [attr.maxlength]="max"
            [attr.aria-describedby]="describedBy('furar-review-comment', true, null)"
            [placeholder]="t + 'commentPlaceholder' | transloco"
            [value]="text()"
            (input)="text.set(comment.value)"
          ></textarea>
        </furar-field>
        <furar-problem-alert [problem]="problem()" />
      </form>
      <ng-container dialogActions>
        <button type="button" furarButton data-review-cancel [disabled]="busy()" (click)="ref.close(false)">
          {{ t + 'cancel' | transloco }}
        </button>
        <button type="submit" form="furar-review-form" furarButton="primary" data-review-send [busy]="busy()">
          {{ (busy() ? t + 'sending' : t + 'send') | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class ReviewDialogComponent {
  protected readonly data = inject<ReviewDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<boolean>>(DialogRef);
  private readonly store = inject(MyBookingsStore);

  protected readonly max = MAX_COMMENT;
  protected readonly describedBy = describedBy;
  protected readonly stars = signal<number | null>(null);
  protected readonly text = signal('');
  protected readonly missing = signal(false);
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  protected async send(event: Event): Promise<void> {
    event.preventDefault();
    if (this.busy()) return;
    const stars = this.stars();
    this.missing.set(stars === null);
    if (stars === null) return;
    this.busy.set(true);
    this.problem.set(null);
    try {
      await this.store.review(this.data.slug, this.data.booking, stars, this.text());
      this.ref.close(true);
    } catch (error) {
      this.problem.set(asProblem(error));
      this.busy.set(false);
    }
  }
}
