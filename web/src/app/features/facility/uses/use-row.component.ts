// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';
import { formatShort } from '../../../shared/ui/calendar/local-date';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { Use } from './uses.store';

/** «31/10/2026» ou «01/07/2026–15/07/2026» (rango con extremos incluídos). */
export function periodText(period: { from: string; to: string }): string {
  return period.from === period.to ? formatShort(period.from) : `${formatShort(period.from)}–${formatShort(period.to)}`;
}

/**
 * Etiquetas dun uso (D1-22; especificación, bloque 1, «Usos»), co estilo das tarxetas (`soft`):
 * como se aluga, canto dura, cando e se bloquea os demais. Van en `<span>` para poder ir dentro
 * dun botón (a tarxeta do modo edición).
 */
@Component({
  selector: 'furar-facility-use-tags',
  imports: [TagComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-tag-list' },
  template: `
    @for (tag of tags(); track $index) {
      <furar-tag look="soft">{{ tag }}</furar-tag>
    }
  `,
})
export class UseTagsComponent {
  readonly use = input.required<Use>();
  /** Sen «Cando» (o asistente non o pregunta: todos os usos son «Sempre»). */
  readonly withoutWhen = input(false, { transform: booleanAttribute });

  private readonly transloco = inject(TranslocoService);
  private readonly lang = toSignal(this.transloco.langChanges$);

  protected readonly tags = computed(() => {
    this.lang();
    const use = this.use();
    const t = (key: string, params?: Record<string, unknown>) =>
      this.transloco.translate(`facility.uses.tags.${key}`, params);
    const mode =
      use.rentalMode === 'PARTS'
        ? t('parts', { count: use.parts.length, names: use.parts.map((part) => part.name).join(', ') })
        : use.rentalMode === 'SEATS'
          ? t('seats', { count: use.capacity })
          : t('whole');
    const when = use.availability === 'DATES' ? use.periods.map(periodText).join(', ') : t('always');
    if (this.withoutWhen()) return [mode, t('minutes', { minutes: use.slotMinutes })];
    return [mode, t('minutes', { minutes: use.slotMinutes }), when, ...(use.blocksOthers ? [t('blocks')] : [])];
  });
}
