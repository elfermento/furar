// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

/**
 * Cambiar o rol ({@code staff.manage}), desactivar e reactivar ({@code staff.deactivate}) unha
 * pertenza (plan do dominio 4, §1.2.8; ADR-0027, revisión do 2026-10-30): nunca queda o concello
 * sen xestor activo.
 */
public interface ManageStaffMemberUseCase {

  /**
   * @throws gal.furar.shared.domain.DomainException {@code staff.not-found}, {@code
   *     common.stale-version}, {@code staff.transition-invalid} ou {@code staff.last-manager}
   */
  StaffMemberView changeRole(ChangeStaffRoleCommand command);

  /**
   * @throws gal.furar.shared.domain.DomainException {@code staff.not-found}, {@code
   *     common.stale-version}, {@code staff.transition-invalid} ou {@code staff.last-manager}
   */
  StaffMemberView deactivate(DeactivateStaffMemberCommand command);

  /**
   * @throws gal.furar.shared.domain.DomainException {@code staff.not-found}, {@code
   *     common.stale-version}, {@code staff.transition-invalid}, {@code staff.anonymised}, {@code
   *     staff.already-member} (xa hai outra pertenza viva co mesmo correo ou a mesma persoa) ou
   *     {@code staff.personal-account} (a conta reservou como veciña mentres estaba desactivada)
   */
  StaffMemberView reactivate(ReactivateStaffMemberCommand command);
}
