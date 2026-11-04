// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

/**
 * Invitar ao persoal e reenviar a invitación (plan do dominio 4, §1.2.6 e §1.2.7; permiso {@code
 * staff.invite}). O correo envíase despois do commit e fóra de toda transacción: non se poden
 * chamar dentro dunha.
 */
public interface InviteStaffUseCase {

  /**
   * Pertenza nova, invitada, e o seu correo.
   *
   * @throws gal.furar.shared.domain.DomainException {@code staff.email-invalid}, {@code
   *     staff.already-member} ou {@code staff.invitation-rate-limited}
   */
  StaffMemberView invite(InviteStaffMemberCommand command);

  /**
   * Outra invitación para unha pertenza aínda invitada: substitúe a anterior e conta outros 7 días.
   *
   * @throws gal.furar.shared.domain.DomainException {@code staff.not-found}, {@code
   *     common.stale-version}, {@code staff.transition-invalid} ou {@code
   *     staff.invitation-rate-limited}
   */
  StaffMemberView resend(ResendStaffInvitationCommand command);
}
