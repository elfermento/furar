// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.DomainException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Clave de reintento de «reservar» (ADR-0025, punto 19; glosario): o texto opaco da cabeceira
 * {@code Idempotency-Key} (borrador IETF {@code draft-ietf-httpapi-idempotency-key-header}). A web
 * manda un UUID por intención de reservar; a API só comproba a forma: de 1 a {@value #MAX_LENGTH}
 * caracteres ASCII visibles, sen comiñas nin barra inversa. Vale tamén entre comiñas, como a cadea
 * dun campo estruturado (RFC 8941) que usa o borrador: {@code "8e03978e-…"}.
 */
public record IdempotencyKey(String value) {

  public static final String HEADER = "Idempotency-Key";
  public static final int MAX_LENGTH = 255;

  private static final Pattern FORMAT = Pattern.compile("^[!#-\\[\\]-~]{1," + MAX_LENGTH + "}$");

  public IdempotencyKey {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid idempotency key");
    }
  }

  /**
   * Le a cabeceira. Sen ela ou baleira → {@code booking.idempotency-key-missing}; mal formada →
   * {@code booking.idempotency-key-invalid} (os dous, 400).
   */
  public static IdempotencyKey parse(@Nullable String header) {
    if (header == null || header.isBlank()) {
      throw new DomainException(BookingError.IDEMPOTENCY_KEY_MISSING);
    }
    String value = header.strip();
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    if (!FORMAT.matcher(value).matches()) {
      throw new DomainException(BookingError.IDEMPOTENCY_KEY_INVALID, Map.of("max", MAX_LENGTH));
    }
    return new IdempotencyKey(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
