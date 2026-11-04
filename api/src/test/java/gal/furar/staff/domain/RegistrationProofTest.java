// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A proba da invitación (ADR-0027, revisión do 2026-11-02). O vector fixo é o mesmo que verifica a
 * extensión de Keycloak ({@code InvitationProofTest}): se un dos lados cambia o formato, falla.
 */
class RegistrationProofTest {

  /** Segredo sintético das probas (48 caracteres), o mesmo na extensión. */
  static final String SIGNER = "0123456789abcdef".repeat(3);

  static final StaffInvitationId INVITATION =
      new StaffInvitationId(UUID.fromString("019a0b6e-0000-7000-8000-00000000c0de"));
  static final EmailAddress EMAIL = EmailAddress.of("Xestora@Naron.example ");
  static final Instant NOW = Instant.parse("2026-11-02T07:30:00Z");

  /** Vector compartido coa extensión de Keycloak. */
  static final String VECTOR =
      "v1.019a0b6e-0000-7000-8000-00000000c0de.1793606400.eGVzdG9yYUBuYXJvbi5leGFtcGxl"
          + ".-N_sUTbCOun2Lktb_tle-d7r0VPyDZDwTkCSLi0yFI8";

  @Test
  void signsTheNormalizedEmailAndTheInvitationAndLastsThirtyMinutes() {
    String proof =
        RegistrationProof.issue(SIGNER, INVITATION, EMAIL, NOW, NOW.plusSeconds(7 * 86_400));
    assertThat(proof).isEqualTo(VECTOR);
    assertThat(proof).matches("[A-Za-z0-9._-]+");
  }

  @Test
  void neverOutlivesTheInvitation() {
    Instant expires = NOW.plusSeconds(600);
    String proof = RegistrationProof.issue(SIGNER, INVITATION, EMAIL, NOW, expires);
    assertThat(proof.split("\\.")[2]).isEqualTo(Long.toString(expires.getEpochSecond()));
  }

  @Test
  void anotherSecretGivesAnotherSignature() {
    String other =
        RegistrationProof.issue(
            "f".repeat(48), INVITATION, EMAIL, NOW, NOW.plusSeconds(7 * 86_400));
    assertThat(other).isNotEqualTo(VECTOR);
    assertThat(other.substring(0, other.lastIndexOf('.')))
        .isEqualTo(VECTOR.substring(0, VECTOR.lastIndexOf('.')));
  }

  @Test
  void refusesAShortSecret() {
    assertThat(RegistrationProof.isUsableSecret("x".repeat(31))).isFalse();
    assertThat(RegistrationProof.isUsableSecret(null)).isFalse();
    assertThat(RegistrationProof.isUsableSecret(SIGNER)).isTrue();
    assertThatThrownBy(() -> RegistrationProof.issue("x".repeat(31), INVITATION, EMAIL, NOW, NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FURAR_INVITATION_PROOF_SECRET");
  }
}
