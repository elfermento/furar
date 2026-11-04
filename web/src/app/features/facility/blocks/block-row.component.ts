// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { allDay, blockHours } from './block-format';
import type { Block } from './blocks.store';

/**
 * O que di un bloqueo (D1-24; especificación, bloque 2, «Bloqueos»): as horas ou «Todo o día»
 * nunha etiqueta e o motivo. En `<span>`, para ir na fila ou dentro da tarxeta do modo edición.
 */
@Component({
  selector: 'furar-facility-block-summary',
  imports: [TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'gap-unit inline-flex flex-wrap items-center' },
  template: `
    <furar-tag tone="neutral">
      {{ allDay() ? ('facility.blocks.allDay' | transloco) : hours() }}
    </furar-tag>
    @if (block().reason) {
      <span class="text-ink-soft">{{ block().reason }}</span>
    }
  `,
})
export class BlockSummaryComponent {
  readonly block = input.required<Block>();

  private readonly language = inject(LanguageService);
  protected readonly allDay = computed(() => allDay(this.block()));
  protected readonly hours = computed(() => blockHours(this.block(), this.language.lang()));
}
