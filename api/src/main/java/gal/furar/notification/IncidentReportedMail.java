// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O correo «Nova incidencia» á xestión do concello. Sen a descrición nin quen avisou: só a
 * instalación, o tipo, cando e a ligazón á lista. {@link #toString()} non mostra os destinatarios.
 * A ligazón á lista da xestión pona {@code notification} co enderezo do concello.
 *
 * @param incidentId a incidencia: a clave do rexistro de envíos
 * @param recipients a quen escribir, cada un coa súa clave (a súa pertenza)
 * @param facilityName instalación
 * @param type {@code BREAKDOWN}, {@code CLEANING}, {@code SAFETY} ou {@code OTHER}
 * @param reportedAt cando se avisou
 */
public record IncidentReportedMail(
    UUID incidentId,
    List<Recipient> recipients,
    String facilityName,
    String type,
    Instant reportedAt) {

  public IncidentReportedMail {
    Objects.requireNonNull(incidentId, "incidentId");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(reportedAt, "reportedAt");
    recipients = List.copyOf(recipients);
  }

  /**
   * Un destinatario.
   *
   * @param key quen é (a súa pertenza ao persoal): un envío por incidencia e clave
   * @param email o seu correo
   */
  public record Recipient(UUID key, String email) {

    public Recipient {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(email, "email");
    }

    @Override
    public String toString() {
      return "Recipient[key=" + key + "]";
    }
  }

  @Override
  public String toString() {
    return "IncidentReportedMail[incidentId="
        + incidentId
        + ", recipients="
        + recipients.size()
        + ", type="
        + type
        + "]";
  }
}
