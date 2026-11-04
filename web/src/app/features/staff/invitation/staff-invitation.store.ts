// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { acceptStaffInvitation } from '../../../api/fn/staff/accept-staff-invitation';
import { getMyStaffAccess } from '../../../api/fn/staff/get-my-staff-access';
import { previewStaffInvitation } from '../../../api/fn/staff/preview-staff-invitation';
import type { AcceptedStaffInvitationResponse } from '../../../api/models/accepted-staff-invitation-response';
import type { MyStaffAccessResponse } from '../../../api/models/my-staff-access-response';
import type { StaffInvitationPreviewResponse } from '../../../api/models/staff-invitation-preview-response';

export type { MyStaffAccessResponse, StaffInvitationPreviewResponse };

/**
 * API da invitación (D4-15): o token vai sempre no corpo dun `POST`, nunca no URL. A lectura do
 * acceso (`GET …/me/staff`) non pasa pola caché de `StaffAccessService`: despois de aceptar, o que
 * conta é o de agora.
 */
@Injectable()
export class StaffInvitationStore {
  private readonly api = inject(Api);

  preview(slug: string, token: string): Promise<StaffInvitationPreviewResponse> {
    return this.api.invoke(previewStaffInvitation, { slug, body: { token } });
  }

  accept(slug: string, token: string): Promise<AcceptedStaffInvitationResponse> {
    return this.api.invoke(acceptStaffInvitation, { slug, body: { token } });
  }

  myAccess(slug: string): Promise<MyStaffAccessResponse> {
    return this.api.invoke(getMyStaffAccess, { slug });
  }
}
