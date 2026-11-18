// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: o concello rexeitou unha solicitude de tempada (ADR-0039, punto 11); a mensaxe
 * vai por correo á entidade. Sen persoas nin a mensaxe, só ids.
 */
public record BookingSeriesRequestRejected(UUID municipalityId, UUID requestId)
    implements TenantAware {

  public BookingSeriesRequestRejected {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(requestId, "requestId");
  }
}
