// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Un oco libre que se pode propoñer como parecido (ADR-0033, punto 9): dunha instalación e dun uso,
 * xa filtrado polas normas.
 *
 * @param facilityName para ordenar por nome
 * @param date o día en Europe/Madrid
 * @param end fin das franxas pedidas
 * @param startMinute o inicio no reloxo de parede de Europe/Madrid (0–1439)
 */
public record SlotCandidate(
    UUID facilityId,
    String facilityName,
    UUID useId,
    LocalDate date,
    Instant start,
    Instant end,
    int startMinute) {

  public SlotCandidate {
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
  }
}
