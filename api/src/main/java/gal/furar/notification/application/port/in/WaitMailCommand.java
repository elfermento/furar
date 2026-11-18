// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Que liberación avisar, desde un evento de {@code booking} (ADR-0033, punto 7).
 *
 * @param releaseId distingue esta liberación: o reintento non repite
 * @param start inicio da franxa que quedou libre
 * @param releasedAt cando quedou libre: as esperas de despois non reciben este aviso
 */
public record WaitMailCommand(UUID releaseId, UUID useId, Instant start, Instant releasedAt) {

  public WaitMailCommand {
    Objects.requireNonNull(releaseId, "releaseId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(releasedAt, "releasedAt");
  }
}
