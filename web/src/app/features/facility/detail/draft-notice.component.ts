// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import type { MissingItem } from './facility-detail.store';
import { MISSING_SECTION, SECTION_IDS, focusSection } from './sections';

/**
 * Aviso de borrador (D1-21a; especificación, bloque 1): o que lle falta á instalación para que a
 * vexa a veciñanza, cunha ligazón a cada sección. `furar-callout` warn **sen** `role="alert"`: xa
 * está ao entrar na ficha, non é un cambio que haxa que anunciar. A ligazón leva o foco ao `h2`
 * da sección (o `href` segue valendo sen JS e para abrir noutra lapela). Se aínda non se publicou,
 * a ligazón ao asistente, que remata en «Publicar».
 */
@Component({
  selector: 'furar-facility-draft-notice',
  imports: [CalloutComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-callout tone="warn" [heading]="'facility.detail.draft.title' | transloco">
      @if (missing().length) {
        <p>{{ 'facility.detail.draft.intro' | transloco }}</p>
        <ul class="mt-unit ps-item list-disc">
          @for (item of missing(); track item) {
            <li>
              <a class="underline" [href]="href(item)" (click)="go($event, item)">
                {{ 'facility.detail.draft.missing.' + item | transloco }}
              </a>
            </li>
          }
        </ul>
      }
      @if (unpublished()) {
        <p [class.mt-unit]="missing().length" data-unpublished>
          {{ 'facility.detail.draft.unpublished' | transloco }}
          <a class="underline" data-wizard [routerLink]="['../nova', facilityId()]">
            {{ 'facility.detail.draft.wizard' | transloco }}
          </a>
        </p>
      }
    </furar-callout>
  `,
})
export class DraftNoticeComponent {
  readonly missing = input.required<readonly MissingItem[]>();
  /** Aínda sen o «Publicar» do asistente: a ligazón lévao a el. */
  readonly unpublished = input(false);
  readonly facilityId = input('');

  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);

  /** Ruta da ficha co fragmento da sección (vale para abrir noutra lapela). */
  protected href(item: MissingItem): string {
    return `${this.router.url.split('#')[0]}#${SECTION_IDS[MISSING_SECTION[item]]}`;
  }

  protected go(event: Event, item: MissingItem): void {
    event.preventDefault();
    focusSection(this.document, MISSING_SECTION[item]);
  }
}
