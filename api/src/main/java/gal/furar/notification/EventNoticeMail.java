// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import gal.furar.shared.domain.Language;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O aviso a cada persoa apuntada ou pedindo praza nun evento: cambiou de data, hora ou lugar, ou
 * cancelouse. {@link #toString()} non mostra os destinatarios.
 *
 * @param change o cambio (só en {@link Kind#MOVED}): cada cambio avisa unha vez a cada persoa
 */
public record EventNoticeMail(
    EventMailFacts event, Kind kind, @Nullable UUID change, List<Recipient> recipients) {

  /** Que pasou. */
  public enum Kind {
    MOVED,
    CANCELLED
  }

  public EventNoticeMail {
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(kind, "kind");
    if ((kind == Kind.MOVED) != (change != null)) {
      throw new IllegalArgumentException("Only a move has a change id");
    }
    recipients = List.copyOf(recipients);
  }

  /**
   * Unha persoa.
   *
   * @param registrationId a súa inscrición: a clave do rexistro de envíos
   */
  public record Recipient(
      UUID registrationId, String email, Language language, @Nullable String name) {

    public Recipient {
      Objects.requireNonNull(registrationId, "registrationId");
      Objects.requireNonNull(email, "email");
      Objects.requireNonNull(language, "language");
    }

    @Override
    public String toString() {
      return "Recipient[registrationId=" + registrationId + "]";
    }
  }

  @Override
  public String toString() {
    return "EventNoticeMail[eventId="
        + event.eventId()
        + ", kind="
        + kind
        + ", recipients="
        + recipients.size()
        + "]";
  }
}
