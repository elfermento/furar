// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../button/button.directive';
import { DialogComponent } from './dialog.component';

/** Que fai quen sae dunha parte con cambios sen gardar. */
export type UnsavedChoice = 'save' | 'discard' | 'cancel';

/** O que pregunta: o título xa traducido («Gardar os cambios de Aparencia?»). */
export interface UnsavedChoiceOptions {
  readonly heading: string;
}

/** Datos internos: as opcións e o id do texto que describe o `alertdialog`. */
export interface UnsavedChoiceData extends UnsavedChoiceOptions {
  readonly descriptionId: string;
}

/**
 * Saír dunha parte con cambios sen gardar (patrón APG «alertdialog»): Cancelar (seguir aquí),
 * Descartar (volven os datos gardados) e Gardar, a acción principal, onde entra o foco. Ábrese con
 * `DialogService.unsavedChoice()`; pechar con Esc ou picando fóra é Cancelar.
 */
@Component({
  selector: 'furar-unsaved-choice',
  imports: [ButtonDirective, DialogComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog
      size="sm"
      [heading]="data.heading"
      [description]="'ui.unsavedChoice.message' | transloco"
      [descriptionId]="data.descriptionId"
    >
      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-choice="cancel" (click)="ref.close('cancel')">
          {{ 'ui.confirm.cancel' | transloco }}
        </button>
        <button type="button" furarButton data-choice="discard" (click)="ref.close('discard')">
          {{ 'ui.unsavedChoice.discard' | transloco }}
        </button>
        <button type="button" furarButton="primary" data-choice="save" (click)="ref.close('save')">
          {{ 'common.save' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class UnsavedChoiceComponent {
  protected readonly data = inject<UnsavedChoiceData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<UnsavedChoice>>(DialogRef);
}
