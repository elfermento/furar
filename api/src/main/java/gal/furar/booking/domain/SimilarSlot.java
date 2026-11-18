// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.util.Objects;

/** Un oco parecido ao pedido e a regra que o propón (ADR-0033, punto 9). */
public record SimilarSlot(Reason reason, SlotCandidate slot) {

  public SimilarSlot {
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(slot, "slot");
  }

  /** As regras, na orde en que se propoñen. */
  public enum Reason {
    /** Esta instalación e uso, o mesmo día: a franxa anterior ou a seguinte á pedida. */
    SAME_FACILITY_NEAR,
    /** Outra instalación co mesmo uso, libre á mesma hora. */
    OTHER_FACILITY_SAME_TIME,
    /** O mesmo día, a primeira hora libre despois da pedida. */
    LATER_SAME_DAY,
    /** Esta instalación e uso, sete días despois, á mesma hora de reloxo. */
    NEXT_WEEK
  }
}
