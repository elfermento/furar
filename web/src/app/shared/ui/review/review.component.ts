// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { StarsComponent } from '../stars/stars.component';

/**
 * Unha valoración nunha lista (ADR-0037), igual na ficha pública e nos indicadores: arriba, en
 * texto suave, o lugar (se a lista é de varias instalacións) e o día; debaixo, a nota en
 * estrelas; e o comentario no seu parágrafo, con aire. O contido proxectado (p. ex. o chip
 * «Oculto») vai nunha fila ao final. Só `span`, para que poida ir dentro dun botón (a fila que
 * abre o detalle). O relleno da fila pono a lista (`.f-review-row`).
 */
@Component({
  selector: 'furar-review',
  imports: [StarsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-review' },
  template: `
    <span class="f-review__meta">
      @if (place()) {
        <span class="f-review__place" data-review-place>{{ place() }}</span>
        <span class="f-review__dot" aria-hidden="true">·</span>
      }
      <time [attr.datetime]="datetime()">{{ date() }}</time>
    </span>
    <furar-stars [value]="stars()" />
    @if (comment()) {
      <span class="f-review__comment" [attr.data-hidden]="hidden() || null" data-review-text>{{ comment() }}</span>
    } @else if (noComment()) {
      <span class="f-review__none">{{ noComment() }}</span>
    }
    <span class="f-review__chips"><ng-content /></span>
  `,
})
export class ReviewComponent {
  /** A nota, de 1 a 5. */
  readonly stars = input.required<number>();
  /** O día xa escrito («10 de novembro»). */
  readonly date = input.required<string>();
  /** O instante, para o `datetime`. */
  readonly datetime = input.required<string>();
  /** Instalación (e espazo) da valoración; baleiro se a lista é dunha soa. */
  readonly place = input('');
  readonly comment = input<string | null | undefined>(null);
  /** Texto se non hai comentario («Sen comentario»); baleiro, nada. */
  readonly noComment = input('');
  /** Comentario oculto na ficha pública: en texto suave. */
  readonly hidden = input(false, { transform: booleanAttribute });
}
