// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: creouse a tempada dunha entidade, tamén ao aprobar a súa solicitude (ADR-0039,
 * punto 8); un só correo á entidade con todas as sesións. Sen persoas, só ids. As sesións non
 * publican {@link BookingConfirmed}.
 */
public record BookingSeriesCreated(UUID municipalityId, UUID seriesId) implements TenantAware {

  public BookingSeriesCreated {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(seriesId, "seriesId");
  }
}
