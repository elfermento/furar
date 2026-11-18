// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * Clave coa que Keycloak chama á API polo porto interno de Actuator. Sae do segredo que xa
 * comparten as dúas ({@code FURAR_INVITATION_PROOF_SECRET}, ADR-0027) cun HMAC por propósito:
 * {@code base64url(HMAC-SHA256(segredo, propósito))}, sen recheo. Cada propósito ten a súa clave:
 * quen coñeza a do segundo factor ({@value #SECOND_FACTOR}, ADR-0029) só pode preguntar si ou non,
 * e non pecha contas ({@value #ACCOUNT_CLOSURE}, ADR-0043). Ningunha asina invitacións. A extensión
 * de Keycloak ({@code deploy/keycloak/extension}, {@code SecondFactorPolicy} e {@code
 * AccountClosureApi}) calcúlaas igual.
 */
public final class InternalCallKey {

  /** O segundo factor do persoal (ADR-0029). */
  public static final String SECOND_FACTOR = "furar-second-factor-v1";

  /** Eliminar a propia conta: se se pode e que xa se eliminou (ADR-0043). */
  public static final String ACCOUNT_CLOSURE = "furar-account-closure-v1";

  /** Caracteres do segredo, como moito curto (o mesmo mínimo ca a proba da invitación). */
  public static final int MIN_SECRET_LENGTH = 32;

  private static final String ALGORITHM = "HmacSHA256";

  private final byte[] key;

  private InternalCallKey(byte[] key) {
    this.key = key;
  }

  /**
   * @param secret o segredo compartido ({@value #MIN_SECRET_LENGTH} caracteres ou máis)
   * @param purpose o propósito ({@link #SECOND_FACTOR}, {@link #ACCOUNT_CLOSURE})
   */
  public static InternalCallKey derive(String secret, String purpose) {
    if (secret == null || secret.strip().length() < MIN_SECRET_LENGTH) {
      throw new IllegalStateException(
          "the shared secret must have at least " + MIN_SECRET_LENGTH + " characters (ADR-0029)");
    }
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.strip().getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return new InternalCallKey(mac.doFinal(purpose.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(ALGORITHM + " unavailable", e);
    }
  }

  /** O valor que manda Keycloak. */
  public String value() {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
  }

  /** Se a clave recibida é esta, en tempo constante. */
  public boolean matches(@Nullable String given) {
    if (given == null) {
      return false;
    }
    return MessageDigest.isEqual(
        value().getBytes(StandardCharsets.US_ASCII), given.getBytes(StandardCharsets.US_ASCII));
  }

  @Override
  public String toString() {
    return "InternalCallKey[***]";
  }
}
