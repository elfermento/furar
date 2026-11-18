// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un correo dunha partida aberta e o seu resultado (ADR-0040, punto 12): a quen organiza (polo id
 * da súa reserva) ou a unha persoa da partida (pola súa fila). Un por destino, tipo e publicación
 * ({@code eventId}): un reintento non volve escribir a quen xa o recibiu, e a un fallido só unha
 * vez máis ({@link Delivery#MAX_ATTEMPTS}). {@link #toString()} non mostra o destinatario.
 *
 * @param bookingId a reserva de quen organiza, ou nulo
 * @param playerId a fila da persoa, ou nulo
 * @param eventId a solicitude en {@code MATCH_REQUESTED}; a publicación nos demais
 * @param attempts intentos feitos, 1 ou 2
 * @param error motivo do último fallo, curto e sen datos persoais
 * @param sentAt cando se enviou, só se {@link DeliveryStatus#SENT}
 */
public record OpenMatchDelivery(
    MailKind kind,
    @Nullable UUID bookingId,
    @Nullable UUID playerId,
    String eventId,
    String recipient,
    Language language,
    DeliveryStatus status,
    int attempts,
    @Nullable String error,
    @Nullable Instant sentAt) {

  private static final int ERROR_MAX = 500;

  public OpenMatchDelivery {
    Objects.requireNonNull(kind, "kind");
    if (!kind.ofOpenMatch()) {
      throw new IllegalArgumentException("Not the mail of an open match: " + kind);
    }
    if ((bookingId == null) == (playerId == null)) {
      throw new IllegalArgumentException("An open match mail goes to the organizer or a player");
    }
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(recipient, "recipient");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(status, "status");
    if (attempts < 1 || attempts > Delivery.MAX_ATTEMPTS) {
      throw new IllegalArgumentException("Attempts out of range: " + attempts);
    }
    if ((status == DeliveryStatus.SENT) != (sentAt != null)) {
      throw new IllegalArgumentException("Only a sent mail has sentAt");
    }
    if (error != null && error.length() > ERROR_MAX) {
      error = error.substring(0, ERROR_MAX);
    }
  }

  /** O destino: a fila da persoa ou a reserva de quen organiza. */
  public UUID target() {
    return playerId != null ? playerId : Objects.requireNonNull(bookingId, "bookingId");
  }

  /** Xa non hai nada que facer: enviouse ou esgotáronse os intentos. */
  public boolean settled() {
    return status == DeliveryStatus.SENT || attempts >= Delivery.MAX_ATTEMPTS;
  }

  @Override
  public String toString() {
    return "OpenMatchDelivery[kind=" + kind + ", eventId=" + eventId + ", status=" + status + "]";
  }
}
