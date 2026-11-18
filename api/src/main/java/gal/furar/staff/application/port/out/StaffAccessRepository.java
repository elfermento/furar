// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * As pertenzas activas do concello actual, para autorizar cada petición (ADR-0027, puntos 2 e 5).
 * Sempre dentro dunha transacción e con concello: sen elas, a RLS non devolve nada.
 */
public interface StaffAccessRepository {

  /** A pertenza activa da persoa ({@code sub}), se a ten. */
  Optional<ActiveMembership> active(String subject);

  /**
   * Rexistra un acceso: o último pasa a anterior se {@code authTime} é máis novo. Das peticións
   * simultáneas co mesmo {@code auth_time}, só unha o consegue.
   *
   * @return se este acceso cambiou a fila
   */
  boolean recordSignIn(String subject, Instant authTime);
}
