// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.notification.domain.PendingJoinRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * As solicitudes de alta que esperan un reintento do correo (ADR-0042, revisión do 2026-11-17).
 * Táboa da plataforma, sen concello.
 */
public interface PendingJoinRequests {

  /** Garda unha solicitude cuxo primeiro envío fallou; o seguinte intento, en {@code next}. */
  void save(PendingJoinRequest request, Instant next);

  /** As que xa tocan ({@code next_attempt_at <= now}), as máis antigas primeiro. */
  List<PendingJoinRequest> due(Instant now, int limit);

  /**
   * Reserva o intento seguinte: suma un intento e pasa o próximo a {@code next}, só se aínda tocaba
   * en {@code now}. Con varias réplicas, só unha a reserva.
   *
   * @return se a reservou
   */
  boolean claim(UUID id, Instant now, Instant next);

  /** Bórraa, xa enviada. */
  void delete(UUID id);

  /** Borra as que chegaron antes de {@code cutoff} sen chegar a enviarse; devolve cantas. */
  int purgeReceivedBefore(Instant cutoff);
}
