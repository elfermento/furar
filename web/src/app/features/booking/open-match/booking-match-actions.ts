// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Injector } from '@angular/core';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { BookingOpenMatchResponse, MyOpenMatchResponse } from '../../../shared/open-matches/open-matches.store';
import { OpenMatches, bookingOpenMatch } from '../../../shared/open-matches/open-matches.store';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { bookingWhen } from '../mine/booking-card.component';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import type { MatchActionId } from './booking-match';
import type { OpenMatchDialogData } from './open-match-dialog.component';
import { OpenMatchDialogComponent } from './open-match-dialog.component';

/**
 * As accións da partida que se escollen no detalle dunha reserva propia (ADR-0040): «Ver a
 * partida» (navega), «Buscar xente para xogar» e «Editar a partida» (o diálogo, co que xa hai se a
 * partida existe: pechada, publicar reábrea) e «Deixar de buscar» (pregunta). Devolve o resumo novo
 * da partida para a tarxeta, ou `null` se non cambiou nada; os erros, a quen chama. Un por páxina.
 */
@Injectable()
export class BookingMatchActions {
  private readonly store = inject(OpenMatches);
  private readonly dialogs = inject(DialogService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly municipality = inject(CurrentMunicipality);

  async run(
    slug: string,
    booking: MyBookingResponse,
    id: MatchActionId,
    injector: Injector,
  ): Promise<BookingOpenMatchResponse | null> {
    const match = booking.openMatch;
    if (id === 'match-see' && match) {
      await this.router.navigate(['/', slug, 'partidas', match.id]);
      return null;
    }
    if (id === 'match-stop' && match) {
      const subject = { title: booking.facilityName, start: booking.start, end: booking.end };
      const stopped = await this.stop(slug, booking.id, match.confirmed, subject);
      return stopped ? bookingOpenMatch(stopped) : null;
    }
    if (id === 'match-open' || id === 'match-edit') return this.edit(slug, booking, injector);
    return null;
  }

  private async edit(
    slug: string,
    booking: MyBookingResponse,
    injector: Injector,
  ): Promise<BookingOpenMatchResponse | null> {
    const existing = booking.openMatch ? await this.store.mine(slug, booking.openMatch.id) : null;
    const data: OpenMatchDialogData = {
      slug,
      bookingId: booking.id,
      seats: booking.seats,
      subheading: [booking.useName, bookingWhen(booking, this.language.lang())].join(' · '),
      cancelHours: this.municipality.value()?.openMatchPolicy?.cancelHours ?? 12,
      existing,
    };
    const ref = this.dialogs.open<MyOpenMatchResponse, OpenMatchDialogData, OpenMatchDialogComponent>(
      OpenMatchDialogComponent,
      { data, sheet: true, injector },
    );
    const saved = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (!saved) return null;
    this.toasts.show(existing?.status === 'OPEN' ? 'openMatch.dialog.saved' : 'openMatch.dialog.published');
    return bookingOpenMatch(saved);
  }

  /**
   * «Deixar de buscar» (tamén na páxina da partida): pregunta e pecha a partida (a
   * reserva e quen xa está seguen). as liñas de detalle (a partida e cando; o
   * que pasa) e o botón principal, non o vermello: non destrúe nada. Devolve a partida pechada, ou
   * `null` se non se pechou.
   */
  async stop(
    slug: string,
    bookingId: string,
    confirmed: number,
    subject: { readonly title: string; readonly start: string; readonly end: string },
  ): Promise<MyOpenMatchResponse | null> {
    const t = (key: string, params?: Record<string, unknown>) =>
      this.transloco.translate(`openMatch.stop.${key}`, params);
    const stopped: { match?: MyOpenMatchResponse } = {};
    const done = await this.dialogs.confirm({
      heading: t('title'),
      details: [
        {
          icon: 'calendar-month',
          tone: 'primary',
          title: subject.title,
          text: bookingWhen(subject, this.language.lang()),
        },
        {
          icon: 'info',
          tone: 'warn',
          text: confirmed > 0 ? t(confirmed === 1 ? 'textOne' : 'text', { count: confirmed }) : t('textNone'),
        },
      ],
      confirmLabel: t('confirm'),
      busyLabel: t('busy'),
      cancelLabel: t('keep'),
      tone: 'primary',
      action: async () => {
        stopped.match = await this.store.stop(slug, bookingId);
      },
    });
    if (!done || !stopped.match) return null;
    this.toasts.show('openMatch.stop.done');
    return stopped.match;
  }
}
