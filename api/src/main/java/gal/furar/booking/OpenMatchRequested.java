// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: alguén pediu unirse a unha partida «Con aprobación» (ADR-0040, punto 5); avísase
 * a quen organiza. Sen persoas, só ids. Leva sempre o concello.
 *
 * @param playerId a solicitude: a clave do correo
 */
public record OpenMatchRequested(UUID municipalityId, UUID matchId, UUID playerId)
    implements TenantAware {

  public OpenMatchRequested {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(matchId, "matchId");
    Objects.requireNonNull(playerId, "playerId");
  }
}
