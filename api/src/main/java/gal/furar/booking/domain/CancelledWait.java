// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Unha espera cancelada porque a instalación deixou de reservarse por internet, se pechou ou se deu
 * de baixa (ADR-0033, revisión do 2026-11-11): o mínimo para escribirlle despois do commit. Sen
 * conta nin nome.
 *
 * @param id o da espera que se borrou
 * @param emailKey a quen escribir ({@code EmailKey.of})
 */
public record CancelledWait(
    UUID id, UUID facilityId, String emailKey, Language language, Instant start, Instant end) {

  public CancelledWait {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(emailKey, "emailKey");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
  }
}
