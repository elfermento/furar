// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { searchingChip } from '../../../shared/open-matches/open-match-format';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import type { StatusCardLine } from '../../../shared/ui/status-card/status-card.component';
import { StatusCardComponent } from '../../../shared/ui/status-card/status-card.component';
import { StarsComponent } from '../../../shared/ui/stars/stars.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { formatDay } from '../reserve/reserve';
import type { BookingWaitResponse } from '../waits/waits.store';
import * as mine from './my-bookings';

/** O día e a hora, por separado: «martes, 27 de outubro» e «18:00–19:00», en Europe/Madrid. */
export function bookingWhenParts(booking: Pick<MyBookingResponse, 'start' | 'end'>, lang: string): [string, string] {
  const start = inMadrid(booking.start);
  const end = inMadrid(booking.end);
  return [formatDay(start.date, lang, 'long'), `${start.time}–${end.time}`];
}

/** «martes, 27 de outubro · 18:00–19:00», en Europe/Madrid. */
export function bookingWhen(booking: Pick<MyBookingResponse, 'start' | 'end'>, lang: string): string {
  return bookingWhenParts(booking, lang).join(' · ');
}

/**
 * Tarxeta dunha reserva en «As miñas reservas» sobre `furar-status-card`: enteira
 * pulsable (abre o detalle, `opened`), sen botóns; o nome, o importe arriba á dereita, a data con
 * icona, o uso e a parte (e para quen, as prazas e quen a cancelou) e a fila de chips: o estado do
 * pago, «Non presentado» e, se xa a valorou, as estrelas. Con `wait` no canto de `booking`, a
 * espera (ADR-0033): o chip «En espera» (gris) coa súa nota. Se busca xente (ADR-0040), o chip
 * «Buscando xente» (azul) coa nota «4 de 10».
 *
 * Chips doutras funcións: proxéctanse co atributo `cardChips` e van despois dos da reserva.
 */
@Component({
  selector: 'furar-booking-card',
  imports: [StarsComponent, StatusCardComponent, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-mybooking-card',
    '[attr.data-booking]': 'booking()?.id',
    '[attr.data-wait]': 'wait()?.id',
    '[attr.data-past]': 'past() || null',
  },
  template: `
    @let t = 'booking.mine.';
    <furar-status-card
      [heading]="facilityName()"
      [amount]="amount()"
      [lines]="lines()"
      [cover]="cover()"
      [past]="past()"
      (opened)="opened.emit()"
    >
      @if (wait()) {
        <span cardChips class="f-status-card__group">
          <furar-tag tone="muted" look="solid" data-chip="waiting">{{ t + 'wait.chip' | transloco }}</furar-tag>
          <span class="f-status-card__note">{{ t + 'wait.note' | transloco }}</span>
        </span>
      }
      @if (searching(); as s) {
        <span cardChips class="f-status-card__group">
          <furar-tag tone="info" look="solid" data-chip="searching">{{ 'openMatch.chip' | transloco }}</furar-tag>
          <span class="f-status-card__note">{{
            'openMatch.ofTotal' | transloco: { total: s.total, maximum: s.maximum }
          }}</span>
        </span>
      }
      @if (chip(); as c) {
        <furar-tag cardChips [tone]="c.tone" [attr.data-chip]="c.key">{{
          t + 'status.' + c.key | transloco
        }}</furar-tag>
      }
      @if (attendance(); as a) {
        <furar-tag cardChips [tone]="a.tone" [attr.data-attendance]="a.key">{{
          'booking.attendance.' + a.key | transloco
        }}</furar-tag>
      }
      @if (booking()?.reviewStars; as stars) {
        <furar-stars cardChips data-booking-reviewed [value]="stars" />
      }
      <ng-container ngProjectAs="[cardChips]"><ng-content select="[cardChips]" /></ng-container>
    </furar-status-card>
  `,
})
export class BookingCardComponent {
  /** A reserva; ou, na variante de espera, `wait`. */
  readonly booking = input<MyBookingResponse | null>(null);
  /** Unha espera no canto dunha reserva: a mesma tarxeta co chip «En espera». */
  readonly wait = input<BookingWaitResponse | null>(null);
  /** Premeu a tarxeta: abrir o detalle. */
  readonly opened = output();

  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  /** O que se mostra: a reserva ou a espera. */
  private readonly shown = computed(() => this.wait() ?? this.booking());
  protected readonly cover = computed(() => this.shown()?.cover ?? null);
  protected readonly facilityName = computed(() => this.shown()?.facilityName ?? '');
  /** A data con icona e, debaixo en gris, o uso e a parte (nas prazas, «Marta e Uxía · 2 prazas»). */
  protected readonly lines = computed<StatusCardLine[]>(() => {
    this.langChanges();
    const shown = this.shown();
    if (!shown) return [];
    const booking = this.booking();
    const meta = booking
      ? mine.bookingMetaParts(booking, (key, params) => this.transloco.translate(key, params))
      : [shown.useName];
    return [
      { id: 'when', icon: 'calendar-month', parts: bookingWhenParts(shown, this.language.lang()) },
      { id: 'meta', soft: true, parts: meta },
    ];
  });
  protected readonly amount = computed(() => mine.bookingAmount(this.booking()));
  protected readonly chip = computed(() => {
    const booking = this.booking();
    return booking ? mine.bookingChip(booking) : null;
  });
  /** «Non presentado» no historial; a validación non se lle mostra á persoa. */
  protected readonly attendance = computed(() => {
    const booking = this.booking();
    return booking ? mine.attendanceChip(booking) : null;
  });
  /** «Buscando xente» (ADR-0040): a reserva busca xente e vese na portada. */
  protected readonly searching = computed(() => {
    const booking = this.booking();
    return booking && !mine.isPast(booking) ? searchingChip(booking.openMatch) : null;
  });
  /** Realizada ou cancelada: historial. */
  protected readonly past = computed(() => {
    const booking = this.booking();
    return !!booking && mine.isPast(booking);
  });
}
