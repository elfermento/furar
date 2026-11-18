// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un correo a unha espera e o seu resultado: «Quedou libre» (ADR-0033, punto 7) ou «Lista de espera
 * cancelada» (revisión do 2026-11-11). Un por espera e evento ({@code releaseId}): un reintento do
 * mesmo evento non volve escribir a quen xa recibiu, e a un fallido só unha vez máis ({@link
 * Delivery#MAX_ATTEMPTS}).
 *
 * @param kind {@link MailKind#SLOT_RELEASED} ou {@link MailKind#WAIT_CANCELLED}
 * @param releaseId a liberación ou a cancelación que se avisa (o {@code event_id} da fila)
 * @param attempts intentos feitos, 1 ou 2
 * @param error motivo do último fallo, curto e sen datos persoais
 * @param sentAt cando se enviou, só se {@link DeliveryStatus#SENT}
 */
public record WaitDelivery(
    MailKind kind,
    UUID waitId,
    UUID releaseId,
    String recipient,
    Language language,
    DeliveryStatus status,
    int attempts,
    @Nullable String error,
    @Nullable Instant sentAt) {

  private static final int ERROR_MAX = 500;

  public WaitDelivery {
    Objects.requireNonNull(kind, "kind");
    if (!kind.ofWait()) {
      throw new IllegalArgumentException("Not the mail of a wait: " + kind);
    }
    Objects.requireNonNull(waitId, "waitId");
    Objects.requireNonNull(releaseId, "releaseId");
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

  /** Xa non hai nada que facer: enviouse ou esgotáronse os intentos. */
  public boolean settled() {
    return status == DeliveryStatus.SENT || attempts >= Delivery.MAX_ATTEMPTS;
  }
}
