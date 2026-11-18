// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A asistencia dunha reserva (ADR-0032, puntos 4 a 6): validala co seu código, marcala como falta e
 * desfacer. Permiso {@code booking.attendance} (Xestión e Recepción). Cada escritura deixa fila en
 * {@code booking_event} e no rexistro de actividade.
 */
public interface AttendanceUseCase {

  /**
   * Le o código e valida a reserva se toca: dentro da marxe, soa; fóra, só con {@code force} e
   * motivo. Repetir a lectura non cambia nada. Sobre unha falta, quítaa (e o bloqueo que causase).
   *
   * @param reason o motivo de validar fóra da marxe, de 1 a 200 caracteres
   * @throws DomainException {@code booking.not-found} se o código non é dunha reserva do concello;
   *     {@code booking.validation-reason-required} ou {@code booking.not-validatable} ao forzar
   */
  Validation validate(String code, boolean force, @Nullable String reason);

  /**
   * O persoal marca a falta dunha reserva xa rematada (If-Match). Se o correo chega ao límite do
   * concello, queda bloqueado; a persoa recibe o correo da falta.
   *
   * @throws DomainException {@code booking.not-found}, {@code common.stale-version}, {@code
   *     booking.no-show-too-early}, {@code booking.already-validated} ou {@code
   *     booking.attendance-cancelled}
   */
  ManagedBooking markNoShow(BookingId id, long expectedVersion);

  /**
   * Desfai a validación ou a falta (If-Match), sen prazo; se a falta causara un bloqueo, levántao.
   *
   * @throws DomainException {@code booking.not-found}, {@code common.stale-version} ou {@code
   *     booking.attendance-not-marked}
   */
  ManagedBooking undo(BookingId id, long expectedVersion);

  /** Que pasou ao ler o código. */
  enum Outcome {
    /** Validouse agora. */
    VALIDATED,
    /** Confirmada e fóra da marxe: non se validou. */
    OUT_OF_WINDOW,
    /** Xa estaba validada. */
    ALREADY_VALIDATED,
    /** Está cancelada. */
    CANCELLED
  }

  /**
   * O resultado de ler un código, sempre cos datos da reserva.
   *
   * @param attendanceAt cando se validou (agora ou antes); nulo se non está validada
   * @param attendanceBy o nome de quen a validou; nulo se non está validada ou xa non se sabe
   */
  record Validation(
      Outcome outcome,
      ManagedBooking booking,
      @Nullable Instant attendanceAt,
      @Nullable String attendanceBy) {

    public Validation {
      Objects.requireNonNull(outcome, "outcome");
      Objects.requireNonNull(booking, "booking");
    }
  }
}
