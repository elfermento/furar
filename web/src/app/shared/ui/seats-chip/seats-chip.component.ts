// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { TagTone } from '../tag/tag.component';
import { TagComponent } from '../tag/tag.component';

/**
 * As prazas libres nun chip: máis de 2, verde («5 prazas libres»); 2 e 1, ámbar («Últimas 2
 * prazas», «Última praza»); ningunha, gris («Completa»). A forma cámbiase só aquí e nas claves
 * `ui.seats.*`.
 */
export interface SeatsChip {
  readonly key: 'free' | 'lastTwo' | 'lastOne' | 'full';
  readonly tone: TagTone;
  readonly count: number;
}

/** O chip para `free` prazas libres; sen límite (`null`), ningún. */
export function seatsChip(free: number | null | undefined): SeatsChip | null {
  if (free === null || free === undefined) return null;
  if (free > 2) return { key: 'free', tone: 'success', count: free };
  if (free === 2) return { key: 'lastTwo', tone: 'warn', count: 2 };
  if (free === 1) return { key: 'lastOne', tone: 'warn', count: 1 };
  return { key: 'full', tone: 'muted', count: 0 };
}

/**
 * O chip das prazas libres: o mesmo no pé das tarxetas das portadas (partidas, eventos e
 * actividades), nas filas da ficha dunha instalación e na fila «Prazas» dun evento completo. Sen
 * límite non pinta nada. Leva `data-seats` coa clave, para as probas.
 *
 * ```html
 * <furar-seats-chip [free]="event.free" />
 * ```
 */
@Component({
  selector: 'furar-seats-chip',
  imports: [TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (chip(); as c) {
      <furar-tag [tone]="c.tone" [attr.data-seats]="c.key">{{
        'ui.seats.' + c.key | transloco: { count: c.count }
      }}</furar-tag>
    }
  `,
})
export class SeatsChipComponent {
  /** Prazas libres; `null`, sen límite. */
  readonly free = input<number | null | undefined>(null);

  protected readonly chip = computed(() => seatsChip(this.free()));
}
