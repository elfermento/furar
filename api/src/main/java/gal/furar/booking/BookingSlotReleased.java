// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: unha franxa dun uso pola que alguén esperaba pasou de ocupada a libre (ADR-0033,
 * punto 7). Un por uso e franxa, sen ningún dato de persoa: o rexistro de publicacións gárdao.
 * Publícase dentro da transacción que solta a ocupación e os escoitadores (o correo «Quedou libre»)
 * reciben despois do commit; a quen avisar pídeno a {@link BookingQueries#waitNotices}. Leva sempre
 * o concello (ADR-0006).
 *
 * @param releaseId distingue esta liberación doutra da mesma franxa: un reintento non repite o
 *     aviso e unha segunda liberación si avisa
 * @param releasedAt o instante da transacción: as esperas creadas despois son dunha ocupación
 *     posterior e non reciben este aviso
 */
public record BookingSlotReleased(
    UUID municipalityId,
    UUID releaseId,
    UUID facilityId,
    UUID useId,
    Instant start,
    Instant end,
    Instant releasedAt)
    implements TenantAware {

  public BookingSlotReleased {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(releaseId, "releaseId");
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(releasedAt, "releasedAt");
  }
}
