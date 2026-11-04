// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { DialogComponent } from '../../shared/ui/dialog/dialog.component';
import { ProseHtmlDirective } from '../../shared/ui/prose/prose-html.directive';

/** O que se ve: o título da páxina e o seu texto (o de Furar cos datos do formulario ou o propio). */
export interface LegalPreviewData {
  readonly title: string;
  readonly html: string;
  readonly lang: string;
}

/**
 * Vista previa dunha páxina legal tal como sairá na web (maqueta 33): o texto co estilo de Furar,
 * no idioma escollido. Pinta con `[furarProseHtml]`, que só copia a lista branca (sen
 * `bypassSecurityTrust*`); o servidor volverá sanealo ao gardar.
 */
@Component({
  selector: 'furar-legal-preview-dialog',
  imports: [ButtonDirective, DialogComponent, ProseHtmlDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog size="lg" [heading]="data.title" [subheading]="'settings.legal.preview' | transloco">
      <article
        class="f-prose f-legal__preview"
        data-legal-preview-body
        [attr.lang]="data.lang"
        [furarProseHtml]="data.html"
      ></article>
      <button dialogActions type="button" furarButton (click)="ref.close()">{{ 'ui.dialog.close' | transloco }}</button>
    </furar-dialog>
  `,
})
export class LegalPreviewDialogComponent {
  protected readonly data = inject<LegalPreviewData>(DIALOG_DATA);
  protected readonly ref = inject(DialogRef);
}
