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
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { ManageBookingsStore } from '../manage/manage-bookings.store';
import { PAYMENT_TONE, minutesText, paymentKey, weekdaysText } from './seasons';
import type { SeriesResponse } from './seasons.store';
import { SeasonsStore } from './seasons.store';
import type { SeriesDetailData } from './series-detail.component';
import { SeriesDetailComponent } from './series-detail.component';

/** Cantas tempadas se ven antes de «Ver máis». */
export const SERIES_SHOWN = 3;

let nextId = 0;

/**
 * «Tempadas» na portada de «Reservas»: as tempadas en curso, cada unha unha fila que
 * abre o seu detalle (a entidade en negra, «instalación · parte», os días e as horas e o chip do
 * pago á dereita); as tres primeiras e «Ver máis». Só a xestión. Sen tempadas, non pinta nada.
 */
@Component({
  selector: 'furar-series-section',
  imports: [ButtonDirective, IconComponent, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @let t = 'booking.seasons.';
    @if (items().length && !hide()) {
      <section class="f-card f-manage__sec" data-section="series" [attr.aria-labelledby]="headingId">
        <h2 [id]="headingId" class="f-season-sec__title">
          {{ t + 'section' | transloco }}
          <span class="f-manage__n" data-series-count>{{ items().length }}</span>
        </h2>
        <ul class="f-manage__rows">
          @for (s of shown(); track s.id) {
            <li>
              <button type="button" class="f-mrow" aria-haspopup="dialog" [attr.data-series]="s.id" (click)="open(s)">
                <span class="f-mrow__main">
                  <b class="f-mrow__name">{{ s.organizationName }}</b
                  >{{ ' ' }} <span class="f-mrow__meta">{{ place(s) }}</span
                  >{{ ' ' }}
                  <span class="f-mrow__when">
                    <furar-icon name="event-repeat" />
                    <span>
                      <span class="f-season-part">{{ days(s) }}</span>
                      {{ ' · ' }}
                      <span class="f-season-part">{{ hours(s) }}</span>
                    </span>
                  </span>
                </span>
                {{ ' ' }}
                <span class="f-mrow__end">
                  <furar-tag look="solid" [tone]="tone[s.paymentStatus]" [attr.data-chip]="s.paymentStatus">{{
                    paymentKey(s.paymentStatus) | transloco
                  }}</furar-tag>
                </span>
              </button>
            </li>
          }
        </ul>
        @if (items().length > shown().length) {
          <button type="button" furarButton size="sm" class="f-manage__more" data-series-more (click)="all.set(true)">
            {{ 'booking.manage.more' | transloco }}
          </button>
        }
      </section>
    }
  `,
})
export class SeriesSectionComponent {
  private readonly store = inject(SeasonsStore);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  /** O detalle cancela sesións co diálogo de sempre, que actualiza a portada. */
  private readonly bookings = inject(ManageBookingsStore, { optional: true });

  readonly slug = input.required<string>();
  /** Co detalle dunha reserva ao lado, a portada só ten a sección desa reserva. */
  readonly hide = input(false);
  /** Só a xestión (o permiso das entidades); Recepción non as ve. */
  private readonly manager = inject(StaffAccessService).canManage;

  protected readonly headingId = `furar-series-section-${nextId++}`;
  protected readonly tone = PAYMENT_TONE;
  protected readonly paymentKey = paymentKey;
  protected readonly items = this.store.activeSeries;
  protected readonly all = signal(false);
  protected readonly shown = computed(() => (this.all() ? this.items() : this.items().slice(0, SERIES_SHOWN)));

  constructor() {
    effect(() => {
      const slug = this.slug();
      if (this.manager()) untracked(() => void this.store.loadSeries(slug));
    });
  }

  protected place(series: SeriesResponse): string {
    return [series.facilityName, series.partName].filter(Boolean).join(' · ');
  }

  protected days(series: SeriesResponse): string {
    this.langChanges();
    return weekdaysText(series.weekdays, this.language.lang(), this.transloco.translate('booking.who.and'));
  }

  protected hours(series: SeriesResponse): string {
    return minutesText(series.startMinute, series.endMinute, this.language.lang());
  }

  /** O detalle (folla no móbil): cada cambio (o pago, unha sesión, rematala) pasa á fila. */
  protected open(series: SeriesResponse): void {
    const data: SeriesDetailData = { series, slug: this.slug(), changed: (updated) => this.changed(updated) };
    this.dialogs.open<SeriesResponse, SeriesDetailData, SeriesDetailComponent>(SeriesDetailComponent, {
      data,
      sheet: true,
      injector: this.injector,
    });
  }

  /** A fila cos datos novos (rematada, sae); e as contas da portada pídense de novo. */
  private changed(updated: SeriesResponse): void {
    this.store.seriesChanged(updated);
    if (this.bookings && this.bookings.overviewState() !== 'idle') void this.bookings.loadOverview(this.slug());
  }
}
