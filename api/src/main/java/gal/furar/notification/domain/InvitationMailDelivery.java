// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O correo dunha invitación ao persoal e o seu resultado (plan do dominio 4, §2.1). Un por
 * invitación e un só intento: reenviar é outra invitación, con outro token. Sen a ligazón: leva o
 * token.
 *
 * @param error motivo do fallo, curto e sen datos persoais
 * @param sentAt cando se enviou, só se {@link DeliveryStatus#SENT}
 */
public record InvitationMailDelivery(
    UUID invitationId,
    String recipient,
    Language language,
    DeliveryStatus status,
    @Nullable String error,
    @Nullable Instant sentAt) {

  /** O valor de {@code event_id}: unha invitación só ten un correo. */
  public static final String ONCE = "once";

  private static final int ERROR_MAX = 500;

  public InvitationMailDelivery {
    Objects.requireNonNull(invitationId, "invitationId");
    Objects.requireNonNull(recipient, "recipient");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(status, "status");
    if ((status == DeliveryStatus.SENT) != (sentAt != null)) {
      throw new IllegalArgumentException("Only a sent mail has sentAt");
    }
    if (error != null && error.length() > ERROR_MAX) {
      error = error.substring(0, ERROR_MAX);
    }
  }

  public static InvitationMailDelivery sent(
      UUID invitationId, String recipient, Language language, Instant now) {
    return new InvitationMailDelivery(
        invitationId, recipient, language, DeliveryStatus.SENT, null, now);
  }

  public static InvitationMailDelivery failed(
      UUID invitationId, String recipient, Language language, String error) {
    return new InvitationMailDelivery(
        invitationId, recipient, language, DeliveryStatus.FAILED, error, null);
  }

  @Override
  public String toString() {
    return "InvitationMailDelivery[invitationId=" + invitationId + ", status=" + status + "]";
  }
}
