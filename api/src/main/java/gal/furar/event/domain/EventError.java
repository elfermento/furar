// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.ErrorCode;

/** Erros do módulo. Cada código ten texto en messages/event_*.properties e en gl.json/es.json. */
public enum EventError implements ErrorCode {
  NOT_FOUND("event.not-found", 404),
  NAME_INVALID("event.name-invalid", 422),
  DESCRIPTION_INVALID("event.description-invalid", 422),
  PLACE_INVALID("event.place-invalid", 422),
  FACILITY_NOT_FOUND("event.facility-not-found", 422),
  CAPACITY_INVALID("event.capacity-invalid", 422),
  PRICE_INVALID("event.price-invalid", 422),
  DEADLINE_INVALID("event.deadline-invalid", 422),
  /** A data e a hora teñen que ser futuras. */
  STARTS_IN_PAST("event.starts-in-past", 422),
  /** Menos prazas cás persoas xa apuntadas. */
  CAPACITY_BELOW_REGISTERED("event.capacity-below-registered", 409),
  /** Un evento cancelado non se cambia nin admite inscricións. */
  CANCELLED("event.cancelled", 409),
  /** Xa empezou: non se cambia nin se cancela. */
  STARTED("event.started", 409),
  /** Con persoas apuntadas non se elimina: cancélase (avisa a todas). */
  HAS_REGISTRATIONS("event.has-registrations", 409),
  /** Pasou «Apuntarse ata» ou o evento xa empezou. */
  CLOSED("event.closed", 409),
  FULL("event.full", 409),
  ALREADY_REGISTERED("event.already-registered", 409),
  NOT_REGISTERED("event.not-registered", 404),
  REGISTRATION_NOT_FOUND("event.registration-not-found", 404),
  /** Aceptar ou rexeitar só unha solicitude pendente. */
  NOT_PENDING("event.not-pending", 409),
  /** O pago só nun evento con prezo e nunha inscrición confirmada. */
  NO_PAYMENT("event.no-payment", 409),
  // --- Actividades con horario (ADR-0041) ---
  /** O tipo non casa co horario, ou o uso co lugar. */
  SCHEDULE_INVALID("event.schedule-invalid", 422),
  SCHEDULE_WEEKDAYS_REQUIRED("event.schedule-weekdays-required", 422),
  /** Horas fóra de 15 en 15 minutos ou fin que non é despois do inicio. */
  SCHEDULE_TIME_INVALID("event.schedule-time-invalid", 422),
  /** Datas antes de hoxe, ao revés ou de máis dun ano. */
  SCHEDULE_RANGE_INVALID("event.schedule-range-invalid", 422),
  /** O tipo dun evento non cambia. */
  KIND_NOT_EDITABLE("event.kind-not-editable", 409),
  /** O horario, as datas e o lugar dunha actividade non cambian: rematase e créase outra. */
  SCHEDULE_NOT_EDITABLE("event.schedule-not-editable", 409),
  /** Ningún día queda libre: non se crea. */
  NO_SESSIONS("event.no-sessions", 422),
  /** Mentres o xestor miraba a vista previa, cambiou algún día. */
  SESSIONS_CHANGED("event.sessions-changed", 409),
  /** A actividade xa rematou. */
  ENDED("event.ended", 409),
  /** Rematar antes ou as sesións, só nunha actividade con horario. */
  NOT_SCHEDULED("event.not-scheduled", 409),
  /** «Rematar desde» fóra de hoxe … última data. */
  END_INVALID("event.end-invalid", 422),
  SESSION_NOT_FOUND("event.session-not-found", 404),
  SESSION_CANCELLED("event.session-cancelled", 409),
  /** Unha sesión xa empezada non se cancela. */
  SESSION_STARTED("event.session-started", 409),
  /** Unha sesión con lista pasada non se cancela. */
  SESSION_HAS_ATTENDANCE("event.session-has-attendance", 409),
  /** Aínda falta para a sesión: a lista pásase desde a marxe antes do inicio. */
  ATTENDANCE_TOO_EARLY("event.attendance-too-early", 422),
  /** Unha marca para alguén que non ten praza nesta actividade. */
  ATTENDANCE_INVALID("event.attendance-invalid", 422),
  BIRTH_YEARS_INVALID("event.birth-years-invalid", 422),
  /** O evento ten límite de idade: hai que dar a data de nacemento. */
  BIRTH_YEAR_REQUIRED("event.birth-year-required", 422),
  /** Unha data de nacemento futura ou anterior a 1900. */
  BIRTH_DATE_INVALID("event.birth-date-invalid", 422),
  AGE_NOT_ALLOWED("event.age-not-allowed", 422),
  /** A persoa a cargo non é desta conta ou está desactivada. */
  DEPENDENT_NOT_FOUND("event.dependent-not-found", 404),
  /** Xa ten as esperas que caben: {@code max}. */
  WAITLIST_LIMIT("event.waitlist-limit", 422),
  /** Confirmar unha praza sen ter aviso dela. */
  NOT_NOTIFIED("event.not-notified", 409);

  private final String code;
  private final int status;

  EventError(String code, int status) {
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
