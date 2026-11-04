// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { changeStaffRole } from '../../../api/fn/staff/change-staff-role';
import { deactivateStaffMember } from '../../../api/fn/staff/deactivate-staff-member';
import { inviteStaffMember } from '../../../api/fn/staff/invite-staff-member';
import { listStaff } from '../../../api/fn/staff/list-staff';
import { reactivateStaffMember } from '../../../api/fn/staff/reactivate-staff-member';
import { resendStaffInvitation } from '../../../api/fn/staff/resend-staff-invitation';
import type { InviteStaffMemberRequest } from '../../../api/models/invite-staff-member-request';
import type { StaffMemberResponse } from '../../../api/models/staff-member-response';

export type { InviteStaffMemberRequest, StaffMemberResponse };
type StaffRole = StaffMemberResponse['role'];

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Persoal do concello (D4-14): a lista (`GET …/staff`, todas, tamén as desactivadas) e as accións.
 * Invitar engade a persoa ao principio; reenviar, cambiar o rol, desactivar e reactivar van coa `version`
 * da fila en `If-Match` (a lista non trae `ETag` por fila) e a resposta substitúe a fila. Se
 * fallan (409, 412, 429…), rexeitan co erro e non tocan nada. Un por páxina.
 */
@Injectable()
export class StaffMembersStore {
  private readonly api = inject(Api);
  private request = 0;

  readonly members = signal<readonly StaffMemberResponse[]>([]);
  readonly state = signal<LoadState>('idle');

  /** Pide a lista; un erro con lista xa á vista non a borra. A última petición manda. */
  async load(slug: string): Promise<void> {
    const request = ++this.request;
    if (this.state() !== 'ready') this.state.set('loading');
    try {
      const members = await this.api.invoke(listStaff, { slug });
      if (request !== this.request) return;
      this.members.set(members);
      this.state.set('ready');
    } catch {
      if (request === this.request && this.state() !== 'ready') this.state.set('error');
    }
  }

  async invite(slug: string, body: InviteStaffMemberRequest): Promise<StaffMemberResponse> {
    const created = await this.api.invoke(inviteStaffMember, { slug, body });
    this.members.update((list) => [created, ...list.filter((m) => m.id !== created.id)]);
    return created;
  }

  async resend(slug: string, member: StaffMemberResponse): Promise<StaffMemberResponse> {
    return this.apply(
      await this.api.invoke(resendStaffInvitation, { slug, id: member.id, 'If-Match': ifMatch(member) }),
    );
  }

  async changeRole(slug: string, member: StaffMemberResponse, role: StaffRole): Promise<StaffMemberResponse> {
    return this.apply(
      await this.api.invoke(changeStaffRole, { slug, id: member.id, 'If-Match': ifMatch(member), body: { role } }),
    );
  }

  async deactivate(slug: string, member: StaffMemberResponse): Promise<StaffMemberResponse> {
    return this.apply(
      await this.api.invoke(deactivateStaffMember, { slug, id: member.id, 'If-Match': ifMatch(member) }),
    );
  }

  async reactivate(slug: string, member: StaffMemberResponse): Promise<StaffMemberResponse> {
    return this.apply(
      await this.api.invoke(reactivateStaffMember, { slug, id: member.id, 'If-Match': ifMatch(member) }),
    );
  }

  private apply(updated: StaffMemberResponse): StaffMemberResponse {
    this.members.update((list) => list.map((m) => (m.id === updated.id ? updated : m)));
    return updated;
  }
}

/** A versión que se ve, como `ETag` forte. */
function ifMatch(member: StaffMemberResponse): string {
  return `"${member.version}"`;
}
