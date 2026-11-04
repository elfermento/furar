// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';

/**
 * Cabeceira de pantalla (docs/deseno.md P1-5): o único h1 (destino do foco ao navegar), o que vai
 * na liña do título (`pageHeadingAside`, p. ex. o chip de estado), introdución opcional cunha
 * icona decorativa diante (`introIcon`, p. ex. a ubicación) e ranura de accións (`pageActions`),
 * á dereita en escritorio. Dentro dunha sección con pestanas (Concello, maqueta 49) o h1 é o da
 * sección: a páxina leva `level="2"` e, se a pestana xa di o que é, `quiet` (título só para
 * lectores de pantalla e sen introdución; as accións seguen á vista).
 */
@Component({
  imports: [IconComponent],
  selector: 'furar-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-page-header', '[attr.data-size]': 'size()', '[attr.data-quiet]': 'quiet() ? "" : null' },
  template: `
    <div class="f-page-header__text">
      <div class="f-page-header__row">
        @if (level() === 2) {
          <h2 class="f-page-header__title" [class.sr-only]="quiet()">{{ heading() }}</h2>
        } @else {
          <h1 class="f-page-header__title" [class.sr-only]="quiet()">{{ heading() }}</h1>
        }
        <ng-content select="[pageHeadingAside]" />
      </div>
      @if (intro() && !quiet()) {
        <p class="f-page-header__intro">
          @if (introIcon(); as icon) {
            <furar-icon [name]="icon" />
          }
          <span>{{ intro() }}</span>
        </p>
      }
    </div>
    <div class="f-page-header__actions"><ng-content select="[pageActions]" /></div>
  `,
})
export class PageHeaderComponent {
  readonly heading = input.required<string>();
  readonly intro = input('');
  readonly introIcon = input<IconName | null>(null);
  /** `display` só na portada (escoller concello). */
  readonly size = input<'title' | 'display'>('title');
  readonly level = input<1 | 2>(1);
  readonly quiet = input(false);
}
