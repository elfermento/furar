// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.domain;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros do módulo. Cada código ten texto en messages/incident_*.properties e en gl.json/es.json.
 */
public enum IncidentError implements ErrorCode {
  NOT_FOUND("incident.not-found", 404),
  /** A instalación non existe no concello ou a veciñanza non a ve. */
  FACILITY_NOT_FOUND("incident.facility-not-found", 422),
  /** A reserva non é da persoa, non é desa instalación ou está cancelada. */
  BOOKING_INVALID("incident.booking-invalid", 422),
  DESCRIPTION_INVALID("incident.description-invalid", 422),
  TOO_MANY_PHOTOS("incident.too-many-photos", 422),
  NOTE_INVALID("incident.note-invalid", 422),
  MESSAGE_INVALID("incident.message-invalid", 422),
  /** Demasiados avisos da mesma conta nun día. */
  TOO_MANY_REPORTS("incident.too-many-reports", 429),
  PHOTO_NOT_FOUND("incident.photo-not-found", 404),
  /**
   * Non se puido ler o ficheiro subido (o tratamento da foto dá os erros {@code facility.photo-*}).
   */
  PHOTO_UNREADABLE("incident.photo-unreadable", 422);

  private final String code;
  private final int status;

  IncidentError(String code, int status) {
    this.code = code;
    this.status = status;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int status() {
    return status;
  }
}
