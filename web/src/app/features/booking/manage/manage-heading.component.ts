// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { ManageNav } from '../../../core/layout/manage-nav';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { ExportBookingsData } from './export-bookings-dialog.component';
import { ExportBookingsDialogComponent } from './export-bookings-dialog.component';
import type { BookingFilters } from './manage-bookings';
import { NO_FILTERS } from './manage-bookings';

/**
 * Cabeceira de «Reservas»: o título e as accións, á dereita. Co apartado en pestanas
 * (Reservas · Validar QR…), o título só para lectores de pantalla, coma en Concello. Accións:
 * «Descargar CSV» (secundario; só a xestión, o mostrador non exporta; no móbil, só a icona), que
 * abre o seu diálogo cos filtros da lista, e «Nova reserva» (primario),
 * que ven a xestión e Recepción: as dúas reservan no nome de alguén.
 */
@Component({
  selector: 'furar-manage-heading',
  imports: [ButtonDirective, IconComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-manage__head', '[attr.data-under-tabs]': 'underTabs() ? "" : null' },
  template: `
    @let t = 'booking.manage.';
    <h1 [class.sr-only]="underTabs()">{{ t + 'title' | transloco }}</h1>
    <div class="f-manage__actions">
      @if (staff.canManage()) {
        <button
          type="button"
          furarButton="secondary"
          class="f-manage__tool--icon-mobile"
          aria-haspopup="dialog"
          data-manage-export
          [attr.aria-label]="t + 'export.title' | transloco"
          (click)="openExport()"
        >
          <furar-icon name="download" />
          <span class="f-manage__tool-label">{{ t + 'export.title' | transloco }}</span>
        </button>
      }
      @if (staff.canViewBookings()) {
        <a furarButton="primary" data-manage-new [routerLink]="['/', slug(), 'xestion', 'reservas', 'nova']">
          <furar-icon name="plus" />
          {{ t + 'new' | transloco }}
        </a>
      }
    </div>
  `,
})
export class ManageHeadingComponent {
  readonly slug = input.required<string>();
  /** Os filtros da lista: «Descargar CSV» empeza con eles. */
  readonly filters = input<BookingFilters>(NO_FILTERS);
  readonly facilities = input<readonly { readonly id: string; readonly name: string }[]>([]);

  protected readonly staff = inject(StaffAccessService);
  protected readonly underTabs = inject(ManageNav).underTabs;
  private readonly dialogs = inject(DialogService);

  protected openExport(): void {
    const data: ExportBookingsData = {
      slug: this.slug(),
      filters: this.filters(),
      facilities: this.facilities().map((f) => ({ id: f.id, name: f.name })),
      today: madridToday(),
    };
    this.dialogs.open<boolean, ExportBookingsData, ExportBookingsDialogComponent>(ExportBookingsDialogComponent, {
      data,
      sheet: true,
    });
  }
}
