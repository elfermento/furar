// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * O aviso a unha persoa da lista de espera de que hai unha praza libre (ADR-0041). Ata {@code
 * exclusiveUntil} só o sabe ela (e as avisadas con ela, se hai varias prazas); despois avísase
 * tamén á seguinte, pero ela segue podendo confirmar mentres a praza siga libre.
 *
 * @param notifiedAt cando empeza a contar e sae o correo (de noite, ás 8:00)
 * @param exclusiveUntil ata cando é só súa, sen contar o tramo de silencio nocturno
 * @param mailedAt cando saíu o correo; nulo mentres non saia
 */
public record WaitlistNotice(
    Instant notifiedAt, Instant exclusiveUntil, @Nullable Instant mailedAt) {

  public WaitlistNotice {
    Objects.requireNonNull(notifiedAt, "notifiedAt");
    Objects.requireNonNull(exclusiveUntil, "exclusiveUntil");
    if (!exclusiveUntil.isAfter(notifiedAt)) {
      throw new IllegalArgumentException("The exclusive time ends after it starts");
    }
  }

  /** Se aínda é só súa (tamén se o aviso empeza ás 8:00 e aínda é de noite). */
  public boolean exclusive(Instant now) {
    return now.isBefore(exclusiveUntil);
  }
}
