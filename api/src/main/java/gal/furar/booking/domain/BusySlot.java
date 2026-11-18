// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Unha hora ocupada dun uso (ADR-0033, puntos 1 e 2): unha franxa que se podería reservar se non
 * fose pola ocupación. Non di quen a ocupa, nin cantas reservas hai, nin se é o mesmo uso ou outro.
 *
 * @param start inicio real
 * @param end fin real da franxa
 * @param startMinute o inicio no reloxo de parede de Europe/Madrid (0–1439), para pintalo
 */
public record BusySlot(Instant start, Instant end, int startMinute) {

  public BusySlot {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
  }
}
