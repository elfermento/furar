// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static gal.furar.booking.domain.Bookings.assertFails;
import static gal.furar.booking.domain.Bookings.confirmed;
import static gal.furar.booking.domain.Bookings.whole;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * A asistencia da reserva sen Spring (ADR-0032, puntos 1 a 5): a marxe de validación nos seus
 * bordos, ao segundo, e cada transición da marca (validada, falta, desfeita), coa regra de que unha
 * reserva con marca xa non se cambia nin se cancela por internet e de que o persoal, ao cancelala
 * ou cambiala, borra a marca.
 */
class BookingAttendanceTest {

  static final Duration MARGIN = Duration.ofMinutes(45);
  static final String STAFF = "sub-staff";

  /** Confirmada, de balde e sen normas: empeza 48 h despois de {@link Bookings#NOW} e dura 1 h. */
  static Booking booking() {
    return confirmed(Price.free(), BookingRules.none());
  }

  @Test
  void aNewBookingHasNoMarkAndWasNeverTouched() {
    Booking b = booking();
    assertThat(b.attendance()).isEqualTo(Attendance.NONE);
    assertThat(b.mark()).isEqualTo(AttendanceMark.NEVER);
  }

  @Test
  void theWindowGoesFromTheMarginBeforeTheStartToTheMarginAfterTheEndToTheSecond() {
    Booking b = booking();
    Instant opens = b.slot().start().minus(MARGIN);
    Instant closes = b.slot().end().plus(MARGIN);

    assertThat(b.inValidationWindow(opens.minusSeconds(1), MARGIN)).isFalse();
    assertThat(b.inValidationWindow(opens, MARGIN)).isTrue();
    assertThat(b.inValidationWindow(b.slot().start(), MARGIN)).isTrue();
    assertThat(b.inValidationWindow(closes, MARGIN)).isTrue();
    assertThat(b.inValidationWindow(closes.plusSeconds(1), MARGIN)).isFalse();
  }

  @Test
  void validatingMarksItUsedWithWhoAndWhenAndKeepsItConfirmed() {
    Booking b = booking();
    Instant at = b.slot().start();

    Booking used = b.validate(at, STAFF);

    assertThat(used.attendance()).isEqualTo(Attendance.USED);
    assertThat(used.mark()).isEqualTo(AttendanceMark.of(Attendance.USED, at, STAFF));
    assertThat(used.status()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(used.isDone(b.slot().end())).as("«Realizada» segue calculándose").isTrue();
  }

  @Test
  void aCancelledBookingIsNeitherValidatedNorMarked() {
    Booking cancelled = booking().cancelByMunicipality(Bookings.NOW, STAFF);
    Instant after = cancelled.slot().end();

    assertFails(BookingError.NOT_VALIDATABLE, () -> cancelled.validate(after, STAFF));
    assertFails(BookingError.ATTENDANCE_CANCELLED, () -> cancelled.markNoShow(after, STAFF));
  }

  @Test
  void aNoShowIsMarkedOnlyOnceTheBookingHasEndedAndNotOverAValidation() {
    Booking b = booking();
    Instant end = b.slot().end();

    assertFails(BookingError.NO_SHOW_TOO_EARLY, () -> b.markNoShow(end.minusSeconds(1), STAFF));
    assertThat(b.canMarkNoShow(end.minusSeconds(1))).isFalse();
    assertThat(b.canMarkNoShow(end)).isTrue();

    Booking missed = b.markNoShow(end, STAFF);
    assertThat(missed.attendance()).isEqualTo(Attendance.NO_SHOW);
    assertThat(missed.status()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(missed.canMarkNoShow(end)).isFalse();

    Booking used = b.validate(b.slot().start(), STAFF);
    assertFails(BookingError.ALREADY_VALIDATED, () -> used.markNoShow(end, STAFF));
  }

  @Test
  void validatingANoShowRemovesTheFault() {
    Booking b = booking();
    Booking missed = b.markNoShow(b.slot().end(), "furar:no-show");

    Booking used = missed.validate(b.slot().end().plusSeconds(60), STAFF);

    assertThat(used.attendance()).isEqualTo(Attendance.USED);
    assertThat(used.mark().by()).isEqualTo(STAFF);
  }

  @Test
  void undoingLeavesNoMarkButRemembersWhoAndWhen() {
    Booking b = booking();
    Instant end = b.slot().end();
    Instant later = end.plusSeconds(600);

    Booking undone = b.markNoShow(end, STAFF).undoAttendance(later, "sub-manager");

    assertThat(undone.attendance()).isEqualTo(Attendance.NONE);
    assertThat(undone.mark())
        .as("xa tocada: a tarefa non a volve marcar")
        .isEqualTo(AttendanceMark.of(Attendance.NONE, later, "sub-manager"));
    assertThat(undone.canMarkNoShow(later)).as("o botón volve saír").isTrue();
    assertFails(BookingError.ATTENDANCE_NOT_MARKED, () -> undone.undoAttendance(later, STAFF));
    assertFails(BookingError.ATTENDANCE_NOT_MARKED, () -> b.undoAttendance(later, STAFF));
  }

  @Test
  void aMarkedBookingIsNotChangedNorCancelledOnline() {
    Booking b = booking();
    // Validada antes do inicio, dentro da marxe: aínda estaría en prazo para cancelar.
    Instant early = b.slot().start().minus(Duration.ofMinutes(30));
    Booking used = b.validate(early, STAFF);

    assertThat(used.canCancel(early)).isFalse();
    assertFails(BookingError.ATTENDANCE_MARKED, () -> used.cancelByPerson(early, "sub-1"));
    assertFails(BookingError.ATTENDANCE_MARKED, () -> used.checkChangeable(early));
    assertFails(
        BookingError.ATTENDANCE_MARKED,
        () -> used.reschedule(whole(72, 1), Price.free(), BookingRules.none(), early));
  }

  @Test
  void theStaffCancellingOrMovingAMarkedBookingClearsTheMark() {
    Booking b = booking();
    Instant early = b.slot().start().minus(Duration.ofMinutes(30));
    Booking used = b.validate(early, STAFF);

    Booking cancelled = used.cancelByMunicipality(early, "sub-manager");
    assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED_BY_MUNICIPALITY);
    assertThat(cancelled.mark())
        .isEqualTo(AttendanceMark.of(Attendance.NONE, early, "sub-manager"));

    Booking moved =
        used.rescheduleByMunicipality(
            whole(72, 1), 1, Price.free(), BookingRules.none(), early, "sub-manager");
    assertThat(moved.attendance()).isEqualTo(Attendance.NONE);
    assertThat(moved.mark().by()).isEqualTo("sub-manager");

    // Sen marca, o cambio non a toca: a tarefa das faltas séguea vendo.
    Booking plain =
        b.rescheduleByMunicipality(
            whole(72, 1), 1, Price.free(), BookingRules.none(), early, "sub-manager");
    assertThat(plain.mark()).isEqualTo(AttendanceMark.NEVER);
  }
}
