// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * A ligazón «Non quero recordatorios» do correo (ADR-0035): proba asinada de que a deu Furar para
 * unha reserva dun concello, sen sesión e sen datos persoais no enderezo. Abrila desactiva os
 * recordatorios do correo desa reserva nese concello.
 *
 * <p>Formato ({@code v1}), en ASCII seguro para un URL: {@code v1.<reserva>.<caduca, segundos
 * Unix>.<HMAC>}. O HMAC-SHA256 vai sobre {@code reminder-opt-out.<concello>.} e todo o anterior ao
 * último punto, en base64url sen recheo. A clave é a mesma ca a pegada do rexistro de actividade
 * ({@code FURAR_AUDIT_HASH_SECRET}, só a ten a API): o prefixo separa os dous usos, e así non fai
 * falta outro segredo.
 *
 * <p>Vale {@link #VALIDITY} desde que se envía o correo: abonda para usala desde o propio
 * recordatorio e non deixa vivas para sempre as ligazóns de correos vellos.
 */
public final class ReminderOptOutToken {

  /** Versión do formato. */
  public static final String VERSION = "v1";

  /** Canto vale a ligazón desde que sae o correo. */
  public static final Duration VALIDITY = Duration.ofDays(60);

  private static final String ALGORITHM = "HmacSHA256";
  private static final String PURPOSE = "reminder-opt-out.";
  private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

  private ReminderOptOutToken() {}

  /** A ligazón desta reserva deste concello, dada agora. */
  public static String issue(String secret, UUID municipalityId, UUID bookingId, Instant now) {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(bookingId, "bookingId");
    String payload = VERSION + "." + bookingId + "." + now.plus(VALIDITY).getEpochSecond();
    return payload + "." + BASE64URL.encodeToString(hmac(secret, municipalityId, payload));
  }

  /** O que di unha ligazón. */
  public sealed interface Check {}

  /** Válida: a reserva da que é. */
  public record Valid(UUID bookingId) implements Check {}

  /** Asinada por Furar para este concello, pero caducada. */
  public record Expired() implements Check {}

  /** Mal formada, manipulada ou doutro concello. */
  public record Invalid() implements Check {}

  /** Comproba a ligazón para este concello. A firma compárase en tempo constante. */
  public static Check verify(
      String secret, UUID municipalityId, @Nullable String token, Instant now) {
    if (token == null || token.length() > 200) {
      return new Invalid();
    }
    int last = token.lastIndexOf('.');
    if (last < 0) {
      return new Invalid();
    }
    String payload = token.substring(0, last);
    byte[] given;
    try {
      given = Base64.getUrlDecoder().decode(token.substring(last + 1));
    } catch (IllegalArgumentException e) {
      return new Invalid();
    }
    if (!MessageDigest.isEqual(given, hmac(secret, municipalityId, payload))) {
      return new Invalid();
    }
    String[] parts = payload.split("\\.", -1);
    if (parts.length != 3 || !VERSION.equals(parts[0])) {
      return new Invalid();
    }
    Optional<UUID> booking = uuid(parts[1]);
    long expires;
    try {
      expires = Long.parseLong(parts[2]);
    } catch (NumberFormatException e) {
      return new Invalid();
    }
    if (booking.isEmpty()) {
      return new Invalid();
    }
    if (now.getEpochSecond() >= expires) {
      return new Expired();
    }
    return new Valid(booking.get());
  }

  private static Optional<UUID> uuid(String value) {
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static byte[] hmac(String secret, UUID municipalityId, String payload) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return mac.doFinal(
          (PURPOSE + municipalityId + "." + payload).getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(ALGORITHM + " unavailable", e);
    }
  }
}
