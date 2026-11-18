// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O correo «Resolvemos o teu aviso» á persoa que avisou dunha incidencia. {@link #toString()} non
 * mostra o destinatario nin a mensaxe.
 *
 * @param incidentId a incidencia: a clave do rexistro de envíos (un só correo por incidencia)
 * @param recipient correo da persoa que avisou
 * @param language idioma do correo, o da persoa ao avisar
 * @param name nome da persoa, se o deu o token
 * @param facilityName instalación
 * @param type {@code BREAKDOWN}, {@code CLEANING}, {@code SAFETY} ou {@code OTHER}
 * @param reportedAt cando avisou
 * @param message a mensaxe do concello, se escribiu unha
 */
public record IncidentResolvedMail(
    UUID incidentId,
    String recipient,
    Language language,
    @Nullable String name,
    String facilityName,
    String type,
    Instant reportedAt,
    @Nullable String message) {

  public IncidentResolvedMail {
    Objects.requireNonNull(incidentId, "incidentId");
    Objects.requireNonNull(recipient, "recipient");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(reportedAt, "reportedAt");
  }

  @Override
  public String toString() {
    return "IncidentResolvedMail[incidentId=" + incidentId + ", language=" + language + "]";
  }
}
