// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O resultado de ocupar a instalación cunha actividade do concello (ADR-0041).
 *
 * @param plan o calculado baixo o bloqueo da instalación
 * @param changed os días con choque xa non son os que viu o xestor: non se escribiu nada
 * @param sessions as sesións escritas, cada unha coa súa reserva (baleira se {@code changed})
 */
public record ActivityOccupation(ActivityPlan plan, boolean changed, List<Occupied> sessions) {

  public ActivityOccupation {
    Objects.requireNonNull(plan, "plan");
    sessions = List.copyOf(sessions);
  }

  /** Unha sesión escrita e a reserva que ocupa a instalación. */
  public record Occupied(LocalDate date, Instant start, Instant end, UUID bookingId) {

    public Occupied {
      Objects.requireNonNull(date, "date");
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
      Objects.requireNonNull(bookingId, "bookingId");
    }
  }
}
