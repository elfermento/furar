// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un oco libre dun uso: unha franxa que se pode reservar agora mesmo (ADR-0025, puntos 6 e 7).
 *
 * @param start inicio real
 * @param end fin real da primeira franxa
 * @param startMinute o inicio no reloxo de parede de Europe/Madrid (0–1439), para pintalo
 * @param maxSlots cantas franxas seguidas se poden reservar desde aquí (≥ 1): as libres seguidas no
 *     mesmo anaco aberto, ata a duración máxima do uso
 * @param freeParts as partes libres na súa orde, só nos usos por partes (baleira nos demais)
 * @param freeSeats as prazas libres, só nos usos por prazas
 */
public record FreeSlot(
    Instant start,
    Instant end,
    int startMinute,
    int maxSlots,
    List<UUID> freeParts,
    @Nullable Integer freeSeats) {

  public FreeSlot {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    freeParts = List.copyOf(freeParts);
    if (maxSlots < 1) {
      throw new IllegalArgumentException("A free slot fits at least one slot: " + maxSlots);
    }
  }
}
