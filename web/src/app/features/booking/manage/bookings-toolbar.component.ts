// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';

/** Espera da busca mentres se escribe. */
const SEARCH_DELAY_MS = 300;

/**
 * Barra das reservas do concello (maqueta 26): a busca en cápsula (nome, correo ou código; avisa despois
 * dunha pausa ao escribir), «Filtros» co número de filtros postos e «Calendario» (D2-16).
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
  `,
})
export class BookingsToolbarComponent {
  readonly slug = input.required<string>();
  readonly q = input('');
  readonly filterCount = input(0);
  /** O texto da busca, despois dunha pausa ao escribir. */
  readonly searched = output<string>();
  readonly openFilters = output();

  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.searched.emit(value), SEARCH_DELAY_MS);
  }
}
