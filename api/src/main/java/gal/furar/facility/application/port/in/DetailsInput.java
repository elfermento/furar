// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Tarxeta Datos tal como chega; o dominio ({@code FacilityDetails}) dí que non vale.
 *
 * @param entryControl con control de entrada (ADR-0032); nulo = como estaba
 */
public record DetailsInput(
    List<SportRefInput> sports,
    @Nullable FacilityType type,
    @Nullable String ridegaCode,
    @Nullable String phone,
    @Nullable String email,
    boolean onlineBooking,
    boolean showFreeSlots,
    @Nullable Boolean covered,
    @Nullable Boolean entryControl) {

  /** Sen dicir nada do control de entrada: queda como estaba. */
  public DetailsInput(
      List<SportRefInput> sports,
      @Nullable FacilityType type,
      @Nullable String ridegaCode,
      @Nullable String phone,
      @Nullable String email,
      boolean onlineBooking,
      boolean showFreeSlots,
      @Nullable Boolean covered) {
    this(sports, type, ridegaCode, phone, email, onlineBooking, showFreeSlots, covered, null);
  }

  public DetailsInput {
    sports = sports == null ? List.of() : List.copyOf(sports);
  }
}
