// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Unha solicitude de alta dun concello («¿Es un concello?», ADR-0042): quen a pide e como
 * contestarlle. Vai por correo a quen opera o servizo; só se garda mentres o correo non sae ({@link
 * PendingJoinRequest}).
 *
 * <p>{@link #toString()} só leva o concello: o resto son datos persoais e nunca deben chegar a un
 * rexistro.
 *
 * @param municipality concello ou entidade que pide a alta
 * @param name nome e apelidos de quen a pide
 * @param role cargo, se o deu
 * @param email correo ao que contestar
 * @param phone teléfono
 * @param message mensaxe, se a escribiu
 */
public record JoinRequest(
    String municipality,
    String name,
    @Nullable String role,
    String email,
    String phone,
    @Nullable String message) {

  public static final int MUNICIPALITY_MAX = 160;
  public static final int NAME_MAX = 120;
  public static final int ROLE_MAX = 120;
  public static final int EMAIL_MIN = 3;
  public static final int EMAIL_MAX = 254;
  public static final int PHONE_MIN = 3;
  public static final int PHONE_MAX = 30;
  public static final int MESSAGE_MAX = 2000;

  /** Un correo completo, co dominio e o seu punto (coma os do concello). */
  public static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

  /** Coma os teléfonos das persoas e entidades nas reservas. */
  public static final String PHONE_PATTERN = "^[0-9+() .-]+$";

  private static final Pattern EMAIL = Pattern.compile(EMAIL_PATTERN);
  private static final Pattern PHONE = Pattern.compile(PHONE_PATTERN);

  public JoinRequest {
    List<FieldViolation> violations = new ArrayList<>();
    municipality = required("municipality", municipality, 1, MUNICIPALITY_MAX, violations);
    name = required("name", name, 1, NAME_MAX, violations);
    role = optional("role", role, ROLE_MAX, violations);
    email = required("email", email, EMAIL_MIN, EMAIL_MAX, violations);
    if (email != null && !EMAIL.matcher(email).matches()) {
      violations.add(
          FieldViolation.of("email", ValidationError.PATTERN, Map.of("regexp", EMAIL_PATTERN)));
    }
    phone = required("phone", phone, PHONE_MIN, PHONE_MAX, violations);
    if (phone != null && !PHONE.matcher(phone).matches()) {
      violations.add(
          FieldViolation.of("phone", ValidationError.PATTERN, Map.of("regexp", PHONE_PATTERN)));
    }
    message = optional("message", message, MESSAGE_MAX, violations);
    if (!violations.isEmpty()) {
      throw DomainException.validation(violations);
    }
  }

  /** Sen espazos ao redor; obrigatorio e entre {@code min} e {@code max} caracteres. */
  private static @Nullable String required(
      String field, @Nullable String value, int min, int max, List<FieldViolation> violations) {
    if (value == null || value.isBlank()) {
      violations.add(FieldViolation.of(field, ValidationError.REQUIRED));
      return null;
    }
    String stripped = value.strip();
    if (stripped.length() < min || stripped.length() > max) {
      violations.add(
          FieldViolation.of(field, ValidationError.SIZE, Map.of("min", min, "max", max)));
      return null;
    }
    return stripped;
  }

  /** Sen espazos ao redor; baleiro = {@code null}; como moito {@code max} caracteres. */
  private static @Nullable String optional(
      String field, @Nullable String value, int max, List<FieldViolation> violations) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String stripped = value.strip();
    if (stripped.length() > max) {
      violations.add(FieldViolation.of(field, ValidationError.SIZE, Map.of("min", 0, "max", max)));
      return null;
    }
    return stripped;
  }

  @Override
  public String toString() {
    return "JoinRequest[municipality=" + municipality + "]";
  }
}
