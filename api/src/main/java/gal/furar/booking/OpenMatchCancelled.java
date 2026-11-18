// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: unha partida aberta cancelouse (ADR-0040, puntos 6 e 7), coa súa reserva ou por
 * non chegar ao mínimo. Sen persoas, só ids: os escoitadores (o correo) reciben despois do commit e
 * len a quen escribir con {@link BookingQueries#openMatchNotice}. Leva sempre o concello
 * (ADR-0006).
 *
 * @param noticeId distingue esta publicación: un reintento non repite o correo
 */
public record OpenMatchCancelled(UUID municipalityId, UUID matchId, UUID noticeId)
    implements TenantAware {

  public OpenMatchCancelled {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(matchId, "matchId");
    Objects.requireNonNull(noticeId, "noticeId");
  }
}
