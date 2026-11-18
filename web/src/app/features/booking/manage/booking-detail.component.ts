// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, Injector, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ActionItemComponent, ActionListDirective } from '../../../shared/ui/action-list/action-list.component';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { formatCents } from '../../../shared/ui/money/money';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { namedParticipants } from '../../../shared/dependents/participants';
import type { ValueListItem } from '../../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../../shared/ui/value-list/value-list.component';
import { managedMatchText } from '../open-match/booking-match';
import { ManagedMatchComponent } from './managed-match.component';
import { bookingFieldRows } from '../booking-fields';
import { attendanceChip, bookingChip } from '../mine/my-bookings';
import { dayOfMonth } from '../validate/validate';
import { RouterLink } from '@angular/router';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { formatDay } from '../reserve/reserve';
import { sessionSeriesRow } from '../seasons/seasons';
import { openSeriesDetail } from '../seasons/series-detail.component';
import { ActivitySessionActionsComponent, activitySessionRows } from './activity-session-actions.component';
import { BookingContactComponent } from './booking-contact.component';
import type { CancelBookingData } from '../../../shared/booking-cancel/cancel-booking-dialog.component';
import { CancelBookingDialogComponent } from '../../../shared/booking-cancel/cancel-booking-dialog.component';
import type { ManagedBookingDetailResponse, PaymentStatus } from './manage-bookings';
import { paymentActions } from './manage-bookings';
import type { FacilitySummaryResponse } from './manage-bookings.store';
import { ManageBookingsStore } from './manage-bookings.store';

/** Texto e icona de cada cobro que se pode marcar (o que adianta e o que desfai). */
const PAYMENT_ACTION: Record<PaymentStatus, { key: string; icon: 'payments' | 'undo' }> = {
  PAID: { key: 'markPaid', icon: 'payments' },
  REFUNDED: { key: 'markRefunded', icon: 'undo' },
  PENDING: { key: 'undoPaid', icon: 'undo' },
  TO_REFUND: { key: 'undoRefunded', icon: 'undo' },
  NONE: { key: 'markPaid', icon: 'payments' },
};

/**
 * Detalle dunha reserva do concello, o mesmo deseño ca a tarxeta da reserva:
 * a instalación coa súa miniatura e enderezo, os valores (`furar-value-list`: persoa,
 * uso, parte, día, hora e código), o correo de quen reserva, o total coa etiqueta do pago e as
 * accións: «Marcar como pagada» ou «Marcar como devolta» e o seu desfacer, e «Cancelar
 * reserva» (só a xestión; pide as canles). Cada acción vai coa versión que se ve; ben,
 * anúnciase (`role="status"`); se a reserva cambiou noutro sitio (412), aviso con «Recargar»;
 * calquera outro erro (unha transición que xa non vale, 422) en aviso. Úsao a páxina de reservas
 * (tarxeta ao lado ou diálogo no móbil) e o calendario, co seu `ManageBookingsStore`.
 */
