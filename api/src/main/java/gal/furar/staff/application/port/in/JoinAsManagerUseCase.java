// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.in;

/**
 * «Engadirme como xestor» (ADR-0028, revisión do 2026-11-11): a administración da plataforma pasa a
 * ser xestora activa do concello actual, sen invitación nin correo, e queda no rexistro ({@code
 * staff.join}). Permiso {@code staff.join}.
 */
public interface JoinAsManagerUseCase {

  /**
   * Idempotente: se xa é xestora activa, devolve a súa pertenza sen cambiar nada nin rexistrar
   * outra vez.
   *
   * @throws gal.furar.shared.domain.DomainException {@code auth.email-not-verified} sen correo
   *     verificado; {@code staff.join-personal-account} se a conta reservou como veciña; {@code
   *     staff.already-member} se o seu correo xa é doutra persoa activa no concello
   */
  StaffMemberView join();
}
