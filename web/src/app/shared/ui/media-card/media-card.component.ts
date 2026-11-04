// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../icon/icon.component';

/**
 * Tarxeta con imaxe (docs/deseno.md §8): imaxe 3:2 (zoom suave ao pasar), título, lugar coa icona
 * de ubicación e, debaixo, o contido de quen a usa (etiquetas `soft`, avisos). Con `link`, o
 * título é unha ligazón estirada a toda a tarxeta; o que vaia en `[cardActions]` (un chip con
 * menú, botóns) queda por riba da ligazón e é unha parada de tabulador propia, sen aniñar
 * interactivos. O que vaia en `[cardBadge]` (unha etiqueta `solid`: «2 ocos hoxe») ponse enriba
 * da imaxe, arriba á esquerda. A imaxe é informativa (o seu `alt`) ou decorativa (`alt=""`) segundo quen chama.
 */
@Component({
  selector: 'furar-media-card',
  imports: [IconComponent, NgTemplateOutlet, RouterLink],
  host: { class: 'f-media-card', '[attr.data-link]': 'link() ? "" : null' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="f-media-card__image">
      <img [src]="src()" [alt]="alt()" loading="lazy" />
      <ng-content select="[cardBadge]" />
    </div>
    <div class="f-media-card__body">
      <ng-template #title>
        @if (link(); as link) {
          <a class="f-media-card__link" [routerLink]="link">{{ heading() }}</a>
        } @else {
          {{ heading() }}
        }
      </ng-template>
      @if (headingLevel() === 2) {
        <h2 class="f-media-card__title"><ng-container [ngTemplateOutlet]="title" /></h2>
      } @else {
        <h3 class="f-media-card__title"><ng-container [ngTemplateOutlet]="title" /></h3>
      }
      @if (place()) {
        <p class="f-media-card__place">
          <furar-icon name="location" />
          <span>{{ place() }}</span>
        </p>
      }
      <ng-content />
      <div class="f-media-card__actions">
        <ng-content select="[cardActions]" />
      </div>
    </div>
  `,
})
export class MediaCardComponent {
  /** Ruta da imaxe (a miniatura). */
  readonly src = input.required<string>();
  /** Texto alternativo; baleiro se a imaxe só decora. */
  readonly alt = input('');
  readonly heading = input.required<string>();
  readonly headingLevel = input<2 | 3>(3);
  /** Enderezo ou lugar, coa icona de ubicación (decorativa). */
  readonly place = input('');
  /** Destino da tarxeta enteira (`routerLink`); sen el, a tarxeta non é ligazón. */
  readonly link = input<readonly unknown[] | string | null>(null);
}
