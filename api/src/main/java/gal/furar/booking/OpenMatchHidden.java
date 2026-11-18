// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: a xestión ocultou unha partida aberta (ADR-0040, punto 8); avísase a quen
 * organiza. Volver mostrala non publica nada. Sen persoas, só ids. Leva sempre o concello.
 *
 * @param noticeId distingue esta publicación
 */
public record OpenMatchHidden(UUID municipalityId, UUID matchId, UUID noticeId)
    implements TenantAware {

  public OpenMatchHidden {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(matchId, "matchId");
    Objects.requireNonNull(noticeId, "noticeId");
  }
}
