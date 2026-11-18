// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: a reserva dunha partida aberta cambiou de día ou de hora (ADR-0040, punto 6); se
 * o cambio do persoal a pechou, o correo dio. Sen persoas, só ids. Leva sempre o concello.
 *
 * @param noticeId distingue esta publicación: cada cambio avisa unha vez a cada persoa
 */
public record OpenMatchChanged(UUID municipalityId, UUID matchId, UUID noticeId)
    implements TenantAware {

  public OpenMatchChanged {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(matchId, "matchId");
    Objects.requireNonNull(noticeId, "noticeId");
  }
}
