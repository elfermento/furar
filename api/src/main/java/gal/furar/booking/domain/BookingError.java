// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros das reservas. Cada código ten texto en {@code messages/booking_*.properties} e en {@code
 * gl.json}/{@code es.json}. O correo sen verificar é {@code auth.email-not-verified}, de {@code
 * AuthError}: dáo o interceptor de {@code PersonUseCase}.
 */
public enum BookingError implements ErrorCode {
  NOT_FOUND("booking.not-found", 404),
  FACILITY_NOT_BOOKABLE("booking.facility-not-bookable", 422),
  USE_NOT_AVAILABLE("booking.use-not-available", 422),
  /** Fóra de horario, sen aliñar ou cortado por un bloqueo. */
  SLOT_INVALID("booking.slot-invalid", 422),
  SLOT_TAKEN("booking.slot-taken", 409),
  PART_TAKEN("booking.part-taken", 409),
  /** Unha persoa = unha praza no mesmo uso e franxa. */
  SEAT_TAKEN_BY_YOU("booking.seat-taken-by-you", 409),
  /** A franxa xa empezou ou non chega á antelación mínima. */
  TOO_SOON("booking.too-soon", 422),
  TOO_FAR("booking.too-far", 422),
  TOO_LONG("booking.too-long", 422),
  CAP_REACHED("booking.cap-reached", 422),
  CANCEL_DEADLINE_PASSED("booking.cancel-deadline-passed", 422),
  /** Xa está cancelada: non se cancela nin se cambia outra vez. */
  NOT_CANCELLABLE("booking.not-cancellable", 409),
  CHANGE_OTHER_FACILITY("booking.change-other-facility", 422),
  CHANGE_PRICE_PAID("booking.change-price-paid", 422),
  PAYMENT_TRANSITION_INVALID("booking.payment-transition-invalid", 422),
  /**
   * Unha conta de traballo (persoal activo dalgún concello) non reserva como veciña: o traballo e a
   * vida privada van en contas distintas (ADR-0027, revisión do 2026-10-30).
   */
  STAFF_ACCOUNT("booking.staff-account", 403),
  /**
   * Un cambio na instalación afecta a reservas e quen o fai non pode decidir sobre elas (a
   * plataforma: configura instalacións, pero non ve reservas; ADR-0028, revisión do 2026-11-03).
   */
  AFFECTED_NEEDS_MANAGER("booking.affected-needs-manager", 403),
  /** Reservar pide a cabeceira {@code Idempotency-Key} (ADR-0025, punto 19). */
  IDEMPOTENCY_KEY_MISSING("booking.idempotency-key-missing", 400),
  /** A cabeceira {@code Idempotency-Key} non ten a forma de {@link IdempotencyKey}. */
  IDEMPOTENCY_KEY_INVALID("booking.idempotency-key-invalid", 400),
  /** A mesma persoa xa usou esta clave para reservar outra cousa. */
  IDEMPOTENCY_KEY_REUSED("booking.idempotency-key-reused", 422),
  /** A descarga en CSV abarca como moito {@code months} meses seguidos. */
  EXPORT_RANGE_TOO_LONG("booking.export-range-too-long", 422),
  /** Os datos dunha reserva feita coa conta da persoa son os da conta: non se corrixen. */
  HOLDER_NOT_EDITABLE("booking.holder-not-editable", 422),
  /** Nun uso por prazas a reserva leva polo menos unha persoa (ADR-0031, punto 10). */
  PARTICIPANTS_REQUIRED("booking.participants-required", 422),
  /** Só os usos por prazas levan persoas. */
  PARTICIPANTS_NOT_ALLOWED("booking.participants-not-allowed", 422),
  TOO_MANY_SEATS("booking.too-many-seats", 422),
  /** Dúas persoas da mesma reserva co mesmo nome e ano. */
  PARTICIPANT_DUPLICATE("booking.participant-duplicate", 422),
  /** Unha ficha que non é da conta, está desactivada ou xa non existe. */
  DEPENDENT_UNKNOWN("booking.dependent-unknown", 422),
  /** Unha persoa a cargo xa ten praza nesa franxa: {@code name}. */
  DEPENDENT_SEAT_TAKEN("booking.dependent-seat-taken", 409),
  /** Quedan menos prazas das pedidas: {@code free}. */
  NOT_ENOUGH_SEATS("booking.not-enough-seats", 409),
  /** O correo é dunha conta de traballo: non se lle liga unha reserva (ADR-0031, punto 7). */
  WORK_ACCOUNT_EMAIL("booking.work-account-email", 422),
  /** Demasiadas lecturas das persoas a cargo desde o mostrador nunha hora. */
  DEPENDENTS_RATE_LIMITED("booking.dependents-rate-limited", 429),
  /** «Validar igualmente» fóra da marxe pide o motivo, de 1 a 200 caracteres (ADR-0032). */
  VALIDATION_REASON_REQUIRED("booking.validation-reason-required", 422),
  /** Unha reserva cancelada non se valida, nin forzando. */
  NOT_VALIDATABLE("booking.not-validatable", 409),
  /** A falta só se marca cando a reserva xa rematou. */
  NO_SHOW_TOO_EARLY("booking.no-show-too-early", 422),
  /** Unha validada non pasa a falta sen desfacer antes a validación. */
  ALREADY_VALIDATED("booking.already-validated", 409),
  /** «Desfacer» sobre unha reserva sen marca. */
  ATTENDANCE_NOT_MARKED("booking.attendance-not-marked", 409),
  /** Unha cancelada non leva marca de asistencia. */
  ATTENDANCE_CANCELLED("booking.attendance-cancelled", 409),
  /** Validada ou con falta, xa non se cambia nin se cancela por internet. */
  ATTENDANCE_MARKED("booking.attendance-marked", 409),
  /** Bloqueada por faltas neste concello: {@code until}, {@code faults} e {@code windowDays}. */
  BLOCKED_NO_SHOW("booking.blocked-no-show", 403),
  /** A conta xa ten as esperas activas que caben: {@code max} (ADR-0033, punto 4). */
  WAIT_LIMIT("booking.wait-limit", 422),
  /** A hora pola que se quere esperar xa está libre: resérvase. */
  WAIT_SLOT_FREE("booking.wait-slot-free", 409),
  /** O correo xa ten unha reserva confirmada dese uso nesa franxa. */
  WAIT_OWN_BOOKING("booking.wait-own-booking", 409),
  /** A espera non existe, xa pasou ou é doutra conta. */
  WAIT_NOT_FOUND("booking.wait-not-found", 404),
  /**
   * A ligazón «Non quero recordatorios» está mal, foi manipulada ou é doutro concello (ADR-0035).
   */
  REMINDER_LINK_INVALID("booking.reminder-link-invalid", 422),
  /** A ligazón «Non quero recordatorios» caducou (ADR-0035). */
  REMINDER_LINK_EXPIRED("booking.reminder-link-expired", 410),
  /**
   * A reserva non se pode valorar: non rematou, foi cancelada, quedou como falta ou, con control de
   * entrada, non se validou (ADR-0037, punto 2).
   */
  REVIEW_NOT_ALLOWED("booking.review-not-allowed", 422),
  /** A reserva xa ten a súa valoración: unha por reserva, sen cambios (ADR-0037, punto 3). */
  REVIEW_EXISTS("booking.review-exists", 409),
  /** Nota fóra de 1 a 5 ou comentario de máis de 300 caracteres. */
  REVIEW_INVALID("booking.review-invalid", 422),
  /** A valoración non existe ou é doutro concello. */
  REVIEW_NOT_FOUND("booking.review-not-found", 404),
  /** O concello non ten «Buscar xente para xogar» aceso (ADR-0040). */
  MATCHES_OFF("booking.matches-off", 404),
  /** A reserva non se pode abrir: non está confirmada ou xa ten marca de asistencia. */
  MATCH_NOT_ALLOWED("booking.match-not-allowed", 422),
  /** Nun uso por prazas cada persoa reserva a súa: non se busca xente. */
  MATCH_SEATS_USE("booking.match-seats-use", 422),
  /** O token non trae nome: nunca se publica o correo. */
  MATCH_NAME_REQUIRED("booking.match-name-required", 422),
  /**
   * Xa non se pode cancelar de balde ou xa pasou o prazo do concello: {@code deadline}, o primeiro
   * dos dous.
   */
  MATCH_TOO_LATE("booking.match-too-late", 422),
  /** Mínimo, máximo ou «Xa somos» imposibles: {@code max}. */
  MATCH_COUNTS_INVALID("booking.match-counts-invalid", 422),
  /** O máximo novo queda por debaixo das persoas que xa van: {@code count}. */
  MATCH_MAXIMUM_BELOW_COUNT("booking.match-maximum-below-count", 422),
  /** A partida non existe, non se ve ou é doutro concello. */
  MATCH_NOT_FOUND("booking.match-not-found", 404),
  /** A partida xa non admite xente: pechada, cancelada ou empezada. */
  MATCH_CLOSED("booking.match-closed", 409),
  /** Non queda sitio. */
  MATCH_FULL("booking.match-full", 409),
  /** Quen organiza non se une á súa propia partida. */
  MATCH_OWN("booking.match-own", 409),
  /** Quen organiza non aceptou a solicitude: non se pide outra vez. */
  MATCH_REJECTED("booking.match-rejected", 409),
  /** A solicitude xa se aceptou ou rexeitou. */
  MATCH_PLAYER_DECIDED("booking.match-player-decided", 409),
  /** A partida xa empezou: xa non se sae. */
  MATCH_STARTED("booking.match-started", 409),
  /** A entidade non existe, está arquivada ou a persoa non é responsable dela (ADR-0039). */
  ORGANIZATION_NOT_FOUND("booking.organization-not-found", 404),
  /** Xa hai outra entidade dada de alta con ese nome no concello. */
  ORGANIZATION_NAME_TAKEN("booking.organization-name-taken", 409),
  /** Máis responsables dos que caben: {@code max}. */
  ORGANIZATION_MANAGERS_LIMIT("booking.organization-managers-limit", 422),
  /** Arquivar ou eliminar unha entidade con sesións ou reservas confirmadas futuras. */
  ORGANIZATION_HAS_FUTURE_BOOKINGS("booking.organization-has-future-bookings", 409),
  /** Os usos por prazas non valen para unha entidade. */
  ORGANIZATION_SEATS_USE("booking.organization-seats-use", 422),
  /** Unha tempada sen ningún día da semana. */
  SERIES_WEEKDAYS_REQUIRED("booking.series-weekdays-required", 422),
  /** Horas fóra de 15 en 15 minutos ou fin que non é despois do inicio. */
  SERIES_TIME_INVALID("booking.series-time-invalid", 422),
  /** Datas antes de hoxe, ao revés ou de máis dun ano. */
  SERIES_RANGE_INVALID("booking.series-range-invalid", 422),
  /** Mentres o xestor miraba a vista previa, cambiou algún día: leva a vista previa nova. */
  SERIES_CONFLICTS_CHANGED("booking.series-conflicts-changed", 409),
  SERIES_NOT_FOUND("booking.series-not-found", 404),
  /** «Rematar desde» fóra de hoxe … última sesión. */
  SERIES_END_INVALID("booking.series-end-invalid", 422),
  /** Na primeira versión as sesións non se moven: cancélanse (ADR-0039, punto 1). */
  SESSION_NOT_EDITABLE("booking.session-not-editable", 422),
  /** Unha sesión dunha actividade do concello non se move: cancélase desde a actividade. */
  ACTIVITY_SESSION_NOT_EDITABLE("booking.activity-session-not-editable", 422),
  /** Os usos por prazas non valen para unha actividade con horario (ADR-0041). */
  ACTIVITY_SEATS_USE("booking.activity-seats-use", 422),
  SERIES_REQUEST_NOT_FOUND("booking.series-request-not-found", 404),
  /** A solicitude xa se aprobou ou rexeitou. */
  SERIES_REQUEST_DECIDED("booking.series-request-decided", 409),
  /** A entidade xa ten as solicitudes pendentes que caben: {@code max}. */
  SERIES_REQUEST_LIMIT("booking.series-request-limit", 422),
  /** Rexeitar pide a mensaxe para a entidade. */
  SERIES_REQUEST_MESSAGE_REQUIRED("booking.series-request-message-required", 422);

  private final String code;
  private final int status;

  BookingError(String code, int status) {
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
