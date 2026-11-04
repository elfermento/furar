// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { MediaCardComponent } from '../../../shared/ui/media-card/media-card.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { PublicFacilityResponse } from './home.store';

/**
 * Tarxeta dunha instalación de exemplo (maqueta 46): a mesma ca a dunha instalación, coa etiqueta
 * «Exemplo» enriba da imaxe e sen ligazón (non hai ficha que abrir).
 */
@Component({
  selector: 'furar-example-card',
  imports: [MediaCardComponent, TagComponent, TranslocoPipe],
  host: { class: 'contents' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-media-card [src]="facility().cover.thumb" [heading]="facility().name" data-example>
      <furar-tag cardBadge tone="muted">{{ 'home.examples.badge' | transloco }}</furar-tag>
      <ul class="f-tag-list" [attr.aria-label]="'home.facilities.sports' | transloco">
        @for (sport of facility().sports; track sport.label.gl) {
          <li>
            <furar-tag look="soft">{{ sport.label.gl }}</furar-tag>
          </li>
        }
      </ul>
    </furar-media-card>
  `,
})
export class ExampleCardComponent {
  readonly facility = input.required<PublicFacilityResponse>();
}
