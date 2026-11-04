// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web;

import gal.furar.staff.adapter.in.web.dto.ActivityActorKindValue;
import gal.furar.staff.adapter.in.web.dto.InvitationDeliveryValue;
import gal.furar.staff.adapter.in.web.dto.StaffInvitationResponse;
import gal.furar.staff.adapter.in.web.dto.StaffInviterResponse;
import gal.furar.staff.adapter.in.web.dto.StaffMemberResponse;
import gal.furar.staff.adapter.in.web.dto.StaffRoleValue;
import gal.furar.staff.adapter.in.web.dto.StaffStatusValue;
import gal.furar.staff.application.port.in.StaffMemberView;
import gal.furar.staff.domain.ResendAvailability;
import gal.furar.staff.domain.StaffInvitation;
import gal.furar.staff.domain.StaffMember;

/**
 * Pertenza ↔ DTO. O hash do token e quen pediu cada envío non saen da API; o correo e o nome dunha
 * pertenza anonimizada ou bloqueada, tampouco.
 */
final class StaffWebMapper {

  private StaffWebMapper() {}

  static StaffMemberResponse toResponse(StaffMemberView view) {
    StaffMember m = view.member();
    StaffInvitation i = view.invitation();
    ResendAvailability resend = view.resend();
    Long version = m.version();
    boolean hidden = m.email().isAnonymised() || view.identityBlocked();
    return new StaffMemberResponse(
        m.id().value(),
        hidden ? null : m.email().value(),
        m.email().isAnonymised(),
        view.identityBlocked(),
        hidden ? null : m.displayName(),
        StaffRoleValue.valueOf(m.role().name()),
        StaffStatusValue.valueOf(m.status().name()),
        m.language().code(),
        m.invitedAt(),
        new StaffInviterResponse(
            ActivityActorKindValue.valueOf(view.invitedBy().kind().name()),
            view.invitedBy().name()),
        m.acceptedAt(),
        m.deactivatedAt(),
        m.lastSignInAt(),
        i == null
            ? null
            : new StaffInvitationResponse(
                i.createdAt(),
                i.expiresAt(),
                InvitationDeliveryValue.valueOf(i.delivery().name()),
                resend == null ? null : resend.availableAt(),
                resend != null && resend.exhausted()),
        version == null ? 0 : version);
  }
}
