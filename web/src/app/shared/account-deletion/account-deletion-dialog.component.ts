// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActionItemComponent, ActionListDirective } from '../ui/action-list/action-list.component';
import { DialogComponent } from '../ui/dialog/dialog.component';
import { IconComponent } from '../ui/icon/icon.component';
import type { BlockerRow } from './account-deletion';

/** O que recibe o diálogo: as filas xa traducidas. */
export interface AccountDeletionDialogData {
  readonly rows: readonly BlockerRow[];
}

/**
 * «Antes de eliminar a túa conta» (ADR-0043): o que impide eliminala, unha fila por cousa. As que se
 * cancelan na web van nunha lista de accións (`furarActionList`) coa ligazón (pechan o diálogo ao
 * ir); o persoal e a plataforma, en texto coa icona. Só «Pechar»: non hai nada que confirmar.
 */
@Component({
  selector: 'furar-account-deletion-dialog',
  imports: [ActionItemComponent, ActionListDirective, DialogComponent, IconComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog
      size="sm"
      [heading]="'accountDeletion.title' | transloco"
      [description]="'accountDeletion.intro' | transloco"
    >
      @if (linked().length) {
        <ul furarActionList [attr.aria-label]="'accountDeletion.title' | transloco" data-account-deletion-links>
          @for (row of linked(); track row.key) {
            <li>
              <furar-action-item [icon]="row.icon">
                <a [routerLink]="row.link" (click)="ref.close()" [attr.data-blocker]="row.key">{{ row.text }}</a>
              </furar-action-item>
            </li>
          }
        </ul>
      }
      @for (row of unlinked(); track row.key) {
        <p class="f-confirm__detail" [attr.data-blocker]="row.key">
          <furar-icon [name]="row.icon" />
          <span>{{ row.text }}</span>
        </p>
      }
    </furar-dialog>
  `,
})
export class AccountDeletionDialogComponent {
  protected readonly data = inject<AccountDeletionDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<void>>(DialogRef);
  protected readonly linked = computed(() => this.data.rows.filter((row) => row.link !== null));
  protected readonly unlinked = computed(() => this.data.rows.filter((row) => row.link === null));
}
