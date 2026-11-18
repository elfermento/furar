// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA } from '@angular/cdk/dialog';
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { madridToday } from '../../../../shared/ui/calendar/madrid-time';
import { DialogComponent } from '../../../../shared/ui/dialog/dialog.component';
import type { SelectOption } from '../../../../shared/ui/select/select.component';
import { SelectComponent } from '../../../../shared/ui/select/select.component';
import { formatAverage } from '../../../../shared/ui/stars/rating-format';
import { ReviewComponent } from '../../../../shared/ui/review/review.component';
import { RowListDirective } from '../../../../shared/ui/row/row-list.directive';
import { StatusStateComponent } from '../../../../shared/ui/status-state/status-state.component';
import type { ReviewOrder } from './facility-reviews.store';
import { FacilityReviewsStore, REVIEW_ORDERS } from './facility-reviews.store';
import { reviewDate } from './review-date';

/** A instalación da capa e o concello do URL. */
export interface ReviewsDialogData {
  readonly slug: string;
  readonly facilityId: string;
  readonly facilityName: string;
}

/**
 * A capa das valoracións dunha instalación (ADR-0037): a nota media grande
 * e cantas; á dereita, «Ordenar por» co despregable do kit (máis recentes por defecto, máis
 * antigas, nota máis alta e máis baixa); debaixo, a lista (`furar-review`: día, estrelas e
 * comentario se o hai, nunca quen valorou) e «Ver máis». No móbil, folla desde abaixo.
 */
@Component({
  selector: 'furar-facility-reviews-dialog',
  imports: [
    ButtonDirective,
    DialogComponent,
    ReviewComponent,
    RowListDirective,
    SelectComponent,
    StatusStateComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'reviews.layer.';
    <furar-dialog [heading]="t + 'title' | transloco" [subheading]="data.facilityName">
      <div class="f-reviews__head">
        <p class="f-reviews__average" data-reviews-average>
          <b>{{ average() }}</b
          >&ngsp;<span>{{ count() }}</span>
        </p>
        <div class="f-reviews__sort">
          <label class="f-label" [for]="sortId">{{ t + 'sort' | transloco }}</label>
          <furar-select
            data-reviews-sort
            [selectId]="sortId"
            [label]="t + 'sort' | transloco"
            [options]="orders()"
            [value]="store.order()"
            (changed)="sort($event)"
          />
        </div>
      </div>
      @switch (store.state()) {
        @case ('error') {
          <furar-status-state mode="error" [heading]="t + 'error' | transloco">
            <button type="button" furarButton (click)="load()">{{ t + 'retry' | transloco }}</button>
          </furar-status-state>
        }
        @case ('ready') {
          <ul furarRowList data-reviews-list [attr.aria-label]="listLabel()">
            @for (review of store.items(); track review.id) {
              <li class="f-review-row" [attr.data-review]="review.id">
                <furar-review
                  [stars]="review.stars"
                  [date]="date(review.createdAt)"
                  [datetime]="review.createdAt"
                  [comment]="review.comment"
                />
              </li>
            }
          </ul>
          @if (store.items().length < store.total()) {
            <button
              type="button"
              furarButton
              class="f-reviews__more"
              data-reviews-more
              [busy]="store.loadingMore()"
              (click)="more()"
            >
              {{ t + 'more' | transloco }}
            </button>
          }
        }
        @default {
          <furar-status-state mode="skeleton" shape="row" [rows]="3" [description]="t + 'loading' | transloco" />
        }
      }
    </furar-dialog>
  `,
})
export class FacilityReviewsDialogComponent implements OnInit {
  protected readonly data = inject<ReviewsDialogData>(DIALOG_DATA);
  protected readonly store = inject(FacilityReviewsStore);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly today = madridToday();

  protected readonly sortId = 'furar-reviews-sort';
  protected readonly orders = computed<SelectOption<ReviewOrder>[]>(() => {
    this.langChanges();
    return REVIEW_ORDERS.map((value) => ({
      value,
      label: this.transloco.translate(`reviews.layer.order.${value}`),
    }));
  });
  protected readonly average = computed(() => {
    this.langChanges();
    const average = this.store.summary()?.average;
    return average === null || average === undefined
      ? ''
      : this.transloco.translate('reviews.layer.average', {
          average: formatAverage(average, this.language.lang()),
        });
  });
  protected readonly count = computed(() => {
    this.langChanges();
    const count = this.store.summary()?.reviews ?? 0;
    return count === 1
      ? this.transloco.translate('reviews.layer.countOne')
      : this.transloco.translate('reviews.layer.count', { count: count.toLocaleString(this.language.lang()) });
  });
  protected readonly listLabel = computed(() => {
    this.langChanges();
    const order = this.transloco.translate(`reviews.layer.order.${this.store.order()}`);
    return this.transloco.translate('reviews.layer.list', { order: order.toLocaleLowerCase(this.language.lang()) });
  });

  ngOnInit(): void {
    void this.load();
  }

  protected load(): Promise<void> {
    return this.store.load(this.data.slug, this.data.facilityId);
  }

  protected sort(order: ReviewOrder): void {
    if (order !== this.store.order()) void this.store.load(this.data.slug, this.data.facilityId, order);
  }

  protected more(): void {
    void this.store.more(this.data.slug, this.data.facilityId);
  }

  protected date(instant: string): string {
    this.langChanges();
    return reviewDate(instant, this.language.lang(), this.today);
  }
}
