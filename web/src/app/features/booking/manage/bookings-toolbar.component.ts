// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { COMPACT_QUERY, mediaMatches } from '../../../core/layout/language-menu';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SearchBarComponent } from '../../../shared/ui/search-bar/search-bar.component';

/** Espera da busca mentres se escribe. */
const SEARCH_DELAY_MS = 300;

/**
 * Busca das reservas do concello: unha cápsula co campo (nome, correo ou código; avisa
 * despois dunha pausa ao escribir) e, dentro, á dereita e tras un separador, «Filtros» (icona co
 * número de filtros postos) e «Calendario» como botóns de só icona, co nome no `aria-label`
 * e no `title`; o mesmo patrón ca o buscador da portada. No móbil o texto de axuda é «Buscar
 * reservas». A cápsula é `furar-search-bar`. «Descargar CSV» e «Nova reserva» van na cabeceira (`furar-manage-heading`).
 */
@Component({
  selector: 'furar-bookings-toolbar',
  imports: [IconComponent, RouterLink, SearchBarComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-manage__toolbar' },
  template: `
    @let t = 'booking.manage.';
    <furar-search-bar class="f-manage__search">
      <input
        type="search"
        data-manage-search
        [attr.aria-label]="t + 'search' | transloco"
        [placeholder]="(compact() ? t + 'search' : t + 'searchPlaceholder') | transloco"
        [value]="q()"
        (input)="onInput($event)"
      />
      @let filtersName =
        filterCount()
          ? (t + 'filters.titleCount' | transloco: { count: filterCount() })
          : (t + 'filters.title' | transloco);
      <button
        searchTool
        type="button"
        class="f-search-bar__tool"
        aria-haspopup="dialog"
        data-manage-filters
        [attr.aria-label]="filtersName"
        [attr.title]="filtersName"
        (click)="openFilters.emit()"
      >
        <furar-icon name="tune" />
        @if (filterCount(); as n) {
          <span class="f-search-bar__count" data-filter-count aria-hidden="true">{{ n }}</span>
        }
      </button>
      <a
        searchTool
        class="f-search-bar__tool"
        data-manage-calendar
        [routerLink]="['/', slug(), 'xestion', 'calendario']"
        [attr.aria-label]="t + 'calendar' | transloco"
        [attr.title]="t + 'calendar' | transloco"
      >
        <furar-icon name="calendar-month" />
      </a>
    </furar-search-bar>
  `,
})
export class BookingsToolbarComponent {
  readonly slug = input.required<string>();
  readonly q = input('');
  readonly filterCount = input(0);
  /** O texto da busca, despois dunha pausa ao escribir. */
  readonly searched = output<string>();
  readonly openFilters = output();

  /** Móbil: o texto de axuda curto («Buscar reservas»). */
  protected readonly compact = mediaMatches(inject(DOCUMENT).defaultView, COMPACT_QUERY, inject(DestroyRef));

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
