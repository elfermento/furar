// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Decisión do xestor sobre unha reserva afectada (ADR-0022, punto 6): cancelala avisando por polo
 * menos unha canle, ou mantela sen avisar. A coherencia compróbaa {@code facility} (422 {@code
 * facility.decision-invalid}).
 */
public record BookingDecision(UUID bookingId, Action action, Set<Channel> channels) {

  public BookingDecision {
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(action, "action");
    channels = channels == null ? Set.of() : Set.copyOf(channels);
  }

  /** Cancelar ou manter. */
  public enum Action {
    CANCEL,
    KEEP
  }

  /** Por onde se avisa dunha cancelación. */
  public enum Channel {
    EMAIL,
    WEB
  }

  /** Cancelar con polo menos unha canle; manter sen ningunha («se se mantén, non se avisa»). */
  public boolean isCoherent() {
    return action == Action.CANCEL ? !channels.isEmpty() : channels.isEmpty();
  }
}
