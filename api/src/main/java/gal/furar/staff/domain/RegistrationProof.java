// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * Proba de que quen abre a ligazón da invitación recibiu o correo invitado (ADR-0027, revisión do
 * 2026-11-02): a web lévaa a Keycloak no parámetro {@code furar_invitation} e a extensión do
 * rexistro de Furar ({@code deploy/keycloak/extension}) dá o correo por verificado se a firma vale,
 * non caducou e o correo do formulario é o mesmo. Sen ela, Keycloak pide verificar o correo coma
 * sempre.
 *
 * <p>Formato ({@code v1}), en ASCII e sen datos en claro fóra do correo, que xa coñece quen ten a
 * ligazón: {@code v1.<id da invitación>.<caduca, segundos Unix>.<correo en base64url>.<HMAC>}. O
 * HMAC-SHA256 vai sobre todo o anterior ao último punto, coa clave {@code
 * FURAR_INVITATION_PROOF_SECRET} (só a teñen a API e Keycloak), en base64url sen recheo. A
 * extensión implementa a mesma verificación; os dous lados proban o mesmo vector fixo ({@code
 * RegistrationProofTest}, {@code InvitationProofTest}).
 *
 * <p>Caduca aos {@link #VALIDITY} ou coa invitación, o que chegue antes: a proba viaxa no URL de
 * Keycloak e queda no historial do navegador, así que vale pouco tempo, o mesmo ca a ligazón de
 * verificación do realm.
 */
public final class RegistrationProof {

  /** Versión do formato: a extensión rexeita calquera outra. */
  public static final String VERSION = "v1";

  /** Canto vale a proba, como a ligazón de verificación do correo de Keycloak (30 minutos). */
  public static final Duration VALIDITY = Duration.ofMinutes(30);

  /** Lonxitude mínima do segredo compartido (en caracteres; xérase con 32 bytes aleatorios). */
  public static final int MIN_SECRET_LENGTH = 32;

  private static final String ALGORITHM = "HmacSHA256";
  private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

  private RegistrationProof() {}

  /**
   * A proba desta invitación para este correo.
   *
   * @param secret o segredo compartido con Keycloak ({@link #MIN_SECRET_LENGTH} caracteres ou máis)
   * @param invitation a invitación
   * @param email o correo invitado, normalizado
   * @param now agora
   * @param invitationExpiresAt cando caduca a invitación: a proba nunca dura máis
   * @return a proba, en ASCII seguro para un URL
   */
  public static String issue(
      String secret,
      StaffInvitationId invitation,
      EmailAddress email,
      Instant now,
      Instant invitationExpiresAt) {
    Objects.requireNonNull(invitation, "invitation");
    Objects.requireNonNull(email, "email");
    Instant expires = now.plus(VALIDITY);
    if (invitationExpiresAt.isBefore(expires)) {
      expires = invitationExpiresAt;
    }
    String payload =
        VERSION
            + "."
            + invitation.value()
            + "."
            + expires.getEpochSecond()
            + "."
            + BASE64URL.encodeToString(email.value().getBytes(StandardCharsets.UTF_8));
    return payload + "." + BASE64URL.encodeToString(hmac(secret, payload));
  }

  /** Se o segredo vale para asinar (non baleiro e longo abondo). */
  public static boolean isUsableSecret(@Nullable String secret) {
    return secret != null && secret.strip().length() >= MIN_SECRET_LENGTH;
  }

  private static byte[] hmac(String secret, String payload) {
    if (!isUsableSecret(secret)) {
      throw new IllegalStateException(
          "FURAR_INVITATION_PROOF_SECRET must have at least " + MIN_SECRET_LENGTH + " characters");
    }
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(ALGORITHM + " unavailable", e);
    }
  }
}
