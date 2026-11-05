// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { ExportBookingsData } from './export-bookings-dialog.component';
import { ExportBookingsDialogComponent } from './export-bookings-dialog.component';
import type { BookingFilters } from './manage-bookings';
import { NO_FILTERS } from './manage-bookings';

/** Espera da busca mentres se escribe. */
const SEARCH_DELAY_MS = 300;

/**
 * Barra das reservas do concello (maqueta 26): a busca en cápsula (nome, correo ou código; avisa despois
 * dunha pausa ao escribir), «Filtros» co número de filtros postos, «Calendario» (D2-16) e, só para a
 * xestión (o mostrador non exporta), «Descargar CSV» (maqueta 60, opción A; no móbil, só a icona),
 * que abre o seu diálogo cos filtros da lista.
 */
@Component({
  selector: 'furar-bookings-toolbar',
  imports: [ButtonDirective, IconComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-manage__toolbar' },
  template: `
    @let t = 'booking.manage.';
    <label class="f-manage__search">
      <furar-icon name="search" />
      <input
        type="search"
        data-manage-search
        [attr.aria-label]="t + 'search' | transloco"
        [placeholder]="t + 'searchPlaceholder' | transloco"
        [value]="q()"
        (input)="onInput($event)"
      />
    </label>
    <button
      type="button"
      furarButton="secondary"
      class="f-manage__tool"
      aria-haspopup="dialog"
      data-manage-filters
      (click)="openFilters.emit()"
    >
      <furar-icon name="tune" />
      {{ t + 'filters.title' | transloco }}
      @if (filterCount(); as n) {
        <span class="f-manage__count" data-filter-count>
          {{ n }}<span class="sr-only">{{ t + 'filters.active' | transloco }}</span>
        </span>
      }
    </button>
    <a
      furarButton="secondary"
      class="f-manage__tool"
      data-manage-calendar
      [routerLink]="['/', slug(), 'xestion', 'calendario']"
    >
      <furar-icon name="calendar-month" />
      {{ t + 'calendar' | transloco }}
    </a>
    @if (staff.canManage()) {
      <button
        type="button"
        furarButton="secondary"
        class="f-manage__tool f-manage__tool--icon-mobile"
        aria-haspopup="dialog"
        data-manage-export
        [attr.aria-label]="t + 'export.title' | transloco"
        (click)="openExport()"
      >
        <furar-icon name="download" />
        <span class="f-manage__tool-label">{{ t + 'export.title' | transloco }}</span>
      </button>
    }
  `,
})
export class BookingsToolbarComponent {
  readonly slug = input.required<string>();
  readonly q = input('');
  readonly filterCount = input(0);
  /** Os filtros da lista: «Descargar CSV» empeza con eles. */
  readonly filters = input<BookingFilters>(NO_FILTERS);
  readonly facilities = input<readonly { readonly id: string; readonly name: string }[]>([]);
  /** O texto da busca, despois dunha pausa ao escribir. */
  readonly searched = output<string>();
  readonly openFilters = output();

  protected readonly staff = inject(StaffAccessService);
  private readonly dialogs = inject(DialogService);

  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

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

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.searched.emit(value), SEARCH_DELAY_MS);
  }
}
