// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.staff.domain.EmailAddress;
import gal.furar.staff.domain.RegistrationProof;
import gal.furar.staff.domain.StaffInvitationId;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Asina as probas da invitación co segredo compartido con Keycloak ({@code
 * FURAR_INVITATION_PROOF_SECRET}, {@link RegistrationProof}). Sen un segredo de polo menos {@value
 * RegistrationProof#MIN_SECRET_LENGTH} caracteres, a API non arrinca; en {@code prod}, ademais,
 * {@code ProdProfileGuard} rexeita o de demo. O segredo nunca sae nin se rexistra.
 */
@Component
public class RegistrationProofs {

  private final String secret;

  public RegistrationProofs(@Value("${furar.invitation.proof-secret}") String secret) {
    if (!RegistrationProof.isUsableSecret(secret)) {
      throw new IllegalStateException(
          "furar.invitation.proof-secret (FURAR_INVITATION_PROOF_SECRET) must have at least "
              + RegistrationProof.MIN_SECRET_LENGTH
              + " characters");
    }
    this.secret = secret.strip();
  }

  /** A proba desta invitación e deste correo, que caduca antes ca a invitación. */
  public String issue(
      StaffInvitationId invitation, EmailAddress email, Instant now, Instant invitationExpiresAt) {
    return RegistrationProof.issue(secret, invitation, email, now, invitationExpiresAt);
  }

  @Override
  public String toString() {
    return "RegistrationProofs[secret=***]";
  }
}
