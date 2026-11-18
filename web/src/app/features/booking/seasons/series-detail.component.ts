// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, Injector, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { ActionItemComponent, ActionListDirective } from '../../../shared/ui/action-list/action-list.component';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { inMadrid, madridToday } from '../../../shared/ui/calendar/madrid-time';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { DisclosureComponent } from '../../../shared/ui/disclosure/disclosure.component';
import { FieldLineComponent } from '../../../shared/ui/field-line/field-line.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ChipMenuComponent } from '../../../shared/ui/menu/chip-menu.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { ValueListItem } from '../../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../../shared/ui/value-list/value-list.component';
import type { CancelBookingData } from '../../../shared/booking-cancel/cancel-booking-dialog.component';
import { CancelBookingDialogComponent } from '../../../shared/booking-cancel/cancel-booking-dialog.component';
import { ManageBookingsStore } from '../manage/manage-bookings.store';
import { formatDay } from '../reserve/reserve';
import type { EndSeriesData } from './end-series-dialog.component';
import { EndSeriesDialogComponent } from './end-series-dialog.component';
import type { SeriesPayment } from './seasons';
import { paymentOptions, placeParts, seriesRows } from './seasons';
import type { ManagedBookingResponse, SeriesResponse } from './seasons.store';
import { SeasonsStore } from './seasons.store';

/** O que abre o detalle: a tempada, o concello e quen quere saber dos cambios (a lista). */
export interface SeriesDetailData {
  /** A tempada ou, se aínda non se ten (desde unha sesión), o seu id: pídese ao abrir. */
  readonly series: SeriesResponse | string;
  readonly slug: string;
  /** A tempada cambiou (o pago, as sesións, rematouse). */
  readonly changed?: (series: SeriesResponse) => void;
}

/** Abre o detalle dunha tempada (folla no móbil); desde unha sesión, co id da súa tempada. */
export function openSeriesDetail(dialogs: DialogService, injector: Injector, data: SeriesDetailData): void {
  dialogs.open(SeriesDetailComponent, { data, sheet: true, injector });
}

/**
 * Detalle dunha tempada na xestión: `furar-dialog` (folla no móbil) coa entidade e
 * «instalación · parte · uso» na cabeceira; os días e as horas, as datas e as sesións; «Pago da
 * tempada» nunha fila solta co chip-menú (Pendente de pago · Pagada · Gratis); as sesións que
 * quedan, pregadas, cada unha con «Cancelar a sesión» á dereita (o diálogo de sempre, un
 * interruptor por canle); e, ao final, «Rematar a tempada…». As sesións non se moven.
 */
@Component({
  selector: 'furar-series-detail',
  imports: [
    ActionItemComponent,
    ActionListDirective,
    ButtonDirective,
    ChipMenuComponent,
    DialogComponent,
    DisclosureComponent,
    FieldLineComponent,
    IconComponent,
    ProblemAlertComponent,
    StatusStateComponent,
    TranslocoPipe,
    ValueListComponent,
  ],
  providers: [SeasonsStore, ManageBookingsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './series-detail.component.html',
})
export class SeriesDetailComponent {
  protected readonly data = inject<SeriesDetailData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<SeriesResponse>>(DialogRef);
  private readonly store = inject(SeasonsStore);
  private readonly bookings = inject(ManageBookingsStore);
  private readonly dialogs = inject(DialogService);
  private readonly toasts = inject(ToastService);
  private readonly injector = inject(Injector);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  protected readonly series = signal<SeriesResponse | null>(
    typeof this.data.series === 'string' ? null : this.data.series,
  );
  protected readonly sessions = signal<ManagedBookingResponse[]>([]);
  protected readonly sessionsState = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly paying = signal(false);
  protected readonly cancelling = signal<string | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);

  protected readonly place = computed(() => {
    const series = this.series();
    return series ? placeParts(series).join(' · ') : '';
  });
  protected readonly payments = computed(() => {
    this.langChanges();
    return paymentOptions(this.t);
  });
  protected readonly rows = computed<ValueListItem[]>(() => {
    this.langChanges();
    const series = this.series();
    return series ? seriesRows(series, this.language.lang(), this.t) : [];
  });
  protected readonly sessionsHeading = computed(() => {
    this.langChanges();
    const count = this.sessions().length;
    return this.sessionsState() === 'ready'
      ? this.t('booking.seasons.detail.upcomingCount', { count })
      : this.t('booking.seasons.detail.upcoming');
  });

  constructor() {
    void this.load(this.data.series);
  }

  protected day(booking: ManagedBookingResponse): string {
    return formatDay(inMadrid(booking.start).date, this.language.lang(), 'long');
  }

  protected time(booking: ManagedBookingResponse): string {
    return `${inMadrid(booking.start).time}–${inMadrid(booking.end).time}`;
  }

  protected async setPayment(series: SeriesResponse, status: SeriesPayment): Promise<void> {
    this.problem.set(null);
    this.paying.set(true);
    try {
      this.update(await this.store.setPayment(this.data.slug, series, status));
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.paying.set(false);
    }
  }

  /** «Cancelar a sesión»: o diálogo de sempre coa reserva da sesión e as súas canles. */
  protected async cancelSession(series: SeriesResponse, session: ManagedBookingResponse): Promise<void> {
    this.problem.set(null);
    this.cancelling.set(session.id);
    try {
      const booking = await this.store.booking(this.data.slug, session.id);
      this.cancelling.set(null);
      const data: CancelBookingData = {
        booking,
        cancel: (channels) => this.bookings.cancel(this.data.slug, booking, channels),
      };
      const ref = this.dialogs.open<boolean | { failed: unknown }, CancelBookingData, CancelBookingDialogComponent>(
        CancelBookingDialogComponent,
        { data, injector: this.injector, autoFocus: '[data-cancel-keep]' },
      );
      const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
      if (result === true) {
        this.toasts.show('booking.seasons.sessionCancelled');
        this.sessions.update((items) => items.filter((item) => item.id !== session.id));
        this.update(await this.store.getSeries(this.data.slug, series.id));
      } else if (typeof result === 'object') {
        this.problem.set(asProblem(result.failed));
      }
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.cancelling.set(null);
    }
  }

  /** «Rematar a tempada…»: desde que día; feito, o detalle mostra desde cando. */
  protected async end(series: SeriesResponse): Promise<void> {
    this.problem.set(null);
    const data: EndSeriesData = {
      series,
      slug: this.data.slug,
      sessions: this.sessionsState() === 'ready' ? this.sessions().map((session) => session.start) : undefined,
    };
    const ref = this.dialogs.open<SeriesResponse, EndSeriesData, EndSeriesDialogComponent>(EndSeriesDialogComponent, {
      data,
      injector: this.injector,
    });
    const ended = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (!ended) return;
    this.toasts.show('booking.seasons.ended', { count: ended.cancelled - series.cancelled });
    this.update(ended);
    this.sessions.set([]);
  }

  private update(series: SeriesResponse): void {
    this.series.set(series);
    this.data.changed?.(series);
  }

  /** A tempada (se só vén o id) e as súas sesións que quedan. */
  private async load(given: SeriesResponse | string): Promise<void> {
    try {
      const series = typeof given === 'string' ? await this.store.getSeries(this.data.slug, given) : given;
      this.series.set(series);
      if (series.endedFrom) return;
      this.sessions.set(await this.store.sessions(this.data.slug, series, madridToday()));
      this.sessionsState.set('ready');
    } catch (error) {
      if (this.series()) this.sessionsState.set('error');
      else this.problem.set(asProblem(error));
    }
  }
}
