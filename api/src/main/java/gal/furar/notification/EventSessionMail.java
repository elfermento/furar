// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O aviso a cada persoa con praza nunha actividade con horario (ADR-0041): cancelouse unha sesión
 * ou a actividade rematou antes. {@link #toString()} non mostra os destinatarios.
 *
 * @param change a sesión cancelada ou o cambio (cada un avisa unha vez a cada persoa)
 * @param date o día da sesión cancelada, ou desde cando xa non hai sesións
 * @param startsAt o inicio da sesión cancelada (ao rematar antes, o inicio dese día)
 */
public record EventSessionMail(
    EventMailFacts event,
    Kind kind,
    UUID change,
    LocalDate date,
    Instant startsAt,
    List<EventNoticeMail.Recipient> recipients) {

  /** Que pasou. */
  public enum Kind {
    SESSION_CANCELLED,
    ENDED_EARLY
  }

  public EventSessionMail {
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(change, "change");
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(startsAt, "startsAt");
    recipients = List.copyOf(recipients);
  }

  @Override
  public String toString() {
    return "EventSessionMail[eventId="
        + event.eventId()
        + ", kind="
        + kind
        + ", recipients="
        + recipients.size()
        + "]";
  }
}
