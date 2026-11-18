// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import gal.furar.shared.domain.Language;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O correo á persoa que pediu praza nun evento «Con aprobación»: aceptada ou rexeitada. {@link
 * #toString()} non mostra o destinatario.
 *
 * @param registrationId a solicitude: a clave do rexistro de envíos (un correo por solicitude)
 * @param accepted aceptada (ten praza) ou rexeitada
 */
public record EventDecisionMail(
    EventMailFacts event,
    UUID registrationId,
    boolean accepted,
    String recipient,
    Language language,
    @Nullable String name) {

  public EventDecisionMail {
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(registrationId, "registrationId");
    Objects.requireNonNull(recipient, "recipient");
    Objects.requireNonNull(language, "language");
  }

  @Override
  public String toString() {
    return "EventDecisionMail[registrationId="
        + registrationId
        + ", accepted="
        + accepted
        + ", language="
        + language
        + "]";
  }
}
