// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Código da reserva (ADR-0025, punto 13; glosario): {@code FUR-} e catro caracteres dun alfabeto de
 * 32 sen {@code 0}, {@code O}, {@code 1} nin {@code I}, que non se confunden ao dictalo no
 * mostrador. Único no concello: se choca ao gardar, quen reserva xera outro (32⁴ ≈ un millón).
 */
public record BookingCode(String value) {

  /** Base32 sen 0/O/1/I: 24 letras e 8 díxitos. */
  public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  public static final String PREFIX = "FUR-";
  public static final int LENGTH = 4;

  private static final Pattern FORMAT = Pattern.compile("^FUR-[A-HJ-NP-Z2-9]{4}$");

  public BookingCode {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid booking code: " + value);
    }
  }

  /** Un código ao chou; a aleatoriedade vén de fóra para poder probalo. */
  public static BookingCode random(RandomGenerator random) {
    StringBuilder code = new StringBuilder(PREFIX);
    for (int i = 0; i < LENGTH; i++) {
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return new BookingCode(code.toString());
  }

  @Override
  public String toString() {
    return value;
  }
}
