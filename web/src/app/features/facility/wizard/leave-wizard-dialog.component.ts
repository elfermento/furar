// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';

export interface LeaveWizardData {
  /** Sen nome non se pode gardar como borrador. */
  readonly canSave: boolean;
}

/** «Seguir» (ou pechar), «Descartar» ou «Gardar borrador». */
export type LeaveWizardResult = 'stay' | 'discard' | 'save' | undefined;

/**
 * Saír do asistente a medias (maqueta 53): seguir, descartar (bórrase o borrador, se xa estaba
 * gardado) ou gardalo. Sen nome non se pode gardar e dío.
 */
@Component({
  selector: 'furar-wizard-leave-dialog',
  imports: [ButtonDirective, DialogComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.wizard.leave.';
    <furar-dialog
      size="sm"
      [heading]="t + 'title' | transloco"
      [description]="data.canSave ? '' : (t + 'noName' | transloco)"
    >
      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-stay (click)="ref.close('stay')">
          {{ t + 'stay' | transloco }}
        </button>
        <button type="button" furarButton="danger" data-discard (click)="ref.close('discard')">
          {{ t + 'discard' | transloco }}
        </button>
        @if (data.canSave) {
          <button type="button" furarButton="primary" data-save-draft (click)="ref.close('save')">
            {{ t + 'save' | transloco }}
          </button>
        }
      </ng-container>
    </furar-dialog>
  `,
})
export class LeaveWizardDialogComponent {
  protected readonly data = inject<LeaveWizardData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<LeaveWizardResult>>(DialogRef);
}
