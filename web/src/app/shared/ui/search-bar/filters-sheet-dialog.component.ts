// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { NgTemplateOutlet } from '@angular/common';
import type { Signal, TemplateRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ButtonDirective } from '../button/button.directive';
import { DialogComponent } from '../dialog/dialog.component';

export interface FiltersSheetData {
  readonly heading: string;
  /** Os chips dos filtros, declarados na páxina: seguen ligados aos seus datos. */
  readonly template: TemplateRef<unknown>;
  /** O botón que pecha («Ver 12 incidencias»), ao día co número de filas. */
  readonly doneLabel: Signal<string>;
}

/**
 * Folla «Filtros» do móbil (`furar-search-bar`): todos os chips dos filtros da lista, que se
 * aplican ao momento, e un botón que a pecha co número de resultados. No escritorio, centrada.
 */
@Component({
  selector: 'furar-filters-sheet-dialog',
  imports: [ButtonDirective, DialogComponent, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog [heading]="data.heading">
      <div class="f-search-bar__filters" data-filters-sheet>
        <ng-container [ngTemplateOutlet]="data.template" />
      </div>
      @if (data.doneLabel(); as done) {
        <button dialogActions type="button" furarButton="primary" data-filters-done (click)="ref.close()">
          {{ done }}
        </button>
      }
    </furar-dialog>
  `,
})
export class FiltersSheetDialogComponent {
  protected readonly data = inject<FiltersSheetData>(DIALOG_DATA);
  protected readonly ref = inject(DialogRef);
}
