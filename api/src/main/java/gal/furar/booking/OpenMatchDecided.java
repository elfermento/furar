// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: quen organiza aceptou ou rexeitou unha solicitude (ADR-0040, punto 4); avísase a
 * quen a pediu. Sen persoas, só ids. Leva sempre o concello.
 *
 * @param noticeId distingue esta publicación
 */
public record OpenMatchDecided(
    UUID municipalityId, UUID matchId, UUID playerId, UUID noticeId, boolean accepted)
    implements TenantAware {

  public OpenMatchDecided {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(matchId, "matchId");
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(noticeId, "noticeId");
  }
}
