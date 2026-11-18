// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { TemplateRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DialogService } from '../dialog/dialog.service';
import { IconComponent } from '../icon/icon.component';
import type { FiltersSheetData } from './filters-sheet-dialog.component';
import { FiltersSheetDialogComponent } from './filters-sheet-dialog.component';

/**
 * Busca dunha lista (o patrón das reservas): unha cápsula coa lupa e o campo que proxecta quen a
 * usa (`<input type="search">`, co seu valor, eventos e `data-*`) e, á dereita tras un separador,
 * os botóns de só icona: «Filtros» e os que se proxecten con `searchTool` (clase
 * `f-search-bar__tool`, p. ex. «Calendario»).
 *
 * Con `filters` (un `<ng-template>` cos chips dos filtros das columnas), no móbil aparece «Filtros»
 * co número de filtros postos, que abre unha folla desde abaixo con todos eles e un botón
 * (`doneLabel`, «Ver 12 incidencias») que a pecha; no escritorio non se ve, porque se filtra
 * nas cabeceiras da táboa.
 *
 * Vai dentro de `.f-list-toolbar` cando comparte liña coa acción da lista («+»).
 */
@Component({
  selector: 'furar-search-bar',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-search-bar' },
  template: `
    <div class="f-search-bar__query">
      <furar-icon name="search" />
      <ng-content select="input" />
    </div>
    @if (filters()) {
      @let filtersName =
        filterCount()
          ? ('ui.searchBar.filtersCount' | transloco: { count: filterCount() })
          : ('ui.searchBar.filters' | transloco);
      <button
        type="button"
        class="f-search-bar__tool f-search-bar__tool--mobile"
        aria-haspopup="dialog"
        data-filters-open
        [attr.aria-label]="filtersName"
        [attr.title]="filtersName"
        (click)="openFilters()"
      >
        <furar-icon name="tune" />
        @if (filterCount(); as n) {
          <span class="f-search-bar__count" data-filters-count aria-hidden="true">{{ n }}</span>
        }
      </button>
    }
    <ng-content select="[searchTool]" />
  `,
})
export class SearchBarComponent {
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);

  /** Os chips dos filtros, para a folla do móbil; sen eles, sen «Filtros». */
  readonly filters = input<TemplateRef<unknown> | null>(null);
  /** Cantos filtros hai postos (o número sobre «Filtros»). */
  readonly filterCount = input(0);
  /** O botón que pecha a folla («Ver 12 incidencias»). */
  readonly doneLabel = input('');

  protected openFilters(): void {
    const template = this.filters();
    if (!template) return;
    const data: FiltersSheetData = {
      heading: this.transloco.translate('ui.searchBar.filters'),
      template,
      doneLabel: this.doneLabel,
    };
    this.dialogs.open<unknown, FiltersSheetData, FiltersSheetDialogComponent>(FiltersSheetDialogComponent, { data });
  }
}
