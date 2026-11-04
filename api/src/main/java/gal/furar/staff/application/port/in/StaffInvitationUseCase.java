// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

import gal.furar.staff.domain.StaffRole;
import org.jspecify.annotations.Nullable;

/**
 * A persoa invitada ve e acepta a súa invitación (plan do dominio 4, §1.2.6, §1.2.13 e §2.3). O
 * token chega no corpo dun {@code POST} e só vive en memoria: non vai a rexistros, eventos nin
 * respostas. Un token descoñecido, mal formado, usado, substituído, doutro concello ou dunha
 * pertenza que xa non está invitada dá sempre o mesmo {@code 404 staff.invitation-invalid}.
 */
public interface StaffInvitationUseCase {

  /**
   * O que di a invitación, sen sesión e sen gastala (abrir a ligazón non consome nada).
   *
   * @throws gal.furar.shared.domain.DomainException {@code staff.invitation-invalid} ou {@code
   *     staff.invitation-expired}
   */
  StaffInvitationPreview preview(@Nullable String token);

  /**
   * Acepta a invitación coa persoa da sesión: a pertenza pasa a activa co seu {@code sub} e o
   * acceso vale desde a seguinte petición.
   *
   * @return o rol no concello
   * @throws gal.furar.shared.domain.DomainException {@code staff.invitation-invalid}, {@code
   *     staff.invitation-expired}, {@code staff.invitation-platform-account}, {@code
   *     staff.invitation-own}, {@code staff.invitation-email-mismatch} ou {@code
   *     staff.already-member}
   */
  StaffRole accept(AcceptStaffInvitationCommand command);
}
