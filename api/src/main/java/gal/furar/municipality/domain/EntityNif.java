// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.ValidationError;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * NIF da entidade responsable (RD 1065/2007 art. 22.1; Orden EHA/451/2008 arts. 2 a 5): letra da
 * forma xurídica, 7 díxitos e un carácter de control. Vale calquera letra de entidade (P concellos
 * e mancomunidades, Q organismos, G fundacións, A/B sociedades…); o control pode ser o díxito ou a
 * súa letra equivalente ({@code JABCDEFGHI}) para todas, porque non hai norma publicada que fixe
 * cal. Un DNI ou NIE (persoas) rexéitase cun erro propio. A web aplica as mesmas regras ({@code
 * entity-nif.ts}).
 */
public final class EntityNif {

  private static final Pattern SEPARATORS = Pattern.compile("[\\s\\u00A0.-]");
  private static final Pattern FORMAT = Pattern.compile("^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$");
  private static final Pattern PERSON =
      Pattern.compile("^(?:[0-9]{8}[A-Z]|[XYZKLM][0-9]{7}[A-Z])$");
  private static final String CONTROL_LETTERS = "JABCDEFGHI";

  private EntityNif() {}

  /**
   * Maiúsculas e sen espazos, puntos nin guións ({@code q-1573421 c} → {@code Q1573421C}); sen nada
   * que quede, {@code null}.
   */
  public static @Nullable String normalise(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String nif = SEPARATORS.matcher(value).replaceAll("").toUpperCase(Locale.ROOT);
    return nif.isEmpty() ? null : nif;
  }

  /**
   * O erro dun NIF xa normalizado, se o ten: {@link MunicipalityError#NIF_PERSONAL} se parece dunha
   * persoa, {@link ValidationError#PATTERN} se non ten a forma e {@link
   * MunicipalityError#NIF_CHECK_DIGIT} se o control non casa.
   */
  public static Optional<ErrorCode> check(String nif) {
    if (PERSON.matcher(nif).matches()) {
      return Optional.of(MunicipalityError.NIF_PERSONAL);
    }
    if (!FORMAT.matcher(nif).matches()) {
      return Optional.of(ValidationError.PATTERN);
    }
    int control = control(nif.substring(1, 8));
    char given = nif.charAt(8);
    boolean matches = given == (char) ('0' + control) || given == CONTROL_LETTERS.charAt(control);
    return matches ? Optional.empty() : Optional.of(MunicipalityError.NIF_CHECK_DIGIT);
  }

  /** Se o valor, normalizado, é un NIF de entidade válido. */
  public static boolean isValid(@Nullable String value) {
    String nif = normalise(value);
    return nif != null && check(nif).isEmpty();
  }

  /**
   * Os díxitos das posicións pares súmanse; os das impares dóbranse e súmanse as cifras do produto.
   * O control é o que falta para a decena seguinte (0 se xa o é).
   */
  private static int control(String digits) {
    int sum = 0;
    for (int i = 0; i < digits.length(); i++) {
      int n = digits.charAt(i) - '0';
      if (i % 2 == 0) {
        int doubled = 2 * n;
        sum += doubled / 10 + doubled % 10;
      } else {
        sum += n;
      }
    }
    return (10 - sum % 10) % 10;
  }
}
