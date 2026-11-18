// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { ButtonDirective } from '../ui/button/button.directive';
import { inMadrid } from '../ui/calendar/madrid-time';
import { DialogComponent } from '../ui/dialog/dialog.component';
import { IconComponent } from '../ui/icon/icon.component';
import { SwitchComponent } from '../ui/switch/switch.component';
import { formatDay, matchCancelNotice } from './booking-cancel';
import type { CancelChannel, ManagedBookingDetailResponse } from './booking-cancel.store';
import { BookingCancelStore } from './booking-cancel.store';

export type { CancelChannel };

export interface CancelBookingData {
  readonly booking: ManagedBookingDetailResponse;
  /**
   * Como se cancela (p. ex. o almacén das reservas, que ademais refresca a lista); sen el, a
   * chamada á API.
   */
  readonly cancel?: (channels: CancelChannel[]) => Promise<unknown>;
}

/** Orde das canles e a marcada ao abrir (o correo). */
const CHANNELS: readonly CancelChannel[] = ['EMAIL', 'WEB'];
const CHECKED: readonly CancelChannel[] = ['EMAIL'];

/**
 * «Cancelar reserva» desde o detalle: a reserva (estilo A, texto con icona), que pasa co
 * pago se xa estaba pagada, e as canles polas que avisar, un interruptor por fila (Correo acendido;
 * Aviso na web).
 * Cunha partida (ADR-0040), avisa de que se cancela tamén e de cantas persoas apuntadas se avisan.
 * Unha sesión dunha tempada (ADR-0039) pregunta «Cancelar a sesión?»: o resto, igual.
 * Sen motivo escrito na fase 1. Cancela coa capa ocupada; pecha con `true` ou co erro.
 */
@Component({
  selector: 'furar-cancel-booking-dialog',
  imports: [ButtonDirective, DialogComponent, IconComponent, ReactiveFormsModule, SwitchComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.manage.cancel.';
    @let b = data.booking;
    @let session = !!b.organization?.seriesId;
    <furar-dialog size="sm" [heading]="t + (session ? 'sessionTitle' : 'title') | transloco" [busy]="busy()">
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
        @if (match; as m) {
          <p class="f-confirm__detail" data-tone="warn" data-cancel-match>
            <furar-icon [name]="m.icon" />
            <span>{{ m.text }}</span>
          </p>
        }
      </div>
      <!-- Sen correo nin conta (reserva do persoal), non hai por onde avisar. -->
      @if (offered().length) {
        <fieldset class="f-mcancel__channels">
          <legend>{{ t + 'channels' | transloco }}</legend>
          @for (channel of offered(); track channel) {
            <furar-switch
              [attr.data-channel]="channel"
              [formControl]="channels.controls[channel]"
              [label]="t + 'channel.' + channel | transloco"
            />
          }
        </fieldset>
      }
      <ng-container dialogActions>
        <button type="button" furarButton data-cancel-keep (click)="ref.close(false)">
          {{ t + 'keep' | transloco }}
        </button>
        <button type="button" furarButton="danger" data-cancel-confirm [busy]="busy()" (click)="confirm()">
          {{ t + (busy() ? 'busy' : session ? 'sessionConfirm' : 'confirm') | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class CancelBookingDialogComponent {
  protected readonly data = inject<CancelBookingData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<boolean | { failed: unknown }>>(DialogRef);
  private readonly bookings = inject(BookingCancelStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);

  protected readonly offered = computed(() => CHANNELS.filter((c) => this.data.booking.cancelChannels.includes(c)));
  /** Un interruptor por canle (un por fila): Correo acendido ao abrir. */
  protected readonly channels = new FormGroup({
    EMAIL: new FormControl(CHECKED.includes('EMAIL'), { nonNullable: true }),
    WEB: new FormControl(CHECKED.includes('WEB'), { nonNullable: true }),
  });
  protected readonly busy = signal(false);
  /** Cunha partida (ADR-0040): cancélase tamén e avísase ás persoas apuntadas. */
  private readonly transloco = inject(TranslocoService);
  protected readonly match = matchCancelNotice(this.data.booking.openMatch, (key, params) =>
    this.transloco.translate(key, params),
  );
  protected readonly when = computed(() => {
    const start = inMadrid(this.data.booking.start);
    const end = inMadrid(this.data.booking.end);
    return `${formatDay(start.date, this.language.lang(), 'long')} · ${start.time}–${end.time}`;
  });

  constructor() {
    // Mentres cancela, os interruptores non se tocan.
    effect(() => (this.busy() ? this.channels.disable() : this.channels.enable()));
  }

  /** As canles acendidas, na orde de sempre. */
  private checked(): CancelChannel[] {
    return this.offered().filter((channel) => this.channels.controls[channel].value);
  }

  protected async confirm(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      const channels = this.checked();
      const booking = this.data.booking;
      await (this.data.cancel
        ? this.data.cancel(channels)
        : this.bookings.cancel(this.municipality.slug() ?? '', booking, channels));
      this.ref.close(true);
    } catch (failed) {
      this.ref.close({ failed });
    }
  }
}
