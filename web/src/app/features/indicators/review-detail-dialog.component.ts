// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { DialogComponent } from '../../shared/ui/dialog/dialog.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { StarsComponent } from '../../shared/ui/stars/stars.component';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import type { ManagedReviewResponse } from './indicator-reviews.store';
import { IndicatorReviewsStore } from './indicator-reviews.store';

/** A valoración, o seu día xa escrito e o concello do URL. */
export interface ReviewDetailData {
  readonly review: ManagedReviewResponse;
  readonly day: string;
  readonly slug: string;
}

/**
 * O detalle dunha valoración na xestión (ADR-0037): instalación, uso, data e nota; o
 * comentario e, se o hai, «Ocultar comentario» (deixa de verse na ficha pública, a nota segue
 * contando) ou, se xa está oculto, «Mostrar comentario». Nunca quen valorou.
 */
@Component({
  selector: 'furar-review-detail-dialog',
  imports: [
    ButtonDirective,
    DialogComponent,
    IconComponent,
    ProblemAlertComponent,
    StarsComponent,
    TagComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'reviews.manage.';
    @let r = review();
    <furar-dialog [heading]="t + 'detail' | transloco" [busy]="busy()">
      <dl class="f-review-detail__facts">
        <div>
          <dt>{{ t + 'facility' | transloco }}</dt>
          <dd>{{ r.facilityName }}</dd>
        </div>
        <div>
          <dt>{{ t + 'use' | transloco }}</dt>
          <dd>{{ r.useName }}</dd>
        </div>
        <div>
          <dt>{{ t + 'date' | transloco }}</dt>
          <dd>{{ data.day }}</dd>
        </div>
        <div>
          <dt>{{ t + 'stars' | transloco }}</dt>
          <dd><furar-stars size="md" [value]="r.stars" /></dd>
        </div>
      </dl>
      <div class="f-review-detail__comment" data-review-detail-comment>
        @if (r.comment) {
          @if (r.hidden) {
            <furar-tag tone="muted" data-review-hidden>{{ t + 'hidden' | transloco }}</furar-tag>
          }
          <p>{{ r.comment }}</p>
        } @else {
          <p class="f-review-detail__none">{{ t + 'noComment' | transloco }}</p>
        }
      </div>
      <furar-problem-alert [problem]="problem()" />
      @if (r.comment) {
        <ng-container dialogActions>
          <button type="button" furarButton data-review-hide [busy]="busy()" (click)="toggle()">
            <furar-icon [name]="r.hidden ? 'visibility' : 'visibility-off'" />
            {{ (r.hidden ? t + 'unhide' : t + 'hide') | transloco }}
          </button>
        </ng-container>
      }
    </furar-dialog>
  `,
})
export class ReviewDetailDialogComponent {
  protected readonly data = inject<ReviewDetailData>(DIALOG_DATA);
  private readonly store = inject(IndicatorReviewsStore);
  private readonly toasts = inject(ToastService);

  protected readonly review = signal(this.data.review);
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  protected async toggle(): Promise<void> {
    if (this.busy()) return;
    const hidden = !this.review().hidden;
    this.busy.set(true);
    this.problem.set(null);
    try {
      this.review.set(await this.store.hide(this.data.slug, this.review(), hidden));
      this.toasts.show(hidden ? 'reviews.manage.hiddenDone' : 'reviews.manage.shownDone');
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }
}
