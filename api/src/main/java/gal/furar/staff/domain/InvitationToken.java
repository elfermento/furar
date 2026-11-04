// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Token dunha invitación (plan do dominio 4, §1.2.6): 32 bytes de {@link SecureRandom} en base64url
 * sen recheo (43 caracteres), dun só uso, que caduca aos 7 días. Só vive en memoria: vai no correo,
 * dentro do fragmento da ligazón, e volve no corpo dun {@code POST}. Na base só se garda o seu
 * {@link TokenHash}, e {@link #toString()} non o mostra, para que non chegue a ningún rexistro.
 */
public final class InvitationToken {

  /** Bytes aleatorios do token. */
  public static final int BYTES = 32;

  /** Validez dende que se emite (tamén ao reenviar, que emite outro). */
  public static final Duration VALIDITY = Duration.ofDays(7);

  private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  private final String value;

  private InvitationToken(String value) {
    this.value = value;
  }

  /** Un token novo. */
  public static InvitationToken generate(SecureRandom random) {
    Objects.requireNonNull(random, "random");
    byte[] bytes = new byte[BYTES];
    random.nextBytes(bytes);
    return new InvitationToken(ENCODER.encodeToString(bytes));
  }

  /**
   * O token que trae unha petición, se ten a forma dun token. Un texto que non a ten non é un erro
   * distinto: o caso de uso responde igual ca a un token descoñecido.
   */
  public static Optional<InvitationToken> parse(@Nullable String raw) {
    if (raw == null || !FORMAT.matcher(raw).matches()) {
      return Optional.empty();
    }
    // 43 caracteres base64url son 258 bits: os dous últimos teñen que ser cero (forma canónica),
    // para que un só token teña un só texto.
    byte[] decoded = Base64.getUrlDecoder().decode(raw);
    if (!ENCODER.encodeToString(decoded).equals(raw)) {
      return Optional.empty();
    }
    return Optional.of(new InvitationToken(raw));
  }

  /** O texto do token, só para a ligazón do correo. */
  public String value() {
    return value;
  }

  /** SHA-256 do texto do token: o que se garda e polo que se busca. */
  public TokenHash hash() {
    try {
      return TokenHash.of(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JVM", e);
    }
  }

  /** Cando caduca un token emitido en {@code issuedAt}. */
  public static Instant expiresAt(Instant issuedAt) {
    return issuedAt.plus(VALIDITY);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof InvitationToken that
        && MessageDigest.isEqual(
            value.getBytes(StandardCharsets.US_ASCII),
            that.value.getBytes(StandardCharsets.US_ASCII));
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "InvitationToken[redacted]";
  }
}
