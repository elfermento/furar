// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';
import { formatInstant } from '../../../shared/ui/calendar/madrid-time';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { ManagedBookingDetailResponse } from './manage-bookings';

/**
 * O contacto no detalle dunha reserva: o correo e, nas que fixo o persoal, o teléfono; debaixo, o
 * último correo que saíu («Confirmación enviada o …») ou, sen correo, que hai que darlle o código
 * (maqueta 58).
 */
@Component({
  selector: 'furar-booking-contact',
  host: { class: 'contents' },
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let d = detail();
    @if (d.holderEmail || d.holderPhone || note()) {
      <p class="f-mdetail__contact">
        @if (d.holderEmail; as email) {
          <a [href]="'mailto:' + email" data-detail-email>
            <furar-icon name="email" />
            <span>{{ email }}</span>
          </a>
        }
        @if (d.holderPhone; as phone) {
          <a [href]="'tel:' + phone" data-detail-phone>
            <furar-icon name="phone" />
            <span>{{ phone }}</span>
          </a>
        }
        @if (note(); as text) {
          <span class="f-mdetail__soft" data-detail-mail-note>{{ text }}</span>
        }
      </p>
    }
  `,
})
export class BookingContactComponent {
  readonly detail = input.required<ManagedBookingDetailResponse>();

  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly note = computed(() => {
    this.langChanges();
    const d = this.detail();
    const t = (key: string, params: Record<string, unknown>) =>
      this.transloco.translate(`booking.manage.detail.${key}`, params);
    if (d.lastMail) return t(`mail.${d.lastMail.kind}`, { at: formatInstant(d.lastMail.sentAt) });
    if (d.bookedBy && !d.holderEmail && d.holderName) return t('noEmail', { code: d.code });
    return null;
  });
}
