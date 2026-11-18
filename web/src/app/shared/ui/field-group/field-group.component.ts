// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/**
 * Campos relacionados nunha cápsula (sistema de campos; styles/components/fields.css): un marco
 * con liñas entre os campos (`furar-field`, `furar-bilingual-field`, `furar-field-popover`). O
 * erro de cada campo vai xusto debaixo del, dentro do grupo, sen o rótulo diante (o seguinte campo
 * baixa); nunha fila de campos curtos, cada erro debaixo da súa metade. O dun `furar-field-popover`
 * vai dentro del, cun atributo `fieldError` (`<span fieldError class="f-field-error" [id]>`).
 *
 * Con `cells`, os campos van nunha fila (campos curtos: desde e ata, teléfono e correo) e parten en
 * máis filas se non caben; o ancho mínimo de cada un é `--f-cell` (10rem; p. ex.
 * `style="--f-cell: 13rem"`). Unha fila de campos curtos dentro dun grupo: `<div class="f-field-cells">`.
 * Con `label`, a cápsula é un `group` con ese nome (só se os rótulos dos campos non abondan).
 */
@Component({
  selector: 'furar-field-group',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-fields' },
  template: `
    <div
      class="f-field-group"
      [attr.data-cells]="cells() ? '' : null"
      [attr.role]="label() ? 'group' : null"
      [attr.aria-label]="label() || null"
    >
      <ng-content />
    </div>
  `,
})
export class FieldGroupComponent {
  /** Campos curtos nunha fila, que parte se non caben. */
  readonly cells = input(false, { transform: booleanAttribute });
  /** Nome do grupo (role="group"), se fai falta. */
  readonly label = input('');
}
