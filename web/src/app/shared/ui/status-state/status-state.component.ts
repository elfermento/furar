// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';

export type StatusMode = 'empty' | 'loading' | 'skeleton' | 'error';

/** Forma do esqueleto: filas dunha lista ou tarxetas nunha grella. */
export type SkeletonShape = 'row' | 'card';

const ICON: Record<StatusMode, IconName> = { empty: 'empty', loading: 'loading', skeleton: 'loading', error: 'error' };

/**
 * Baleiro, cargando ou erro no sitio do contido (docs/deseno.md P1-8). En `error` a icona é
 * sempre a mesma; `loading` é role="status" co texto common.loading. Acción opcional proxectada.
 * `skeleton` (D1-05d) é un `loading` coa forma do contido que vén (`rows` filas ou tarxetas,
 * segundo `shape`) en vez da icona: os bloques son decorativos e o texto, oculto, anúncialo o
 * mesmo `role="status"`.
 */
@Component({
  selector: 'furar-status-state',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-status',
    '[attr.data-mode]': 'mode()',
    '[attr.data-heading-level]': 'headingLevel()',
    '[attr.data-shape]': 'mode() === "skeleton" ? shape() : null',
    '[attr.role]': 'busy() ? "status" : null',
  },
  template: `
    @if (mode() === 'skeleton') {
      <span class="cdk-visually-hidden">{{ description() || ('common.loading' | transloco) }}</span>
      <div class="f-status__skeleton" aria-hidden="true">
        @for (block of blocks(); track block) {
          <div class="f-status__bone">
            <span class="f-status__bar"></span>
            <span class="f-status__bar" data-size="short"></span>
            @if (shape() === 'card') {
              <span class="f-status__bar" data-size="pill"></span>
            }
          </div>
        }
      </div>
    } @else {
      <span class="f-status__badge"><furar-icon [name]="icon()" [size]="24" /></span>
      @if (heading()) {
        @switch (headingLevel()) {
          @case (1) {
            <h1 class="f-status__title">{{ heading() }}</h1>
          }
          @case (3) {
            <h3 class="f-status__title">{{ heading() }}</h3>
          }
          @default {
            <h2 class="f-status__title">{{ heading() }}</h2>
          }
        }
      }
      @if (description()) {
        <p class="f-status__text">{{ description() }}</p>
      } @else if (mode() === 'loading') {
        <p class="f-status__text">{{ 'common.loading' | transloco }}</p>
      }
    }
    <div class="f-status__action"><ng-content /></div>
  `,
})
export class StatusStateComponent {
  readonly mode = input<StatusMode>('empty');
  readonly heading = input('');
  readonly headingLevel = input<1 | 2 | 3>(2);
  readonly description = input('');
  /** Cantos bloques pinta o esqueleto (só en `skeleton`). */
  readonly rows = input(3);
  /** Forma dos bloques do esqueleto (só en `skeleton`). */
  readonly shape = input<SkeletonShape>('row');
  protected readonly icon = computed(() => ICON[this.mode()]);
  protected readonly busy = computed(() => this.mode() === 'loading' || this.mode() === 'skeleton');
  protected readonly blocks = computed(() => Array.from({ length: Math.max(1, this.rows()) }, (_, i) => i));
}
