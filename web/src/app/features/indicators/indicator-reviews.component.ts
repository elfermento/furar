// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  effect,
  inject,
  input,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { formatDateLong, formatDayMonth } from '../../shared/ui/calendar/local-date';
import { inMadrid } from '../../shared/ui/calendar/madrid-time';
import type { ColumnFilterOption } from '../../shared/ui/column-filter/column-filter.component';
import { ColumnFilterComponent } from '../../shared/ui/column-filter/column-filter.component';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ReviewComponent } from '../../shared/ui/review/review.component';
import { RowListDirective } from '../../shared/ui/row/row-list.directive';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import type { ManagedReviewResponse, ReviewsQuery } from './indicator-reviews.store';
import { IndicatorReviewsStore } from './indicator-reviews.store';
import type { ReviewDetailData } from './review-detail-dialog.component';
import { ReviewDetailDialogComponent } from './review-detail-dialog.component';

/**
 * A tarxeta «Valoracións» dos indicadores (ADR-0037): as do período e os filtros do
 * panel, cantas e cantas con comentario; o filtro «Nota» en pílula; a lista (cada valoración
 * co canon de `furar-review`: instalación e día, nota e o comentario no seu parágrafo) e «Ver
 * máis». A fila abre o detalle, onde se oculta ou se volve mostrar o comentario; oculto, leva o
 * chip «Oculto». Sen valoracións no período (e sen filtro de nota), a tarxeta non sae.
 */
@Component({
  selector: 'furar-indicator-reviews',
  imports: [
    ButtonDirective,
    ColumnFilterComponent,
    IconComponent,
    ReviewComponent,
    RowListDirective,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
  ],
  providers: [IndicatorReviewsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[hidden]': '!visible()' },
  template: `
    @let t = 'reviews.manage.';
    <section class="f-card f-ireviews" aria-labelledby="f-ireviews-title" data-indicator-reviews>
      <h2 id="f-ireviews-title" class="f-ireviews__title">{{ t + 'title' | transloco }}</h2>
      <p class="f-ireviews__summary" data-reviews-summary>{{ summary() }}</p>
      <div class="f-ireviews__pills">
        <furar-column-filter
          variant="chip"
          [label]="t + 'stars' | transloco"
          [options]="starOptions()"
          [selected]="selected()"
          [doneLabel]="doneLabel()"
          (toggled)="toggle($event)"
          (cleared)="filter([])"
        />
      </div>
      @if (store.state() === 'error') {
        <furar-status-state mode="error" [heading]="t + 'loadError' | transloco">
          <button type="button" furarButton (click)="reload()">{{ t + 'retry' | transloco }}</button>
        </furar-status-state>
      } @else {
        @if (store.items().length) {
          <ul
            furarRowList
            class="f-ireviews__list"
            data-reviews-list
            [attr.aria-label]="t + 'caption' | transloco"
            [attr.aria-busy]="store.state() === 'loading' || null"
          >
            @for (review of store.items(); track review.id) {
              <li [attr.data-review]="review.id">
                <button
                  type="button"
                  class="f-review-row f-ireviews__open"
                  aria-haspopup="dialog"
                  [attr.data-open]="review.id"
                  (click)="open(review)"
                >
                  <furar-review
                    [stars]="review.stars"
                    [date]="shortDay(review.createdAt)"
                    [datetime]="review.createdAt"
                    [place]="review.facilityName"
                    [comment]="review.comment"
                    [noComment]="t + 'noComment' | transloco"
                    [hidden]="review.hidden"
                  >
                    @if (review.hidden) {
                      <furar-tag tone="muted" data-review-hidden>{{ t + 'hidden' | transloco }}</furar-tag>
                    }
                  </furar-review>
                  <furar-icon class="f-review-row__chevron" name="chevron-right" />
                </button>
              </li>
            }
          </ul>
        }
        @if (store.state() === 'ready' && !store.items().length) {
          <p class="f-ireviews__empty" data-reviews-empty>{{ t + 'empty' | transloco }}</p>
        }
        @if (more()) {
          <button
            type="button"
            furarButton
            class="f-ireviews__more"
            data-reviews-more
            [busy]="store.loadingMore()"
            (click)="store.more(slug(), query())"
          >
            {{ t + 'more' | transloco }}
          </button>
        }
      }
    </section>
  `,
})
export class IndicatorReviewsComponent {
  readonly slug = input.required<string>();
  readonly query = input.required<ReviewsQuery>();

  protected readonly store = inject(IndicatorReviewsStore);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  /** Fóra só se non hai ningunha no período e non se filtra por nota (a táboa baleira non axuda). */
  protected readonly visible = computed(
    () => this.store.state() === 'error' || !!this.store.stars().length || (this.store.page()?.totalItems ?? 0) > 0,
  );
  protected readonly selected = computed(() => this.store.stars().map(String));
  protected readonly more = computed(() => this.store.items().length < (this.store.page()?.totalItems ?? 0));
  protected readonly summary = computed(() => {
    this.langChanges();
    const page = this.store.page();
    if (!page) return '';
    const lang = this.language.lang();
    const commented = page.withComment.toLocaleString(lang);
    return page.totalItems === 1
      ? this.transloco.translate('reviews.manage.summaryOne', { commented })
      : this.transloco.translate('reviews.manage.summary', { count: page.totalItems.toLocaleString(lang), commented });
  });
  /** As cinco notas, da 5 á 1, con cantas ten cada unha. */
  protected readonly starOptions = computed<ColumnFilterOption[]>(() => {
    this.langChanges();
    return (this.store.page()?.byStars ?? []).map(({ stars, count }) => ({
      value: String(stars),
      label: this.transloco.translate(stars === 1 ? 'ui.stars.optionOne' : 'ui.stars.option', { count: stars }),
      count,
      stars,
    }));
  });
  protected readonly doneLabel = computed(() => {
    this.langChanges();
    return this.transloco.translate('reviews.manage.done', { count: this.store.page()?.totalItems ?? 0 });
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      const query = this.query();
      untracked(() => void this.store.load(slug, query));
    });
  }

  protected reload(): void {
    void this.store.load(this.slug(), this.query());
  }

  /** «Nota»: suma ou quita a nota premida. */
  protected toggle(value: string): void {
    const stars = Number(value);
    const current = this.store.stars();
    this.filter(current.includes(stars) ? current.filter((s) => s !== stars) : [...current, stars]);
  }

  protected filter(stars: readonly number[]): void {
    void this.store.load(this.slug(), this.query(), stars);
  }

  protected shortDay(instant: string): string {
    this.langChanges();
    return formatDayMonth(inMadrid(instant).date, this.language.lang());
  }

  /** A fila abre o detalle. */
  protected open(review: ManagedReviewResponse): void {
    const data: ReviewDetailData = {
      review: this.store.items().find((r) => r.id === review.id) ?? review,
      day: formatDateLong(inMadrid(review.createdAt).date, this.language.lang()),
      slug: this.slug(),
    };
    this.dialogs.open<unknown, ReviewDetailData, ReviewDetailDialogComponent>(ReviewDetailDialogComponent, {
      data,
      injector: this.injector,
    });
  }
}
