// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifica a proba da invitación que asina a API de Furar ({@code
 * gal.furar.staff.domain.RegistrationProof}): {@code v1.<id da invitación>.<caduca, segundos
 * Unix>.<correo en base64url>.<HMAC-SHA256 en base64url>}, co HMAC sobre todo o anterior ao último
 * punto e a clave {@code FURAR_INVITATION_PROOF_SECRET}. Calquera outra cousa (sen proba, outra
 * versión, firma mala, caducada, mal formada) dá {@link Optional#empty()}: o rexistro segue coma
 * sempre e Keycloak pide verificar o correo. Sen dependencias de Keycloak, para probala soa.
 */
public final class InvitationProof {

  /** Versión do formato que se acepta. */
  public static final String VERSION = "v1";

  /** Lonxitude mínima do segredo, a mesma ca na API. */
  public static final int MIN_SECRET_LENGTH = 32;

  /** Tope de lonxitude (Keycloak garda ata 2000 caracteres por parámetro adicional). */
  static final int MAX_LENGTH = 1024;

  private static final String ALGORITHM = "HmacSHA256";
  private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9._-]+");

  /**
   * O que proba unha proba válida.
   *
   * @param invitationId a invitación de Furar
   * @param email o correo invitado, normalizado (minúsculas, sen espazos ao redor)
   * @param expiresAt caducidade, en segundos Unix
   */
  public record Claims(String invitationId, String email, long expiresAt) {

    /** Se é o mesmo correo, normalizado igual ca na API. */
    public boolean isFor(String otherEmail) {
      return otherEmail != null && email.equals(normalize(otherEmail));
    }
  }

  private final byte[] secret;

  private InvitationProof(byte[] secret) {
    this.secret = secret;
  }

  /** O verificador, ou baleiro se o segredo falta ou é curto (a extensión queda sen efecto). */
  public static Optional<InvitationProof> withSecret(String secret) {
    if (secret == null || secret.strip().length() < MIN_SECRET_LENGTH) {
      return Optional.empty();
    }
    return Optional.of(new InvitationProof(secret.strip().getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * As afirmacións da proba se a firma vale e non caducou.
   *
   * @param proof a proba tal como chegou (pode ser {@code null})
   * @param nowEpochSeconds agora, en segundos Unix (o reloxo de Keycloak)
   */
  public Optional<Claims> verify(String proof, long nowEpochSeconds) {
    if (proof == null || proof.length() > MAX_LENGTH || !SHAPE.matcher(proof).matches()) {
      return Optional.empty();
    }
    String[] parts = proof.split("\\.", -1);
    if (parts.length != 5 || !VERSION.equals(parts[0])) {
      return Optional.empty();
    }
    String payload = proof.substring(0, proof.lastIndexOf('.'));
    byte[] given;
    try {
      given = Base64.getUrlDecoder().decode(parts[4]);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    // Comparación en tempo constante: non hai oráculo de tempo sobre a firma.
    if (!MessageDigest.isEqual(hmac(payload), given)) {
      return Optional.empty();
    }
    try {
      String invitationId = UUID.fromString(parts[1]).toString();
      long expiresAt = Long.parseLong(parts[2]);
      String email = new String(Base64.getUrlDecoder().decode(parts[3]), StandardCharsets.UTF_8);
      if (expiresAt <= nowEpochSeconds || email.isBlank() || !email.equals(normalize(email))) {
        return Optional.empty();
      }
      return Optional.of(new Claims(invitationId, email, expiresAt));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private byte[] hmac(String payload) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(ALGORITHM + " unavailable", e);
    }
  }

  /** Igual ca a API ({@code EmailAddress}): sen espazos ao redor e en minúsculas. */
  static String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return "InvitationProof[secret=***]";
  }
}
