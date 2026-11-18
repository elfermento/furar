// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: as esperas dunha instalación cancelaronse porque deixou de reservarse por
 * internet, se pechou ou se deu de baixa (ADR-0033, revisión do 2026-11-11). Un por cambio, sen
 * ningún dato de persoa: o rexistro de publicacións gárdao. Os escoitadores (o correo «Lista de
 * espera cancelada») reciben despois do commit e piden a quen escribir a {@link
 * BookingQueries#cancelledWaitNotices}. Leva sempre o concello (ADR-0006).
 *
 * @param cancellationId distingue esta cancelación: un reintento non repite o correo
 */
public record BookingWaitsCancelled(
    UUID municipalityId, UUID cancellationId, UUID facilityId, Instant cancelledAt)
    implements TenantAware {

  public BookingWaitsCancelled {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(cancellationId, "cancellationId");
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(cancelledAt, "cancelledAt");
  }
}
