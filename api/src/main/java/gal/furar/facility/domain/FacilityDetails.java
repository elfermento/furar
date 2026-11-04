// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Tarxeta «Datos» (especificación, bloque 1): deportes, tipo, código RIDEGA, teléfono, correo,
 * reserva por internet ou ocos libres e cuberta. Os campos propios do concello van á parte
 * (D1-11c). Só a reserva por internet é obrigatoria; se non hai reserva por internet, pódense
 * mostrar os ocos libres, pero nunca as dúas cousas á vez.
 *
 * @param sports na orde na que se gardaron; a mesma referencia dúas veces chega á base, que a
 *     rexeita (409 {@code facility.sport-duplicated})
 * @param covered nulo = sen dato
 */
public record FacilityDetails(
    List<SportRef> sports,
    @Nullable FacilityType type,
    @Nullable String ridegaCode,
    @Nullable String phone,
    @Nullable String email,
    boolean onlineBooking,
    boolean showFreeSlots,
    @Nullable Boolean covered) {

  public static final int SPORTS_MAX = 30;
  public static final int RIDEGA_MAX = 30;
  public static final int EMAIL_MAX = 254;
  private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ]{6,20}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  /** Os da alta: sen deportes nin datos, reserva por internet e sen ocos visibles. */
  public static final FacilityDetails EMPTY =
      new FacilityDetails(List.of(), null, null, null, null, true, false, null);

  /** Comproba e normaliza (espazos ao redor fóra; texto baleiro = sen dato). */
  public FacilityDetails {
    sports = List.copyOf(sports);
    ridegaCode = strip(ridegaCode);
    phone = strip(phone);
    email = strip(email);
    if (onlineBooking && showFreeSlots) {
      throw new DomainException(FacilityError.BOOKING_MODE_INVALID);
    }
    List<FieldViolation> invalid = new ArrayList<>();
    if (sports.size() > SPORTS_MAX) {
      invalid.add(
          FieldViolation.of(
              "details.sports", ValidationError.SIZE, Map.of("min", 0, "max", SPORTS_MAX)));
    }
    if (ridegaCode != null && ridegaCode.length() > RIDEGA_MAX) {
      invalid.add(
          FieldViolation.of(
              "details.ridegaCode", ValidationError.SIZE, Map.of("min", 1, "max", RIDEGA_MAX)));
    }
    if (phone != null && !PHONE.matcher(phone).matches()) {
      invalid.add(FieldViolation.of("details.phone", ValidationError.PATTERN));
    }
    if (email != null && (email.length() > EMAIL_MAX || !EMAIL.matcher(email).matches())) {
      invalid.add(FieldViolation.of("details.email", ValidationError.PATTERN));
    }
    if (!invalid.isEmpty()) {
      throw new DomainException(FacilityError.DETAILS_INVALID, Map.of(), invalid);
    }
  }

  private static @Nullable String strip(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }
}
