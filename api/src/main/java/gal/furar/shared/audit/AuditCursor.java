// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Onde segue a seguinte páxina: o último evento lido, por {@code (occurred_at, id)}. Estable aínda
 * que entren eventos novos ou que varios teñan o mesmo instante (paxinación por chave, sen {@code
 * offset}).
 *
 * @param occurredAt cando pasou o último evento da páxina
 * @param id o seu identificador, que desfai os empates
 */
public record AuditCursor(Instant occurredAt, UUID id) {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  public AuditCursor {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(id, "id");
  }

  /** Texto opaco para o URL (base64url). */
  public String token() {
    return ENCODER.encodeToString((occurredAt + "|" + id).getBytes(StandardCharsets.UTF_8));
  }

  /** Le o texto de {@link #token()}; calquera outro → 400 {@code common.invalid-parameter}. */
  public static AuditCursor parse(String token) {
    try {
      String text = new String(DECODER.decode(token), StandardCharsets.UTF_8);
      int bar = text.indexOf('|');
      if (bar < 0) {
        throw invalid();
      }
      return new AuditCursor(
          Instant.parse(text.substring(0, bar)), UUID.fromString(text.substring(bar + 1)));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw invalid();
    }
  }

  private static DomainException invalid() {
    return new DomainException(CommonError.INVALID_PARAMETER, Map.of("name", "cursor"));
  }
}
