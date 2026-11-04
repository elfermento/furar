// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { localized } from '../../../shared/custom-fields/localized';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import type { ConfirmDetail } from '../../../shared/ui/dialog/confirm.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { registerSymbols } from '../../../shared/ui/icon/icon.component';
import { LAZY_SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TabDirective, TabsComponent } from '../../../shared/ui/tabs/tabs.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { formatDay } from '../reserve/reserve';
import { BookingCardComponent, bookingWhen } from './booking-card.component';
import type { BookingQrData } from './booking-qr-dialog.component';
import { BookingQrDialogComponent } from './booking-qr-dialog.component';
import type { BookingScope } from './my-bookings';
import type { NoteResponse } from './my-bookings.store';
import { MyBookingsStore } from './my-bookings.store';

// As iconas desta páxina que non van na carga inicial (Cambiar, Ver QR, «Sen custo»).
registerSymbols(LAZY_SYMBOLS);

/**
 * «As miñas reservas» (`/:concello/reservas`, D2-13, maquetas 14 e 15): Próximas e Historial en
 * `furar-tabs`, unha tarxeta por reserva (`furar-booking-card`) e «Ver máis» se hai máis páxinas.
 * Cancelar pregunta en `furar-confirm` estilo A (a reserva coa súa icona e unha liña: «Sen custo»
 * co prazo, ou «Xa está pagada» coa nota de devolución do concello, que vén da API); ben, a
 * tarxeta cambia sen recargar e anúnciase; se a reserva cambiou noutro sitio (412), aviso con
 * «Recargar». «Ver QR» abre `furar-dialog`. Precisa sesión (`personGuard`).
 */
@Component({
  selector: 'furar-my-bookings-page',
  imports: [
    BookingCardComponent,
    ButtonDirective,
    CalloutComponent,
    RouterLink,
    StatusStateComponent,
    TabDirective,
    TabsComponent,
    TranslocoPipe,
  ],
  providers: [MyBookingsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-bookings.page.html',
})
export class MyBookingsPage implements OnInit {
  protected readonly store = inject(MyBookingsStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly scope = signal<BookingScope>('UPCOMING');
  protected readonly list = computed(() => this.store.lists[this.scope()]());
  protected readonly hasMore = computed(() => this.list().items.length < this.list().total);
  protected readonly phone = computed(() => this.store.contact()?.phone ?? null);

  private readonly toasts = inject(ToastService);
  /** 412: a reserva cambiou noutro sitio; aviso con «Recargar». */
  protected readonly stale = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  constructor() {
    // Cada pestana pídese a primeira vez que se abre (e de novo se quedou para recargar).
    effect(() => {
      const scope = this.scope();
      const slug = this.slug();
      if (slug) untracked(() => void this.store.load(slug, scope));
    });
  }

  ngOnInit(): void {
    const slug = this.slug();
    if (slug) this.store.loadMunicipalityTexts(slug);
  }

  protected retry(): void {
    void this.store.load(this.slug(), this.scope(), true);
  }

  protected more(): void {
    void this.store.loadMore(this.slug(), this.scope());
  }

  protected reload(): void {
    this.stale.set(false);
    this.problem.set(null);
    void this.store.load(this.slug(), 'UPCOMING', true);
  }

  protected showQr(booking: MyBookingResponse): void {
    const data: BookingQrData = { booking, when: bookingWhen(booking, this.language.lang()) };
    this.dialogs.open(BookingQrDialogComponent, { data });
  }

  /**
   * Pregunta (estilo A) e cancela coa capa ocupada. O texto da devolución é a nota do concello
   * (`payment-notes`) ou, se van por instalación, a da instalación da reserva; o de Furar só di que
   * queda pendente de devolver.
   */
  protected async cancel(booking: MyBookingResponse): Promise<void> {
    const t = (key: string, params?: Record<string, unknown>) =>
      this.transloco.translate(`booking.mine.cancel.${key}`, params);
    this.stale.set(false);
    this.problem.set(null);
    try {
      const done = await this.dialogs.confirm({
        heading: t('title'),
        confirmLabel: t('confirm'),
        busyLabel: t('busy'),
        cancelLabel: t('keep'),
        tone: 'danger',
        details: this.cancelDetails(booking, await this.store.refundNote(this.slug(), booking.facilityId)),
        action: () => this.store.cancel(this.slug(), booking),
      });
      if (done) this.toasts.show('booking.mine.cancel.done', { name: booking.facilityName });
    } catch (error) {
      const problem = asProblem(error);
      if (problem.status === 412) {
        this.stale.set(true);
      } else {
        this.problem.set(problem);
      }
    }
  }

  private cancelDetails(booking: MyBookingResponse, refundNote: NoteResponse | null): ConfirmDetail[] {
    const lang = this.language.lang();
    const what: ConfirmDetail = {
      icon: 'calendar-month',
      tone: 'primary',
      title: booking.facilityName,
      text: bookingWhen(booking, lang),
    };
    if (booking.paymentStatus === 'PAID') {
      const refund = localized(refundNote, lang);
      const text = [this.transloco.translate('booking.mine.cancel.paid'), refund].filter(Boolean).join(' ');
      return [what, { icon: 'info', tone: 'warn', text }];
    }
    const until = inMadrid(booking.cancelUntil);
    const deadline = this.transloco.translate('booking.reserve.done.at', {
      day: formatDay(until.date, lang, 'long'),
      time: until.time,
    });
    return [
      what,
      {
        icon: 'event-available',
        tone: 'success',
        text: this.transloco.translate('booking.mine.cancel.free', { deadline }),
      },
    ];
  }
}
