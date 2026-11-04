// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { formatCents } from '../../../shared/ui/money/money';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { formatDay } from '../reserve/reserve';
import { bookingChip, canChange, isCancelled, pastDeadline } from './my-bookings';

let nextId = 0;

/** «martes, 27 de outubro · 18:00–19:00», en Europe/Madrid. */
export function bookingWhen(booking: MyBookingResponse, lang: string): string {
  const start = inMadrid(booking.start);
  const end = inMadrid(booking.end);
  return `${formatDay(start.date, lang, 'long')} · ${start.time}–${end.time}`;
}

/**
 * Tarxeta dunha reserva (maqueta 14): a miniatura da instalación (decorativa), o nome en negra, a data con icona, o uso e a parte (e quen
 * a cancelou); o prezo e a etiqueta do pago na súa liña (á dereita no escritorio); as accións
 * noutra: Cambiar (`quiet`, á páxina de reservar en modo cambio), Cancelar (`danger`) e Ver QR, ou
 * «Reservar de novo» no historial. Fóra do prazo para cancelar (P1) non hai Cambiar nin Cancelar
 * e sae o teléfono do servizo de deportes. Os botóns repetidos levan o nome da instalación como
 * descrición (`aria-describedby`).
 */
@Component({
  selector: 'furar-booking-card',
  imports: [ButtonDirective, IconComponent, RouterLink, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-card f-mybooking',
    '[attr.data-booking]': 'booking().id',
    '[attr.data-past]': 'past() || null',
  },
  template: `
    @let t = 'booking.mine.';
    @let b = booking();
    @if (b.cover; as cover) {
      <img
        class="f-mybooking__img"
        data-booking-cover
        [src]="cover.thumb"
        [width]="cover.width"
        [height]="cover.height"
        alt=""
        loading="lazy"
      />
    }
    <div class="f-mybooking__body">
      <h2 class="f-mybooking__name" [id]="headingId">{{ b.facilityName }}</h2>
      <p class="f-mybooking__when" data-booking-when>
        <furar-icon name="calendar-month" />
        <span>{{ when() }}</span>
      </p>
      <p class="f-mybooking__meta" data-booking-meta>{{ meta() }}</p>
      @if (amount() || chip()) {
        <p class="f-mybooking__money" data-booking-money>
          @if (amount(); as value) {
            <b>{{ value }}</b>
          }
          @if (chip(); as c) {
            <furar-tag [tone]="c.tone" [look]="c.look" [attr.data-chip]="c.key">{{
              t + 'status.' + c.key | transloco
            }}</furar-tag>
          }
        </p>
      }
      @if (late()) {
        <p class="f-mybooking__late" data-booking-late>
          <furar-icon name="info" />
          <span>
            {{ t + 'late.text' | transloco }}
            @if (phone(); as tel) {
              {{ t + 'late.call' | transloco }}
              <a [href]="'tel:' + telHref()">{{ tel }}</a>
            }
          </span>
        </p>
      }
      <div class="f-mybooking__acts">
        @if (changeable()) {
          <a
            furarButton="quiet"
            [routerLink]="reserveLink()"
            [queryParams]="{ cambiar: b.id }"
            [attr.aria-describedby]="headingId"
            data-booking-change
          >
            <furar-icon name="edit-calendar" />
            {{ t + 'actions.change' | transloco }}
          </a>
          <button
            type="button"
            furarButton="danger"
            [attr.aria-describedby]="headingId"
            data-booking-cancel
            (click)="cancelBooking.emit(b)"
          >
            <furar-icon name="event-busy" />
            {{ t + 'actions.cancel' | transloco }}
          </button>
        }
        @if (!past()) {
          <button
            type="button"
            furarButton
            [attr.aria-describedby]="headingId"
            data-booking-qr
            (click)="showQr.emit(b)"
          >
            <furar-icon name="qr" />
            {{ t + 'actions.qr' | transloco }}
          </button>
        } @else {
          <a
            furarButton
            [routerLink]="reserveLink()"
            [queryParams]="againParams()"
            [attr.aria-describedby]="headingId"
            data-booking-again
          >
            <furar-icon name="event-repeat" />
            {{ t + 'actions.again' | transloco }}
          </a>
        }
      </div>
    </div>
  `,
})
export class BookingCardComponent {
  readonly booking = input.required<MyBookingResponse>();
  /** Concello do URL (as ligazóns á páxina de reservar). */
  readonly slug = input.required<string>();
  /** Teléfono do servizo de deportes (fóra de prazo); sen el, só o aviso. */
  readonly phone = input<string | null>(null);
  readonly cancelBooking = output<MyBookingResponse>();
  readonly showQr = output<MyBookingResponse>();

  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  protected readonly headingId = `furar-booking-${nextId++}`;

  protected readonly when = computed(() => bookingWhen(this.booking(), this.language.lang()));
  /** «Fútbol 7 · Metade sur · cancelada polo concello». */
  protected readonly meta = computed(() => {
    this.langChanges();
    const booking = this.booking();
    const who =
      booking.status === 'CANCELLED_BY_MUNICIPALITY'
        ? 'booking.mine.cancelledByMunicipality'
        : booking.status === 'CANCELLED_BY_PERSON'
          ? 'booking.mine.cancelledByPerson'
          : null;
    return [booking.useName, booking.partName, who ? this.transloco.translate(who) : null].filter(Boolean).join(' · ');
  });
  protected readonly amount = computed(() => {
    const price = this.booking().price;
    return price.kind === 'PAID' && typeof price.amountCents === 'number' ? formatCents(price.amountCents) : null;
  });
  protected readonly chip = computed(() => bookingChip(this.booking()));
  /** Realizada ou cancelada: historial («Reservar de novo»). */
  protected readonly past = computed(() => this.booking().done || isCancelled(this.booking()));
  protected readonly changeable = computed(() => canChange(this.booking()));
  protected readonly late = computed(() => pastDeadline(this.booking()));
  protected readonly telHref = computed(() => (this.phone() ?? '').replace(/[^\d+]/g, ''));
  protected readonly reserveLink = computed(() => [
    '/',
    this.slug(),
    'instalacions',
    this.booking().facilityId,
    'reservar',
  ]);
  protected readonly againParams = computed(() => ({
    uso: this.booking().useId,
    parte: this.booking().partId ?? null,
  }));
}
