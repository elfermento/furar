// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { formatCents } from '../../../shared/ui/money/money';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { bookingChip } from '../mine/my-bookings';
import type { ManagedBookingResponse } from './manage-bookings';
import { agoText, manageWhen } from './manage-bookings';

/**
 * Reservas nas listas do concello (maqueta 26): cada unha, un botón que abre o detalle
 * (`aria-pressed` mentres está aberto). Á esquerda, quen reserva en negra, «instalación · uso ·
 * parte», o día e a hora con icona e, se se pide, cando se reservou (e quen a cancelou); á
 * dereita, o importe e a etiqueta do pago coa paleta (a mesma das miñas reservas).
 */
@Component({
  selector: 'furar-booking-rows',
  imports: [IconComponent, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="f-manage__rows">
      @for (b of items(); track b.id) {
        <li>
          <button
            type="button"
            class="f-mrow"
            [attr.data-booking]="b.id"
            [attr.aria-pressed]="b.id === selectedId()"
            (click)="picked.emit(b)"
          >
            <span class="f-mrow__main">
              <b class="f-mrow__name">{{ b.holderName ?? ('booking.manage.anonymized' | transloco) }}</b
              >{{ ' ' }} <span class="f-mrow__meta">{{ meta(b) }}</span
              >{{ ' ' }}
              <span class="f-mrow__when">
                <furar-icon name="calendar-month" />
                <span>{{ when(b) }}</span>
              </span>
              @if (showMade()) {
                {{ ' ' }}<span class="f-mrow__made" data-row-made>{{ made(b) }}</span>
              }
            </span>
            {{ ' ' }}
            <span class="f-mrow__end">
              @if (amount(b); as value) {
                <span class="f-mrow__amount">{{ value }}</span
                >{{ ' ' }}
              }
              @if (chip(b); as c) {
                <furar-tag [tone]="c.tone" [look]="c.look" [attr.data-chip]="c.key">{{
                  'booking.mine.status.' + c.key | transloco
                }}</furar-tag>
              }
            </span>
          </button>
        </li>
      }
    </ul>
  `,
})
export class BookingRowsComponent {
  readonly items = input.required<readonly ManagedBookingResponse[]>();
  /** Hoxe en Europe/Madrid («hoxe», «mañá»). */
  readonly today = input.required<IsoDate>();
  /** A reserva aberta no detalle. */
  readonly selectedId = input<string | null>(null);
  /** Engade cando se reservou («Reservada hai 5 min») e quen a cancelou. */
  readonly showMade = input(false);
  readonly picked = output<ManagedBookingResponse>();

  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  /** Para que os textos calculados cambien co idioma. */
  protected readonly lang = toSignal(this.transloco.langChanges$);

  protected meta(b: ManagedBookingResponse): string {
    return [b.facilityName, b.useName, b.partName].filter(Boolean).join(' · ');
  }

  protected when(b: ManagedBookingResponse): string {
    this.lang();
    const t = (key: string) => this.transloco.translate(`booking.manage.${key}`);
    return manageWhen(b, this.language.lang(), this.today(), { today: t('today'), tomorrow: t('tomorrow') });
  }

  protected made(b: ManagedBookingResponse): string {
    this.lang();
    const parts = [
      this.transloco.translate('booking.manage.reserved', { ago: agoText(b.reservedAt, this.language.lang()) }),
    ];
    if (b.status === 'CANCELLED_BY_MUNICIPALITY')
      parts.push(this.transloco.translate('booking.mine.cancelledByMunicipality'));
    if (b.status === 'CANCELLED_BY_PERSON') parts.push(this.transloco.translate('booking.mine.cancelledByPerson'));
    return parts.join(' · ');
  }

  protected amount(b: ManagedBookingResponse): string | null {
    return b.price.kind === 'PAID' && typeof b.price.amountCents === 'number' ? formatCents(b.price.amountCents) : null;
  }

  protected chip(b: ManagedBookingResponse) {
    return bookingChip(b);
  }
}
