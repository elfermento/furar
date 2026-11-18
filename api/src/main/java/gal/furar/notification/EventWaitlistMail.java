// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O aviso a unha persoa da lista de espera dun evento de que hai unha praza libre (ADR-0041): ata
 * {@code exclusiveUntil} só o sabe ela; despois avísase tamén á seguinte, pero pode confirmala
 * mentres siga libre. {@link #toString()} non mostra o destinatario.
 *
 * @param registrationId a inscrición
 * @param notifiedAt cando empezou o aviso: cada aviso á mesma persoa é un correo
 * @param dependentName a persoa a cargo para quen é a praza; nulo se é para quen recibe o correo
 */
public record EventWaitlistMail(
    EventMailFacts event,
    UUID registrationId,
    Instant notifiedAt,
    Instant exclusiveUntil,
    String recipient,
    Language language,
    @Nullable String name,
    @Nullable String dependentName) {

  public EventWaitlistMail {
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(registrationId, "registrationId");
    Objects.requireNonNull(notifiedAt, "notifiedAt");
    Objects.requireNonNull(exclusiveUntil, "exclusiveUntil");
    Objects.requireNonNull(recipient, "recipient");
    Objects.requireNonNull(language, "language");
  }

  @Override
  public String toString() {
    return "EventWaitlistMail[registrationId=" + registrationId + ", language=" + language + "]";
  }
}
