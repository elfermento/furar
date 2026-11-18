// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';

export interface LeaveWizardData {
  /** Sen nome non se pode gardar como borrador. */
  readonly canSave: boolean;
  /** Xa hai borrador gardado (se non, saír sen gardar pérdeo todo). */
  readonly hasDraft: boolean;
  /** Hora do último gardado nesta visita («10:42»), se a hai. */
  readonly savedAt: string | null;
}

/** «Saír sen gardar» ou «Gardar e saír»; pechar (Esc, ×, fóra) é seguir no asistente. */
export type LeaveWizardResult = 'leave' | 'save' | undefined;

/**
 * Saír do asistente con cambios sen gardar: saír sen gardar (o borrador queda como
 * se gardou por última vez) ou gardar e saír. Descartar o borrador non vai aquí, senón na cabeceira.
 * Sen nome non se pode gardar e dío.
 */
@Component({
  selector: 'furar-wizard-leave-dialog',
  imports: [ButtonDirective, DialogComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.wizard.leave.';
    <furar-dialog size="sm" [heading]="t + 'title' | transloco" [description]="description()">
      <ng-container dialogActions>
        <button type="button" furarButton data-leave (click)="ref.close('leave')">
          {{ t + 'leave' | transloco }}
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
  private readonly transloco = inject(TranslocoService);

  /** Que queda se se sae sen gardar (e, sen nome, por que non se pode gardar). */
  protected readonly description = computed(() => {
    const t = 'facility.wizard.leave.';
    const { canSave, hasDraft, savedAt } = this.data;
    const kept = !hasDraft
      ? this.transloco.translate(t + 'lost')
      : savedAt
        ? this.transloco.translate(t + 'keptAt', { time: savedAt })
        : this.transloco.translate(t + 'kept');
    return canSave ? kept : `${this.transloco.translate(t + 'noName')} ${kept}`;
  });
}
