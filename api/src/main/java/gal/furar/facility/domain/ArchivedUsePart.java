// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Parte que se quitou dun uso e quedou arquivada (as reservas do dominio 2 poden apuntar a ela). O
 * agregado {@link FacilityUse} só leva as vivas; esta forma só a usa a portabilidade (ADR-0018),
 * para que o ficheiro furar-export non perda as arquivadas.
 */
public record ArchivedUsePart(UUID id, UseId useId, String name, int position, Instant archivedAt) {

  public ArchivedUsePart {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(archivedAt, "archivedAt");
  }
}
