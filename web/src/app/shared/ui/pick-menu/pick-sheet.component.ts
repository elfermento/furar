// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DialogComponent } from '../dialog/dialog.component';
import type { DialogService } from '../dialog/dialog.service';
import type { PickMenuOption, PickSheetRequest } from './pick-menu.component';

interface PickSheetData {
  readonly heading: string;
  readonly options: readonly PickMenuOption[];
}

/**
 * A lista de `furar-pick-menu` no móbil: unha folla desde abaixo co título e unha fila por opción
 * (o nome á esquerda e o detalle á dereita, coma no menú do escritorio). Escoller pecha a folla
 * coa opción; Pechar, Esc ou picar fóra, sen ningunha.
 */
@Component({
  selector: 'furar-pick-sheet',
  imports: [DialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog [heading]="data.heading" size="sm">
      <ul class="f-pick-menu__sheet" [attr.aria-label]="data.heading">
        @for (option of data.options; track option.id) {
          <li>
            <button type="button" class="f-menu__item" [attr.data-option]="option.id" (click)="ref.close(option.id)">
              <span class="f-usermenu__option-name">{{ option.label }}</span>
              @if (option.detail) {
                {{ ' ' }}<span class="f-usermenu__value">{{ option.detail }}</span>
              }
            </button>
          </li>
        }
      </ul>
    </furar-dialog>
  `,
})
export class PickSheetComponent {
  protected readonly data = inject<PickSheetData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<string, PickSheetComponent>>(DialogRef);
}

/** Abre a folla e devolve a opción escollida (`undefined` se se pecha sen escoller). */
export function openPickSheet(dialogs: DialogService, request: PickSheetRequest): Promise<string | undefined> {
  const ref = dialogs.open<string, PickSheetData, PickSheetComponent>(PickSheetComponent, {
    data: { heading: request.heading, options: request.options },
    from: request.from,
  });
  return firstValueFrom(ref.closed);
}
