// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { ManagedBookingDetailComponent } from './booking-detail.component';
import type { FacilitySummaryResponse } from './manage-bookings.store';

export interface BookingDetailDialogData {
  readonly facility: FacilitySummaryResponse | null;
}

/** O detalle dunha reserva no móbil (debaixo de 1024 px non cabe ao lado): o mesmo, nun diálogo. */
@Component({
  selector: 'furar-booking-detail-dialog',
  imports: [DialogComponent, ManagedBookingDetailComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog size="sm" [heading]="'booking.manage.detail.title' | transloco">
      <furar-managed-booking-detail [facility]="data.facility" />
    </furar-dialog>
  `,
})
export class BookingDetailDialogComponent {
  protected readonly data = inject<BookingDetailDialogData>(DIALOG_DATA);
}
