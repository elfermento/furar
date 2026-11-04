// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Directive } from '@angular/core';

/**
 * Tarxeta que se pulsa (docs/deseno.md §8): un `<button>` nativo co aspecto dunha tarxeta (bordo
 * `divider`, radio de card, recheo) que, ao pasar ou co foco, marca o bordo en `primary`. O
 * contido (título, etiquetas) pono quen o usa; o nome accesible é o seu texto. Para listas que se
 * editan nun modal (usos, excepcións, bloqueos no modo edición).
 */
@Directive({
  selector: 'button[furarTile]',
  host: { class: 'f-tile' },
})
export class TileDirective {}
