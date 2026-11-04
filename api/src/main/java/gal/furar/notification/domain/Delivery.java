// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un correo dunha reserva e o seu resultado (bloque 2 da especificación: «rexístrase a quen se
 * avisou, como e cando»). Un por reserva, tipo e evento: o reintento do rexistro de publicacións
 * non envía dúas veces. Un envío fallido reinténtase unha vez ({@link #MAX_ATTEMPTS}).
 *
 * @param eventId distingue dous correos do mesmo tipo (cada cambio leva a súa versión)
 * @param attempts intentos feitos, 1 ou 2
 * @param error motivo do último fallo, curto e sen datos persoais
 * @param sentAt cando se enviou, só se {@link DeliveryStatus#SENT}
 */
public record Delivery(
    UUID bookingId,
    MailKind kind,
    String eventId,
    String recipient,
    Language language,
    DeliveryStatus status,
    int attempts,
    @Nullable String error,
    @Nullable Instant sentAt) {

  /** O primeiro intento e un reintento. */
  public static final int MAX_ATTEMPTS = 2;

  private static final int ERROR_MAX = 500;

  public Delivery {
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(recipient, "recipient");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(status, "status");
    if (attempts < 1 || attempts > MAX_ATTEMPTS) {
      throw new IllegalArgumentException("Attempts out of range: " + attempts);
    }
    if ((status == DeliveryStatus.SENT) != (sentAt != null)) {
      throw new IllegalArgumentException("Only a sent mail has sentAt");
    }
    if (error != null && error.length() > ERROR_MAX) {
      error = error.substring(0, ERROR_MAX);
    }
  }

  /** O intento número {@code attempt} saíu ben. */
  public static Delivery sent(MailRequest request, int attempt, Instant now) {
    return new Delivery(
        request.bookingId(),
        request.kind(),
        request.eventId(),
        request.recipient(),
        request.language(),
        DeliveryStatus.SENT,
        attempt,
        null,
        now);
  }

  /** O intento número {@code attempt} fallou. */
  public static Delivery failed(MailRequest request, int attempt, String error) {
    return new Delivery(
        request.bookingId(),
        request.kind(),
        request.eventId(),
        request.recipient(),
        request.language(),
        DeliveryStatus.FAILED,
        attempt,
        error,
        null);
  }

  /** Xa non hai nada que facer: enviouse ou esgotáronse os intentos. */
  public boolean settled() {
    return status == DeliveryStatus.SENT || attempts >= MAX_ATTEMPTS;
  }
}
