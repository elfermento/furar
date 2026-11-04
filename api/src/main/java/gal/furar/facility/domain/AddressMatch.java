// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

/**
 * Enderezo que propón o buscador para encher o formulario (especificación, bloque 1, «Enderezos»).
 * Pode vir incompleto (unha vía sen número, un CP que non é único): o xestor complétao e quen
 * valida é {@link Address} ao gardar. Coordenadas con 6 decimais.
 */
public record AddressMatch(
    @Nullable String street,
    @Nullable String streetNumber,
    @Nullable String postalCode,
    @Nullable String locality,
    @Nullable String ineCode,
    @Nullable BigDecimal latitude,
    @Nullable BigDecimal longitude) {

  public AddressMatch {
    latitude = scale(latitude);
    longitude = scale(longitude);
  }

  private static @Nullable BigDecimal scale(@Nullable BigDecimal value) {
    return value == null ? null : value.setScale(Address.COORDINATE_SCALE, RoundingMode.HALF_UP);
  }
}
