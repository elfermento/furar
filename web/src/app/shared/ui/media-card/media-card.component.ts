// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import type { Params } from '@angular/router';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../icon/icon.component';

/**
 * Tarxeta con imaxe (docs/deseno.md §8): imaxe 3:2 (zoom suave ao pasar), título, lugar coa icona
 * de ubicación e, debaixo, o contido de quen a usa (etiquetas `soft`, avisos). Con `link`, o
 * título é unha ligazón estirada a toda a tarxeta; o que vaia en `[cardActions]` (un chip con
 * menú, botóns) queda por riba da ligazón e é unha parada de tabulador propia, sen aniñar
 * interactivos. O que vaia en `[cardBadge]` (unha etiqueta `solid`: «2 ocos hoxe») ponse enriba
 * da imaxe, arriba á esquerda. Con `when` (un evento), a data coa icona do calendario vai enriba do
 * lugar e, con `period` (unha actividade), as datas debaixo dela. O que vaia en `[cardFoot]` (as prazas dunha partida) vai abaixo de todo, tras unha liña
 * fina e en letra pequena, pegado ao fondo aínda que as tarxetas dunha tira midan distinto. Con
 * `selected` leva un aro (a escollida nun mapa). A imaxe é informativa (o seu `alt`) ou decorativa
 * (`alt=""`) segundo quen chama; sen `src`, queda o fondo da imaxe baleiro.
 */
@Component({
  selector: 'furar-media-card',
  imports: [IconComponent, NgTemplateOutlet, RouterLink],
  host: {
    class: 'f-media-card',
    '[attr.data-link]': 'link() ? "" : null',
    '[attr.data-selected]': 'selected() ? "" : null',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="f-media-card__image">
      @if (src()) {
        <img [src]="src()" [alt]="alt()" loading="lazy" />
      }
      <ng-content select="[cardBadge]" />
    </div>
    <div class="f-media-card__body">
      <ng-template #title>
        @if (link(); as link) {
          <a class="f-media-card__link" [routerLink]="link" [queryParams]="linkParams()">{{ heading() }}</a>
        } @else {
          {{ heading() }}
        }
      </ng-template>
      @if (headingLevel() === 2) {
        <h2 class="f-media-card__title"><ng-container [ngTemplateOutlet]="title" /></h2>
      } @else {
        <h3 class="f-media-card__title"><ng-container [ngTemplateOutlet]="title" /></h3>
      }
      @if (when()) {
        <p class="f-media-card__when">
          <furar-icon name="calendar-month" />
          <span>{{ when() }}</span>
        </p>
      }
      @if (period()) {
        <p class="f-media-card__when" data-card-period>
          <furar-icon name="event-repeat" />
          <span>{{ period() }}</span>
        </p>
      }
      @if (place()) {
        <p class="f-media-card__place">
          <furar-icon name="location" />
          <span>
            @if (distance()) {
              <b>{{ distance() }}</b> ·
            }
            {{ place() }}</span
          >
        </p>
      }
      <ng-content />
      <div class="f-media-card__actions">
        <ng-content select="[cardActions]" />
      </div>
      <div class="f-media-card__foot">
        <ng-content select="[cardFoot]" />
      </div>
    </div>
  `,
})
export class MediaCardComponent {
  /** Ruta da imaxe (a miniatura); baleira, sen imaxe. */
  readonly src = input('');
  /** Texto alternativo; baleiro se a imaxe só decora. */
  readonly alt = input('');
  readonly heading = input.required<string>();
  readonly headingLevel = input<2 | 3>(3);
  /** Cando (un evento: «sáb. 28 nov. · 10:00»), coa icona do calendario, enriba do lugar. */
  readonly when = input('');
  /** As datas dunha actividade con horario («Do 1 out. ao 19 xuñ.»), coa icona de repetir, debaixo de `when`. */
  readonly period = input('');
  /** Enderezo ou lugar, coa icona de ubicación (decorativa). */
  readonly place = input('');
  /** Distancia a quen mira («a 1,2 km»), en negra diante do lugar. */
  readonly distance = input('');
  /** Destino da tarxeta enteira (`routerLink`); sen el, a tarxeta non é ligazón. */
  readonly link = input<readonly unknown[] | string | null>(null);
  /** Parámetros do URL do destino (`?uso=` dun uso xa escollido). */
  readonly linkParams = input<Params | null>(null);
  /** Resaltada: a tarxeta da marca escollida nun mapa (aro da cor do foco). */
  readonly selected = input(false, { transform: booleanAttribute });
}
