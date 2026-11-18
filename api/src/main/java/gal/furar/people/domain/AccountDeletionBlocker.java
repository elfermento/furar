// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.domain;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Algo que impide eliminar a conta (ADR-0043): hai que cancelalo ou pedir que o quiten antes.
 *
 * @param kind que é
 * @param municipalityId en que concello; nulo só na administración da plataforma
 * @param eventId o evento ou a actividade, só en {@link Kind#EVENT}
 * @param eventNameGl o seu nome en galego, só en {@link Kind#EVENT}
 * @param eventNameEs o seu nome en castelán, só en {@link Kind#EVENT}
 * @param count cantas (reservas, partidas, inscricións, pertenzas)
 */
public record AccountDeletionBlocker(
    Kind kind,
    @Nullable UUID municipalityId,
    @Nullable UUID eventId,
    @Nullable String eventNameGl,
    @Nullable String eventNameEs,
    long count) {

  public AccountDeletionBlocker {
    Objects.requireNonNull(kind, "kind");
  }

  /** Os tipos, na orde en que se mostran. */
  public enum Kind {
    /** Reservas confirmadas que aínda non remataron. */
    BOOKING,
    /** Partidas abertas que organiza e que aínda non se xogaron. */
    OPEN_MATCH,
    /** Inscrición (pide praza ou ten praza) nun evento ou actividade que aínda non rematou. */
    EVENT,
    /** Persoal activo dun concello: alguén da xestión ten que desactivala. */
    STAFF,
    /** Administración da plataforma. */
    PLATFORM_ADMIN
  }
}
