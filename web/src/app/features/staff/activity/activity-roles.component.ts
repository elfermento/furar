// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { RoleChip } from './activity';

/**
 * Os roles dunha persoa en chip (fondo do ton do rol e letra branca en negra), igual na táboa e no
 * detalle; sen rol, «—». O filtro Rol pinta as súas opcións co mesmo chip (`ROLE_TONE`).
 */
@Component({
  selector: 'furar-activity-roles',
  imports: [TagComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-activity__roles' },
  template: `
    @for (role of roles(); track role.label) {
      <furar-tag [tone]="role.tone">{{ role.label }}</furar-tag>
    } @empty {
      <span class="f-activity__soft" aria-hidden="true">—</span>
    }
  `,
})
export class ActivityRolesComponent {
  readonly roles = input.required<readonly RoleChip[]>();
}
