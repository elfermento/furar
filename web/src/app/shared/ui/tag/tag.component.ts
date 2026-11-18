// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** `muted`: o que xa non está en uso (p. ex. de baixa); o resto, segundo o significado que lle deu o produto. */
export type TagTone = 'neutral' | 'primary' | 'success' | 'warn' | 'danger' | 'info' | 'muted';
/**
 * `solid`: recheo do ton e letra branca ou negra. `outline`: superficie, bordo e letra no ton.
 * `soft`: fondo `tint` e letra na tinta, sen ton (etiquetas informativas que se repiten, como os
 * deportes dunha tarxeta); o ton non se aplica.
 */
export type TagLook = 'solid' | 'outline' | 'soft';

/** Etiqueta informativa en cápsula (docs/deseno.md P1-9), para metadatos dunha fila. */
@Component({
  selector: 'furar-tag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-tag', '[attr.data-tone]': 'tone()', '[attr.data-look]': 'look()' },
  template: '<ng-content />',
})
export class TagComponent {
  readonly tone = input<TagTone>('neutral');
  readonly look = input<TagLook>('solid');
}
