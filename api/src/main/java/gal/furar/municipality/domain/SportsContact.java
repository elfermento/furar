// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Contacto do servizo de deportes do concello (plan do dominio 2, P8): sae na ficha pública e no pé
 * dos correos das reservas. Todos opcionais; baleiro → {@code null}. Os erros saen por campo
 * ({@code sportsContact.*}).
 *
 * @param name nome do servizo ({@code Servizo Municipal de Deportes})
 * @param address enderezo de atención
 * @param phone teléfono
 * @param email correo
 */
public record SportsContact(
    @Nullable String name,
    @Nullable String address,
    @Nullable String phone,
    @Nullable String email) {

  public static final SportsContact EMPTY = new SportsContact(null, null, null, null);

  public static final int TEXT_MAX = 200;
  public static final int PHONE_MAX = 30;

  static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  static final Pattern PHONE = Pattern.compile("^[0-9+()./ -]+$");

  public SportsContact {
    name = blankToNull(name);
    address = blankToNull(address);
    phone = blankToNull(phone);
    email = blankToNull(email);
    List<FieldViolation> errors = new ArrayList<>();
    size(errors, "name", name, TEXT_MAX);
    size(errors, "address", address, TEXT_MAX);
    if (phone != null && (phone.length() > PHONE_MAX || !PHONE.matcher(phone).matches())) {
      errors.add(FieldViolation.of("sportsContact.phone", ValidationError.PATTERN));
    }
    if (email != null && (email.length() > TEXT_MAX || !EMAIL.matcher(email).matches())) {
      errors.add(FieldViolation.of("sportsContact.email", ValidationError.PATTERN));
    }
    if (!errors.isEmpty()) {
      throw DomainException.validation(errors);
    }
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static void size(
      List<FieldViolation> errors, String field, @Nullable String value, int max) {
    if (value != null && value.length() > max) {
      errors.add(
          FieldViolation.of("sportsContact." + field, ValidationError.SIZE, Map.of("max", max)));
    }
  }
}
