// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un correo dunha tempada á entidade e o seu resultado (ADR-0039, punto 12): pola serie ({@code
 * SEASON_CREATED}, {@code SEASON_ENDED}) ou pola solicitude ({@code SEASON_REQUEST_REJECTED}). Un
 * por destino, tipo e publicación ({@code eventId}); a un fallido, só unha vez máis ({@link
 * Delivery#MAX_ATTEMPTS}). {@link #toString()} non mostra o destinatario.
 *
 * @param target a serie ou a solicitude
 * @param error motivo do último fallo, curto e sen datos persoais
 * @param sentAt cando se enviou, só se {@link DeliveryStatus#SENT}
 */
public record SeriesDelivery(
    MailKind kind,
    UUID target,
    String eventId,
    String recipient,
    Language language,
    DeliveryStatus status,
    int attempts,
    @Nullable String error,
    @Nullable Instant sentAt) {

  private static final int ERROR_MAX = 500;

  public SeriesDelivery {
    Objects.requireNonNull(kind, "kind");
    if (!kind.ofSeason()) {
      throw new IllegalArgumentException("Not the mail of a season: " + kind);
    }
    Objects.requireNonNull(target, "target");
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

  /** Se o destino é a solicitude (e non a serie). */
  public boolean ofRequest() {
    return kind == MailKind.SEASON_REQUEST_REJECTED;
  }

  /** Xa non hai nada que facer: enviouse ou esgotáronse os intentos. */
  public boolean settled() {
    return status == DeliveryStatus.SENT || attempts >= Delivery.MAX_ATTEMPTS;
  }

  @Override
  public String toString() {
    return "SeriesDelivery[kind=" + kind + ", eventId=" + eventId + ", status=" + status + "]";
  }
}
