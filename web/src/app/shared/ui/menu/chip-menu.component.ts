// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActionDirective } from '../action/action.directive';
import { IconComponent } from '../icon/icon.component';
import type { TagTone } from '../tag/tag.component';
import { TagComponent } from '../tag/tag.component';
import { MENU } from './menu.directives';

/** Unha opción do chip: valor, texto xa traducido e ton (o que lle deu o produto). */
export interface ChipMenuOption<T extends string = string> {
  readonly value: T;
  readonly label: string;
  readonly tone: TagTone;
}

/**
 * Chip que mostra un valor e abre un menú para cambialo (D1-05a; docs/deseno.md P1-9): un
 * `<button>` cun `furar-tag` dentro e un `[furarMenu]` de opcións únicas (`menuitemradio`). O valor
 * vai sempre en texto, tamén no menú (cada opción co seu punto de cor, decorativo).
 * Nome accesible completo: «Estado: Activa. Cambiar».
 *
 * Non cambia o valor por si mesmo: emite `selected` co pedido e quen o usa decide (confirmar,
 * reservas afectadas, gardar) e actualiza `value`. Escoller a opción xa marcada non emite.
 * Mentres se aplica, `busy` (non `disabled`, que deixaría o foco no aire).
 */
@Component({
  selector: 'furar-chip-menu',
  imports: [ActionDirective, IconComponent, TagComponent, TranslocoPipe, ...MENU],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-menu-trigger' },
  template: `
    <button
      type="button"
      class="f-menu-trigger__button"
      furarAction
      [furarMenuTrigger]="menu"
      [busy]="busy()"
      [disabled]="disabled()"
      [attr.aria-label]="'ui.chipMenu.name' | transloco: { label: label(), value: current()?.label ?? '' }"
    >
      <furar-tag [tone]="current()?.tone ?? 'neutral'">
        {{ current()?.label }}
        <furar-icon name="chevron-down" />
      </furar-tag>
    </button>
    <ng-template #menu>
      <div furarMenu [attr.aria-label]="label()">
        @for (option of options(); track option.value) {
          <button
            type="button"
            furarMenuItemRadio
            [checked]="option.value === value()"
            (triggered)="pick(option.value)"
          >
            <span class="f-tag-swatch" aria-hidden="true" [attr.data-tone]="option.tone"></span>
            {{ option.label }}
          </button>
        }
      </div>
    </ng-template>
  `,
})
export class ChipMenuComponent<T extends string = string> {
  /** Que é o valor, para o nome do botón e do menú: «Estado». */
  readonly label = input.required<string>();
  readonly options = input.required<readonly ChipMenuOption<T>[]>();
  readonly value = input.required<T>();
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Aplicando o cambio: acción en curso do contexto (`furarAction`), co foco no chip. */
  readonly busy = input(false, { transform: booleanAttribute });
  /** O valor pedido; quen o usa decide se se aplica. */
  readonly selected = output<T>();

  protected readonly current = computed(() => this.options().find((option) => option.value === this.value()));

  protected pick(value: T): void {
    if (value !== this.value()) {
      this.selected.emit(value);
    }
  }
}
