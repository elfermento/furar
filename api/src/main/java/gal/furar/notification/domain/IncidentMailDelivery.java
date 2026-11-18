// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un correo dunha incidencia e o seu resultado (ADR-0036). Un por incidencia, tipo e destinatario
 * ({@code key}): a pertenza de quen xestiona en «Nova incidencia» e {@link #ONCE} en «Resolvemos o
 * teu aviso». Un só intento.
 *
 * @param error motivo do fallo, curto e sen datos persoais
 * @param sentAt cando se enviou, só se {@link DeliveryStatus#SENT}
 */
public record IncidentMailDelivery(
    UUID incidentId,
    Kind kind,
    String key,
    String recipient,
    Language language,
    DeliveryStatus status,
    @Nullable String error,
    @Nullable Instant sentAt) {

  /** A clave do correo á persoa que avisou: só hai un. */
  public static final String ONCE = "once";

  private static final int ERROR_MAX = 500;

  /** Que correo, co valor da columna {@code kind}. */
  public enum Kind {
    INCIDENT_REPORTED,
    INCIDENT_RESOLVED
  }

  public IncidentMailDelivery {
    Objects.requireNonNull(incidentId, "incidentId");
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
    return "IncidentMailDelivery[incidentId="
        + incidentId
        + ", kind="
        + kind
        + ", status="
        + status
        + "]";
  }
}
