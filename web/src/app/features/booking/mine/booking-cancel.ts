// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { localized } from '../../../shared/custom-fields/localized';
import type { ConfirmDetail } from '../../../shared/ui/dialog/confirm.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { matchCancelNotice } from '../open-match/booking-match';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { formatDay } from '../reserve/reserve';
import { bookingWhen } from './booking-card.component';
import type { NoteResponse } from './my-bookings.store';
import { MyBookingsStore } from './my-bookings.store';

/**
 * Cancelar unha reserva propia (en «As miñas reservas» e na páxina da partida que organiza):
 * pregunta en `furar-confirm` estilo A (a reserva coa súa icona e unha liña: «Sen
 * custo» co prazo, ou «Xa está pagada» coa nota de devolución do concello; con partida, o aviso ás
 * apuntadas) e cancela coa capa ocupada. Ben, anúnciao. Os erros (412, fóra de prazo…), a quen
 * chama. Vai co `MyBookingsStore` da páxina.
 *
 * Unha sesión dunha tempada (ADR-0039; «Eu · ‹entidade›») pregunta «Cancelar a sesión?»: o resto,
 * igual. A entidade cancela polo seu camiño (`cancel`).
 */
@Injectable()
export class BookingCancel {
  private readonly store = inject(MyBookingsStore);
  private readonly dialogs = inject(DialogService);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

  /** Pregunta e cancela (por defecto, como reserva propia); `true` se se cancelou. */
  async confirm(
    slug: string,
    booking: MyBookingResponse,
    cancel: () => Promise<unknown> = () => this.store.cancel(slug, booking),
  ): Promise<boolean> {
    const t = (key: string, params?: Record<string, unknown>) =>
      this.transloco.translate(`booking.mine.cancel.${key}`, params);
    const session = !!booking.organization?.seriesId;
    const done = await this.dialogs.confirm({
      heading: t(session ? 'sessionTitle' : 'title'),
      confirmLabel: t(session ? 'sessionConfirm' : 'confirm'),
      busyLabel: t('busy'),
      cancelLabel: t('keep'),
      tone: 'danger',
      details: this.details(booking, await this.store.refundNote(slug, booking.facilityId)),
      action: cancel,
    });
    if (done) this.toasts.show('booking.mine.cancel.done', { name: booking.facilityName });
    return done;
  }

  private details(booking: MyBookingResponse, refundNote: NoteResponse | null): ConfirmDetail[] {
    const lang = this.language.lang();
    const what: ConfirmDetail = {
      icon: 'calendar-month',
      tone: 'primary',
      title: booking.facilityName,
      text: bookingWhen(booking, lang),
    };
    const notice = matchCancelNotice(booking.openMatch, (key, params) => this.transloco.translate(key, params));
    const match = notice ? [notice] : [];
    if (booking.paymentStatus === 'PAID') {
      const refund = localized(refundNote, lang);
      const text = [this.transloco.translate('booking.mine.cancel.paid'), refund].filter(Boolean).join(' ');
      return [what, { icon: 'info', tone: 'warn', text }, ...match];
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
      ...match,
    ];
  }
}
