// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { QrComponent } from '../../../shared/ui/qr/qr.component';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';

/** O que mostra a capa: a reserva e o seu día e hora xa formateados. */
export interface BookingQrData {
  readonly booking: MyBookingResponse;
  readonly when: string;
}

/**
 * «Ver QR» (maqueta 14): `furar-dialog` co nome da instalación debaixo do título, o QR co código
 * e o día e a hora. Pecha con «Pechar», Esc ou picar fóra.
 */
@Component({
  selector: 'furar-booking-qr-dialog',
  imports: [DialogComponent, QrComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog size="sm" [heading]="'booking.mine.qr.title' | transloco" [subheading]="data.booking.facilityName">
      <div class="f-mybooking-qr">
        <furar-qr [size]="data.booking.qr.size" [path]="data.booking.qr.path" [code]="data.booking.code" />
        <b class="f-mybooking-qr__code" data-qr-code>{{ data.booking.code }}</b>
        <p>{{ data.when }}</p>
      </div>
    </furar-dialog>
  `,
})
export class BookingQrDialogComponent {
  protected readonly data = inject<BookingQrData>(DIALOG_DATA);
}
