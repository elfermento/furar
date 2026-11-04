// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { formatDay } from '../reserve/reserve';
import type { CancelChannel, ManagedBookingDetailResponse } from './manage-bookings';
import { ManageBookingsStore } from './manage-bookings.store';

export interface CancelBookingData {
  readonly booking: ManagedBookingDetailResponse;
}

/** Orde das canles e a marcada ao abrir (P12: o correo). */
const CHANNELS: readonly CancelChannel[] = ['EMAIL', 'WEB'];
const CHECKED: readonly CancelChannel[] = ['EMAIL'];

/**
 * «Cancelar reserva» desde o detalle (P12): a reserva (estilo A, texto con icona), que pasa co
 * pago se xa estaba pagada, e as canles polas que avisar, o mesmo bloque ca o modal de reservas
 * afectadas pero dunha soa reserva (Correo marcado; Aviso na web).
 * Sen motivo escrito na fase 1. Cancela coa capa ocupada; pecha con `true` ou co erro.
 */
@Component({
  selector: 'furar-cancel-booking-dialog',
  imports: [ButtonDirective, DialogComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.manage.cancel.';
    @let b = data.booking;
    <furar-dialog size="sm" [heading]="t + 'title' | transloco" [busy]="busy()">
      <div class="f-confirm__details">
        <p class="f-confirm__detail" data-tone="primary">
          <furar-icon name="calendar-month" />
          <span>
            <b>{{ b.holderName }}</b>
            {{ b.facilityName }} · {{ when() }}
          </span>
        </p>
        @if (b.paymentStatus === 'PAID') {
          <p class="f-confirm__detail" data-tone="warn" data-cancel-paid>
            <furar-icon name="info" />
            <span>{{ t + 'paid' | transloco }}</span>
          </p>
        }
      </div>
      <fieldset class="f-mcancel__channels" [disabled]="busy()">
        <legend>{{ t + 'channels' | transloco }}</legend>
        @for (channel of offered(); track channel) {
          <label class="f-mcancel__channel">
            <input
              type="checkbox"
              [attr.data-channel]="channel"
              [checked]="checked().includes(channel)"
              (change)="toggle(channel)"
            />
            {{ t + 'channel.' + channel | transloco }}
          </label>
        }
      </fieldset>
      <ng-container dialogActions>
        <button type="button" furarButton data-cancel-keep (click)="ref.close(false)">
          {{ t + 'keep' | transloco }}
        </button>
        <button type="button" furarButton="danger" data-cancel-confirm [busy]="busy()" (click)="confirm()">
          {{ (busy() ? t + 'busy' : t + 'confirm') | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class CancelBookingDialogComponent {
  protected readonly data = inject<CancelBookingData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<boolean | { failed: unknown }>>(DialogRef);
  private readonly store = inject(ManageBookingsStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);

  protected readonly offered = computed(() => CHANNELS.filter((c) => this.data.booking.cancelChannels.includes(c)));
  protected readonly checked = signal<CancelChannel[]>(CHECKED.filter((c) => this.offered().includes(c)));
  protected readonly busy = signal(false);
  protected readonly when = computed(() => {
    const start = inMadrid(this.data.booking.start);
    const end = inMadrid(this.data.booking.end);
    return `${formatDay(start.date, this.language.lang(), 'long')} · ${start.time}–${end.time}`;
  });

  protected toggle(channel: CancelChannel): void {
    this.checked.update((list) =>
      list.includes(channel)
        ? list.filter((c) => c !== channel)
        : CHANNELS.filter((c) => c === channel || list.includes(c)),
    );
  }

  protected async confirm(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      await this.store.cancel(this.municipality.slug() ?? '', this.data.booking, this.checked());
      this.ref.close(true);
    } catch (failed) {
      this.ref.close({ failed });
    }
  }
}
