// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import gal.furar.people.DependentSummary;
import gal.furar.shared.domain.DomainException;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * As persoas a cargo da conta dun correo, para o «Para quen» do mostrador (ADR-0031, punto 7).
 * Permiso {@code booking.book-for}. Sen conta ou sen fichas, a mesma lista baleira: ningunha
 * resposta di se un correo ten conta. Cada lectura queda no rexistro de actividade antes de
 * devolver nada, cunha pegada con clave do correo, e hai un máximo por persoa do persoal e hora.
 */
public interface HolderDependentsUseCase {

  /**
   * @param email o correo que escribiu o persoal; ou
   * @param bookingId a reserva que está cambiando (o seu correo)
   * @throws DomainException {@code booking.dependents-rate-limited} pasado o máximo por hora;
   *     {@code booking.not-found} se a reserva non existe; {@code common.invalid-parameter} sen
   *     ningún dos dous
   */
  List<DependentSummary> of(@Nullable String email, @Nullable BookingId bookingId);
}
