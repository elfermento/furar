// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Vista mínima e estable dunha instalación para outros módulos (A19: só tipos da raíz e de Java).
 *
 * @param status {@code ACTIVE}, {@code TEMPORARILY_CLOSED} ou {@code WITHDRAWN}
 * @param onlineBooking se se reserva por internet
 * @param showFreeSlots se, sen reserva por internet, a ficha amosa os ocos libres
 * @param address o enderezo postal (o {@code LOCATION} do {@code .ics}, o detalle do xestor); nulo
 *     nun borrador que aínda non o ten (nunca nunha publicada)
 */
public record FacilitySummary(
    UUID id,
    String name,
    String status,
    boolean onlineBooking,
    boolean showFreeSlots,
    @Nullable Address address) {

  /** Se está de baixa ({@code WITHDRAWN}): nunca abre nin se reserva. */
  public boolean withdrawn() {
    return WITHDRAWN.equals(status);
  }

  private static final String WITHDRAWN = "WITHDRAWN";

  /** Enderezo postal, sen coordenadas nin código INE. */
  public record Address(
      String street, @Nullable String streetNumber, String postalCode, String locality) {

    /** «Rúa do Alto, 12, 15570 Narón». */
    public String oneLine() {
      String number = streetNumber == null || streetNumber.isBlank() ? "" : ", " + streetNumber;
      return street + number + ", " + postalCode + " " + locality;
    }
  }
}
