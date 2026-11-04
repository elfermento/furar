// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros das reservas (plan do dominio 2, §2.2). Cada código ten texto en {@code
 * messages/booking_*.properties} e en {@code gl.json}/{@code es.json}. O correo sen verificar é
 * {@code auth.email-not-verified}, de {@code AuthError}: dáo o interceptor de {@code
 * PersonUseCase}.
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
  IDEMPOTENCY_KEY_REUSED("booking.idempotency-key-reused", 422);

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
