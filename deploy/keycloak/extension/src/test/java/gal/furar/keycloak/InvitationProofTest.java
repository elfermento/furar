// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * A proba da invitación (ADR-0027, revisión do 2026-11-02). O vector fixo é o que asina a API
 * ({@code RegistrationProofTest}): se un dos lados cambia o formato, falla.
 */
class InvitationProofTest {

  /** Segredo sintético das probas, o mesmo ca na API. */
  static final String SIGNER = "0123456789abcdef".repeat(3);

  /** Asinado pola API para xestora@naron.example, caduca o 2026-11-02 ás 08:00 UTC. */
  static final String VECTOR =
      "v1.019a0b6e-0000-7000-8000-00000000c0de.1793606400.eGVzdG9yYUBuYXJvbi5leGFtcGxl"
          + ".-N_sUTbCOun2Lktb_tle-d7r0VPyDZDwTkCSLi0yFI8";

  static final long EXPIRES = 1_793_606_400L;
  static final long BEFORE = EXPIRES - 60;

  private final InvitationProof proofs = InvitationProof.withSecret(SIGNER).orElseThrow();

  @Test
  void aValidProofGivesTheInvitationAndTheEmail() {
    InvitationProof.Claims claims = proofs.verify(VECTOR, BEFORE).orElseThrow();
    assertEquals("019a0b6e-0000-7000-8000-00000000c0de", claims.invitationId());
    assertEquals("xestora@naron.example", claims.email());
    assertEquals(EXPIRES, claims.expiresAt());
  }

  @Test
  void theSameEmailMatchesAfterNormalizingAndAnotherDoesNot() {
    InvitationProof.Claims claims = proofs.verify(VECTOR, BEFORE).orElseThrow();
    assertTrue(claims.isFor("xestora@naron.example"));
    assertTrue(claims.isFor(" Xestora@Naron.Example "));
    assertFalse(claims.isFor("outra@naron.example"));
    assertFalse(claims.isFor(null));
  }

  @Test
  void anotherSecretIsRejected() {
    InvitationProof other = InvitationProof.withSecret("f".repeat(48)).orElseThrow();
    assertEquals(Optional.empty(), other.verify(VECTOR, BEFORE));
  }

  @Test
  void aTamperedProofIsRejected() {
    // Outro correo coa firma vella.
    String email = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("atacante@example.org".getBytes(StandardCharsets.UTF_8));
    String[] parts = VECTOR.split("\\.");
    String otherEmail = String.join(".", parts[0], parts[1], parts[2], email, parts[4]);
    assertEquals(Optional.empty(), proofs.verify(otherEmail, BEFORE));
    // Máis tempo coa firma vella.
    String later = String.join(".", parts[0], parts[1], "1893456000", parts[3], parts[4]);
    assertEquals(Optional.empty(), proofs.verify(later, BEFORE));
    // Firma cortada, outra versión ou anacos de máis.
    assertEquals(Optional.empty(), proofs.verify(VECTOR.substring(0, VECTOR.length() - 2), BEFORE));
    assertEquals(Optional.empty(), proofs.verify("v2" + VECTOR.substring(2), BEFORE));
    assertEquals(Optional.empty(), proofs.verify(VECTOR + ".x", BEFORE));
  }

  @Test
  void anExpiredProofIsRejected() {
    assertTrue(proofs.verify(VECTOR, EXPIRES - 1).isPresent());
    assertEquals(Optional.empty(), proofs.verify(VECTOR, EXPIRES));
    assertEquals(Optional.empty(), proofs.verify(VECTOR, EXPIRES + 3600));
  }

  @Test
  void noProofOrGarbageIsRejected() {
    assertEquals(Optional.empty(), proofs.verify(null, BEFORE));
    assertEquals(Optional.empty(), proofs.verify("", BEFORE));
    assertEquals(Optional.empty(), proofs.verify("non é unha proba", BEFORE));
    assertEquals(Optional.empty(), proofs.verify("v1.a.b.c.d", BEFORE));
    assertEquals(Optional.empty(), proofs.verify("v1." + "a".repeat(2000), BEFORE));
  }

  @Test
  void aProofSignedWithANonNormalizedEmailIsRejected() {
    String payload =
        "v1.019a0b6e-0000-7000-8000-00000000c0de."
            + EXPIRES
            + "."
            + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Xestora@Naron.example".getBytes(StandardCharsets.UTF_8));
    assertEquals(Optional.empty(), proofs.verify(payload + "." + sign(payload), BEFORE));
  }

  @Test
  void withoutASecretOrWithAShortOneThereIsNoVerifier() {
    assertTrue(InvitationProof.withSecret(null).isEmpty());
    assertTrue(InvitationProof.withSecret("").isEmpty());
    assertTrue(InvitationProof.withSecret("x".repeat(31)).isEmpty());
    assertTrue(InvitationProof.withSecret("x".repeat(32)).isPresent());
  }

  private static String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SIGNER.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
