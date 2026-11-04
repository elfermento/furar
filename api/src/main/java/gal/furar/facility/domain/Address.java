// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Enderezo da instalación (especificación, bloque 1, «Enderezos»): vía, número, CP, localidade,
 * código INE do concello e coordenadas. Gárdase na instalación e non depende do buscador. Pode
 * faltar o número («s/n») e o punto no mapa (as dúas coordenadas á vez: sen mapa no despregue non
 * hai onde poñelo). Coordenadas con 6 decimais (~10 cm).
 */
public record Address(
    String street,
    @Nullable String streetNumber,
    String postalCode,
    String locality,
    String ineCode,
    @Nullable BigDecimal latitude,
    @Nullable BigDecimal longitude) {

  public static final int STREET_MAX = 200;
  public static final int NUMBER_MAX = 20;
  public static final int LOCALITY_MAX = 100;
  public static final int COORDINATE_SCALE = 6;
  private static final Pattern FIVE_DIGITS = Pattern.compile("^[0-9]{5}$");
  private static final BigDecimal LAT_MAX = BigDecimal.valueOf(90);
  private static final BigDecimal LON_MAX = BigDecimal.valueOf(180);

  /** Comproba e normaliza (espazos ao redor fóra, número baleiro = sen número). */
  public Address {
    street = strip(street);
    streetNumber = strip(streetNumber);
    postalCode = strip(postalCode);
    locality = strip(locality);
    ineCode = strip(ineCode);
    List<FieldViolation> missing = new ArrayList<>();
    required(missing, "street", street);
    required(missing, "postalCode", postalCode);
    required(missing, "locality", locality);
    required(missing, "ineCode", ineCode);
    // O punto vai enteiro ou non vai.
    if (latitude == null && longitude != null) {
      required(missing, "latitude", null);
    }
    if (longitude == null && latitude != null) {
      required(missing, "longitude", null);
    }
    if (!missing.isEmpty()) {
      throw new DomainException(FacilityError.ADDRESS_REQUIRED, Map.of(), missing);
    }
    List<FieldViolation> invalid = new ArrayList<>();
    size(invalid, "street", street, STREET_MAX);
    if (streetNumber != null) {
      size(invalid, "streetNumber", streetNumber, NUMBER_MAX);
    }
    size(invalid, "locality", locality, LOCALITY_MAX);
    pattern(invalid, "postalCode", postalCode);
    pattern(invalid, "ineCode", ineCode);
    if (latitude != null && longitude != null) {
      range(invalid, "latitude", latitude, LAT_MAX);
      range(invalid, "longitude", longitude, LON_MAX);
    }
    if (!invalid.isEmpty()) {
      throw new DomainException(FacilityError.ADDRESS_INVALID, Map.of(), invalid);
    }
    if (latitude != null && longitude != null) {
      latitude = latitude.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
      longitude = longitude.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }
  }

  /** Se ten punto no mapa. */
  public boolean hasPoint() {
    return latitude != null && longitude != null;
  }

  private static @Nullable String strip(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  private static void required(List<FieldViolation> out, String field, @Nullable Object value) {
    if (value == null) {
      out.add(FieldViolation.of("address." + field, ValidationError.REQUIRED));
    }
  }

  private static void size(List<FieldViolation> out, String field, String value, int max) {
    if (value.length() > max) {
      out.add(
          FieldViolation.of(
              "address." + field, ValidationError.SIZE, Map.of("min", 1, "max", max)));
    }
  }

  private static void pattern(List<FieldViolation> out, String field, String value) {
    if (!FIVE_DIGITS.matcher(value).matches()) {
      out.add(FieldViolation.of("address." + field, ValidationError.PATTERN));
    }
  }

  private static void range(
      List<FieldViolation> out, String field, BigDecimal value, BigDecimal max) {
    if (value.abs().compareTo(max) > 0) {
      out.add(
          FieldViolation.of(
              "address." + field, ValidationError.RANGE, Map.of("min", max.negate(), "max", max)));
    }
  }
}
