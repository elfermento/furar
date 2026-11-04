// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { formatCountdown } from '../../../shared/ui/countdown/countdown';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { MENU } from '../../../shared/ui/menu/menu.directives';
import type { TagTone } from '../../../shared/ui/tag/tag.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import type { LastAccess, MemberAction, MemberState, StaffMemberResponse } from './staff-members';
import {
  STATE_TONE,
  lastAccessOf,
  memberActions,
  memberLabel,
  memberState,
  resendBlock,
  resendInline,
} from './staff-members';

/** O que pide a fila: a persoa e a acción. */
export interface MemberActionRequest {
  readonly member: StaffMemberResponse;
  readonly action: MemberAction;
}

/** Unha fila xa preparada para pintar (os textos tradúceos o modelo). */
interface MemberRow {
  readonly member: StaffMemberResponse;
  readonly label: string;
  /**
   * Sen correo: anonimizada pola conservación («Persoa anonimizada») ou desactivada hai máis do
   * tramo visible do rexistro, coa identidade bloqueada («Persoa desactivada»).
   */
  readonly hidden: 'anonymized' | 'blocked' | null;
  readonly state: MemberState;
  readonly tone: TagTone;
  readonly access: LastAccess;
  readonly resend: boolean;
  /**
   * «Reenviar» desactivado: a clave do texto («Podes reenviar en 1:45» ou «xa se enviou o máximo»)
   * e os seus parámetros; `null` se se pode reenviar.
   */
  readonly blocked: { readonly key: string; readonly params: { readonly time?: string } } | null;
  readonly actions: MemberAction[];
}

const ACTION_ICON = { resend: 'email', role: 'pencil', deactivate: 'logout', reactivate: 'undo' } as const;

/**
 * Lista do persoal (D4-14): táboa no escritorio (`caption`, `th scope`) e tarxetas no móbil (sen
 * scroll horizontal a 320 px). Columnas: usuario (o correo; «Persoa anonimizada» ou «Persoa
 * desactivada» se a conservación o quitou ou o bloqueou), rol, estado (Pendente, Activa,
 * Desactivada) e último acceso; mentres está pendente, esa columna di como vai a invitación
 * («Invitación enviada o …»; en vermello «Non se puido enviar» ou «Invitación caducada», con
 * «Reenviar» á vista). As accións, nun `[furarMenu]` por fila co correo no botón; a fila non fai nada
 * por si mesma. Mentres non se pode reenviar, «Reenviar» (na fila e no menú) queda desactivado con
 * `aria-disabled` e a conta atrás á vista («Podes reenviar en 1:45») como a súa descrición
 * (`aria-describedby`): non se anuncia cada segundo.
 */
@Component({
  selector: 'furar-staff-members-list',
  imports: [ButtonDirective, IconComponent, NgTemplateOutlet, TagComponent, TranslocoPipe, ...MENU],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff-members-list.component.html',
})
export class StaffMembersListComponent {
  readonly members = input.required<readonly StaffMemberResponse[]>();
  /** Táboa (escritorio) ou tarxetas (móbil). */
  readonly desktop = input(true);
  /** Nome da táboa ou da lista. */
  readonly caption = input.required<string>();
  /** Agora (ms), para saber se unha invitación caducou. */
  readonly now = input.required<number>();
  /** A persoa coa que se está a facer algo (os seus botóns, ocupados). */
  readonly busyId = input<string | null>(null);
  readonly requested = output<MemberActionRequest>();

  protected readonly rows = computed<MemberRow[]>(() => {
    const now = this.now();
    return this.members().map((member) => {
      const state = memberState(member);
      return {
        member,
        label: memberLabel(member),
        hidden: member.anonymized ? 'anonymized' : member.blocked ? 'blocked' : null,
        state,
        tone: STATE_TONE[state],
        access: lastAccessOf(member, now),
        resend: resendInline(member, now),
        blocked: blockedOf(member, now),
        actions: memberActions(member),
      };
    });
  });

  protected icon(action: MemberAction) {
    return ACTION_ICON[action];
  }

  protected request(member: StaffMemberResponse, action: MemberAction): void {
    this.requested.emit({ member, action });
  }

  /** «Reenviar» da fila: desactivado (`aria-disabled`, segue co foco) non pide nada. */
  protected resendInline(row: MemberRow): void {
    if (!row.blocked) this.request(row.member, 'resend');
  }
}

function blockedOf(member: StaffMemberResponse, now: number): MemberRow['blocked'] {
  const block = resendBlock(member, now);
  if (!block) return null;
  if (block.kind === 'spent') return { key: 'spent', params: {} };
  return { key: 'in', params: { time: formatCountdown(block.until - now) } };
}
