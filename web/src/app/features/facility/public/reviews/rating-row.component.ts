// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, Injector, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { DialogService } from '../../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { formatAverage } from '../../../../shared/ui/stars/rating-format';
import { FacilityReviewsStore } from './facility-reviews.store';
import type { ReviewsDialogData } from './reviews-dialog.component';
import { FacilityReviewsDialogComponent } from './reviews-dialog.component';

/**
 * A fila «Valoración» da tarxeta lateral da ficha pública (ADR-0037): «4,6 de 5 · 38
 * valoracións ›», co mesmo aspecto ca «Ver os N datos», que abre a capa coas valoracións. Sen
 * valoracións (ou se a nota non chega), non se pinta. Só aquí: nin nas tarxetas do catálogo nin no
 * corpo da ficha.
 */
@Component({
  selector: 'furar-facility-rating-row',
  imports: [IconComponent, TranslocoPipe],
  providers: [FacilityReviewsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-public-card__item', '[hidden]': '!text()', 'data-public-rating': '' },
  template: `
    <furar-icon name="star" [size]="24" />
    <div class="min-w-0">
      <h2 class="f-public-card__title">{{ 'reviews.row.title' | transloco }}</h2>
      <button
        type="button"
        class="f-public-card__link f-public-card__more"
        aria-haspopup="dialog"
        data-public-rating-open
        (click)="open()"
      >
        {{ text() }}
        <furar-icon name="chevron-right" />
      </button>
    </div>
  `,
})
export class FacilityRatingRowComponent implements OnInit {
  readonly slug = input.required<string>();
  readonly facilityId = input.required<string>();
  readonly facilityName = input.required<string>();

  private readonly store = inject(FacilityReviewsStore);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  /** «4,6 de 5 · 38 valoracións»; baleiro sen valoracións. */
  protected readonly text = computed(() => {
    this.langChanges();
    const summary = this.store.summary();
    if (!summary?.reviews || summary.average === null || summary.average === undefined) return '';
    const lang = this.language.lang();
    const average = formatAverage(summary.average, lang);
    return summary.reviews === 1
      ? this.transloco.translate('reviews.row.summaryOne', { average })
      : this.transloco.translate('reviews.row.summary', { average, count: summary.reviews.toLocaleString(lang) });
  });

  ngOnInit(): void {
    void this.store.loadSummary(this.slug(), this.facilityId());
  }

  protected open(): void {
    const data: ReviewsDialogData = {
      slug: this.slug(),
      facilityId: this.facilityId(),
      facilityName: this.facilityName(),
    };
    this.dialogs.open<unknown, ReviewsDialogData, FacilityReviewsDialogComponent>(FacilityReviewsDialogComponent, {
      data,
      sheet: true,
      injector: this.injector,
    });
  }
}
