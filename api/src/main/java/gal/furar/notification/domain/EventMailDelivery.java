// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un correo dun evento do concello e o seu resultado (ADR-0038). Un por evento, tipo e clave
 * ({@code key}): a inscrición, ou o cambio e a inscrición nun aviso de cambio. Un só intento.
 *
 * @param error motivo do fallo, curto e sen datos persoais
 * @param sentAt cando se enviou, só se {@link DeliveryStatus#SENT}
 */
public record EventMailDelivery(
    UUID eventId,
    Kind kind,
    String key,
    String recipient,
    Language language,
    DeliveryStatus status,
    @Nullable String error,
    @Nullable Instant sentAt) {

  private static final int ERROR_MAX = 500;

  /** Que correo, co valor da columna {@code kind}. */
  public enum Kind {
    EVENT_ACCEPTED,
    EVENT_REJECTED,
    EVENT_CHANGED,
    EVENT_CANCELLED,
    /** Hai unha praza libre para quen espera (ADR-0041). */
    EVENT_WAITLIST_NOTIFIED,
    /** Cancelouse unha sesión dunha actividade (ADR-0041). */
    EVENT_SESSION_CANCELLED,
    /** A actividade rematou antes (ADR-0041). */
    EVENT_ENDED_EARLY
  }

  public EventMailDelivery {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(key, "key");
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

  @Override
  public String toString() {
    return "EventMailDelivery[eventId=" + eventId + ", kind=" + kind + ", status=" + status + "]";
  }
}
