// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';

/** As cinco posicións dunha nota. */
export const STAR_POSITIONS: readonly number[] = [1, 2, 3, 4, 5];

/**
 * Unha nota de 1 a 5 en estrelas (ADR-0037): tantas cheas coma a nota e o resto
 * baleiras, na cor principal. É unha imaxe co seu nome («4 de 5 estrelas»); as iconas son
 * decorativas. `sm` (16 px) nas listas e tarxetas; `md` (20 px) no detalle.
 */
@Component({
  selector: 'furar-stars',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-stars',
    role: 'img',
    '[attr.data-size]': 'size()',
    '[attr.data-stars]': 'stars()',
    '[attr.aria-label]': 'name()',
  },
  template: `
    @for (n of positions; track n) {
      <furar-icon [name]="n <= stars() ? 'star-fill' : 'star'" />
    }
  `,
})
export class StarsComponent {
  /** A nota, de 1 a 5 (fóra diso, recórtase). */
  readonly value = input.required<number>();
  readonly size = input<'sm' | 'md'>('sm');

  protected readonly positions = STAR_POSITIONS;
  protected readonly stars = computed(() => Math.min(5, Math.max(0, Math.round(this.value()))));

  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  protected readonly name = computed(() => {
    this.langChanges();
    return this.transloco.translate('ui.stars.label', { count: this.stars() });
  });
}