@Component({
  selector: 'furar-managed-booking-detail',
  imports: [
    ActionItemComponent,
    ActivitySessionActionsComponent,
    ActionListDirective,
    BookingContactComponent,
    ButtonDirective,
    CalloutComponent,
    IconComponent,
    ManagedMatchComponent,
    RouterLink,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
    ValueListComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-mdetail', '[attr.data-booking-detail]': 'store.detail()?.id ?? null' },
  templateUrl: './booking-detail.component.html',
})
export class ManagedBookingDetailComponent {
  /** A instalación da reserva (miniatura); sen ela, só o nome e o enderezo. */
  readonly facility = input<FacilitySummaryResponse | null>(null);
  /** Con botón «Pechar» (a tarxeta ao lado; no diálogo xa o pon `furar-dialog`). */
  readonly closable = input(false);
  readonly closed = output();

  protected readonly store = inject(ManageBookingsStore);
  private readonly staff = inject(StaffAccessService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  private readonly toasts = inject(ToastService);
  protected readonly stale = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  /** O cobro que se está a marcar (ou `null`). */
  protected readonly busy = signal<PaymentStatus | null>(null);

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  private readonly detail = computed(() => this.store.detail()?.detail ?? null);

  protected readonly rows = computed<ValueListItem[]>(() => {
    this.langChanges();
    const d = this.detail();
    if (!d) return [];
    const t = (key: string) => this.transloco.translate(`booking.manage.detail.${key}`);
    const start = inMadrid(d.start);
    const end = inMadrid(d.end);
    // Unha sesión dunha actividade (ADR-0041): a actividade, o uso, o día e a hora.
    if (d.activity) return activitySessionRows(d, this.language.lang(), t);
    const rows: ValueListItem[] = [
      {
        id: 'person',
        label: t('person'),
        value: d.holderName ?? this.transloco.translate('booking.manage.anonymized'),
      },
    ];
    // Para quen: os nomes, co ano das persoas a cargo e acompañantes.
    const who = namedParticipants(d.participants);
    if (who.length) rows.push({ id: 'who', label: t('who'), value: who.join(' · ') });
    const seats = d.seats > 1 ? this.transloco.translate('booking.who.seats', { count: d.seats }) : null;
    rows.push({ id: 'use', label: t('use'), value: [d.useName, seats].filter(Boolean).join(' · ') });
    if (d.partName) rows.push({ id: 'part', label: t('part'), value: d.partName });
    rows.push(
      { id: 'day', label: t('day'), value: formatDay(start.date, this.language.lang(), 'long') },
      { id: 'time', label: t('time'), value: `${start.time}–${end.time}` },
      { id: 'code', label: t('code'), value: d.code },
    );
    // Reserva por outra persoa: quen do persoal a fixo e o seu rol de agora.
    if (d.bookedBy) {
      const role = this.transloco.translate(`session.role.${d.bookedBy.role}`);
      rows.push({
        id: 'bookedBy',
        label: t('bookedBy'),
        value: d.bookedBy.name ? `${d.bookedBy.name} · ${role}` : role,
      });
    }
    // Unha sesión dunha tempada (ADR-0039): os días e as sesións da tempada.
    const series = sessionSeriesRow(d.organization?.series, this.language.lang(), (k, p) =>
      this.transloco.translate(k, p),
    );
    if (series) rows.push(series);
    // A partida (ADR-0040): os números, sen nomes; pechada ou cancelada, dío.
    if (d.openMatch) {
      const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
      rows.push({ id: 'match', label: t('openMatch.manage.row'), value: managedMatchText(d.openMatch, t) });
    }
    // Os campos propios da reserva: o persoal ve todos os que teñen valor.
    const translate = (key: string) => this.transloco.translate(key);
    rows.push(...bookingFieldRows(this.store.fields(), d.customFields, this.language.lang(), translate));
    return rows;
  });
  protected readonly amount = computed(() => {
    const price = this.detail()?.price;
    return price?.kind === 'PAID' && typeof price.amountCents === 'number' ? formatCents(price.amountCents) : null;
  });
  protected readonly chip = computed(() => {
    const d = this.detail();
    return d ? bookingChip(d) : null;
  });
  protected readonly attendance = computed(() => {
    const d = this.detail();
    return d ? attendanceChip(d) : null;
  });
  /** «Non presentado»: unha reserva xa rematada e sen marca; Xestión e Recepción. */
  protected readonly canMarkNoShow = computed(
    () => this.staff.canViewBookings() && (this.detail()?.canMarkNoShow ?? false),
  );
  /** «Quitar a falta» ou «Quitar a validación», sen prazo (á parte do «Desfacer» do cobro). */
  protected readonly canUndoAttendance = computed(
    () => this.staff.canViewBookings() && (this.detail()?.attendance ?? 'NONE') !== 'NONE',
  );
  protected readonly attendanceBusy = signal(false);
  /**
   * As faltas que leva a persoa e cando se bloquea, sempre que a reserva teña «non presentado» e o
   * concello penalice: tamén ao reabrir a ficha.
   */
  protected readonly noShowNotice = computed(() => {
    this.langChanges();
    const d = this.detail();
    const standing = d?.attendance === 'NO_SHOW' ? d.noShow : undefined;
    if (!d || !standing) return null;
    const one = standing.faults === 1 ? 'One' : '';
    const params = {
      name: d.holderName ?? this.transloco.translate('booking.manage.anonymized'),
      faults: standing.faults,
      days: standing.windowDays,
      threshold: standing.threshold,
      blockDays: standing.blockDays,
    };
    return standing.blockedUntil
      ? this.transloco.translate(`booking.manage.noShow.blocked${one}`, {
          ...params,
          until: dayOfMonth(standing.blockedUntil, this.language.lang()),
        })
      : this.transloco.translate(`booking.manage.noShow.count${one}`, params);
  });
  protected readonly actions = computed(() => {
    const d = this.detail();
    return d ? paymentActions(d) : [];
  });
  /** «Cambiar» (o mesmo formulario, relleno): quen ve as reservas, mentres está confirmada e por vir. */
  protected readonly canChange = computed(() => {
    const d = this.detail();
    // Unha sesión dunha tempada non se move (ADR-0039, punto 1): cancélase.
    return this.staff.canViewBookings() && !!d?.cancellable && !d.done && !d.organization?.seriesId;
  });
  /** «Ver a tempada»: o detalle da tempada desta sesión (só a xestión, que é quen a ve). */
  protected readonly canSeeSeries = computed(() => !!this.detail()?.organization?.series && this.staff.canManage());
  /** «Cancelar reserva»: só a xestión (STAFF non cancela) e se aínda está confirmada. */
  protected readonly canCancel = computed(() => this.staff.canManage() && (this.detail()?.cancellable ?? false));

  protected paymentKey(target: PaymentStatus): string {
    return PAYMENT_ACTION[target].key;
  }

  protected paymentIcon(target: PaymentStatus): 'payments' | 'undo' {
    return PAYMENT_ACTION[target].icon;
  }

  protected async setPayment(detail: ManagedBookingDetailResponse, target: PaymentStatus): Promise<void> {
    this.reset();
    this.busy.set(target);
    try {
      await this.store.setPayment(this.slug(), detail, target);
      this.toasts.show(`booking.manage.detail.done.${PAYMENT_ACTION[target].key}`);
    } catch (error) {
      this.fail(error);
    } finally {
      this.busy.set(null);
    }
  }

  /** Pregunta as canles e cancela coa capa ocupada; os erros quedan nesta tarxeta. */
  protected async cancel(detail: ManagedBookingDetailResponse): Promise<void> {
    this.reset();
    const data: CancelBookingData = {
      booking: detail,
      cancel: (channels) => this.store.cancel(this.slug(), detail, channels),
    };
    const ref = this.dialogs.open<boolean | { failed: unknown }, CancelBookingData, CancelBookingDialogComponent>(
      CancelBookingDialogComponent,
      { data, injector: this.injector, autoFocus: '[data-cancel-keep]' },
    );
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (result === true) {
      this.toasts.show('booking.manage.detail.done.cancelled');
    } else if (typeof result === 'object') {
      this.fail(result.failed);
    }
  }

  /** O detalle da tempada da sesión (o seu diálogo, folla no móbil). */
  protected openSeries(detail: ManagedBookingDetailResponse): void {
    const id = detail.organization?.series?.id;
    if (id) openSeriesDetail(this.dialogs, this.injector, { series: id, slug: this.slug() });
  }

  protected async markNoShow(detail: ManagedBookingDetailResponse): Promise<void> {
    await this.attend(() => this.store.markNoShow(this.slug(), detail));
  }

  protected async undoAttendance(detail: ManagedBookingDetailResponse): Promise<void> {
    await this.attend(() => this.store.undoAttendance(this.slug(), detail));
  }

  private async attend(work: () => Promise<unknown>): Promise<void> {
    this.reset();
    this.attendanceBusy.set(true);
    try {
      await work();
    } catch (error) {
      this.fail(error);
    } finally {
      this.attendanceBusy.set(false);
    }
  }

  protected reload(): void {
    this.reset();
    const id = this.store.detail()?.id;
    if (id) void this.store.open(this.slug(), id);
  }

  private reset(): void {
    this.stale.set(false);
    this.problem.set(null);
  }

  protected fail(error: unknown): void {
    const problem = asProblem(error);
    if (problem.status === 412) this.stale.set(true);
    else this.problem.set(problem);
  }
}
