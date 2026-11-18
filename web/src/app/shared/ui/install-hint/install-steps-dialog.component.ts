// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../button/button.directive';
import { DialogComponent } from '../dialog/dialog.component';
import { IconComponent } from '../icon/icon.component';
import type { IosBrowser } from './install-hint';

/**
 * «Instalar» en Safari ou Chrome de iPhone e iPad: iOS non deixa instalar desde a web, así que a
 * folla di os dous pasos (Compartir › Engadir á pantalla de inicio) co nome e a icona que se ven no
 * navegador. A mesma folla para os dous: só cambia onde está «Compartir» (na barra de Safari; en
 * Chrome, na barra de enderezos), coa mesma icona de iOS. Pecha con «Entendido», co botón de
 * pechar, con Esc ou picando fóra (DialogService).
 */
@Component({
  selector: 'furar-install-steps-dialog',
  imports: [ButtonDirective, DialogComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'ui.installHint.ios.';
    <furar-dialog size="sm" [heading]="t + 'title' | transloco">
      <ol class="f-install-steps" [attr.data-install-steps]="browser">
        <li>
          <span class="f-install-steps__num" aria-hidden="true">1</span>
          <span
            >{{ t + 'tap' | transloco }}
            <span class="f-install-steps__key"><furar-icon name="ios-share" />{{ t + 'share' | transloco }}</span>
            {{ t + (browser === 'chrome' ? 'inChrome' : 'inSafari') | transloco }}</span
          >
        </li>
        <li>
          <span class="f-install-steps__num" aria-hidden="true">2</span>
          <span
            >{{ t + 'choose' | transloco }}
            <span class="f-install-steps__key">{{ t + 'addToHome' | transloco }}</span></span
          >
        </li>
      </ol>
      <ng-container dialogActions>
        <button type="button" furarButton block data-install-steps-done (click)="ref.close()">
          {{ t + 'done' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class InstallStepsDialogComponent {
  protected readonly ref = inject(DialogRef);
  /** De que navegador son os pasos (Safari, se non o di). */
  protected readonly browser: IosBrowser = inject<IosBrowser | null>(DIALOG_DATA, { optional: true }) ?? 'safari';
}
