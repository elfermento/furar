// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública síncrona das reservas para outros módulos (ADR-0005, ARQ-3): lecturas dentro do
 * concello actual (salvo {@link #hasResidentBookings}), con records propios da raíz e nunca o
 * dominio (A19). Non comproba permisos: xa os comprobou o caso de uso que chama; nunca se expón por
 * HTTP. Mínima na fase 1: crece co que pidan os avisos e o impacto dos cambios da instalación.
 */
public interface BookingQueries {

  /** A reserva do concello actual, se existe. */
  Optional<BookingSummary> find(UUID id);

  /**
   * A reserva do concello actual para contala nun correo, se existe e ten a quen escribir (nin
   * anonimizada nin do persoal sen correo).
   */
  Optional<BookingNotice> notice(UUID id);

  /**
   * Se a persoa reservou algunha vez como veciña, en <b>calquera</b> concello e en calquera estado
   * (a única lectura fóra do concello actual; ADR-0027, revisión do 2026-10-30): unha conta persoal
   * non acepta unha invitación ao persoal. Só di si ou non.
   *
   * @param subject {@code sub} do token
   */
  boolean hasResidentBookings(String subject);
}
