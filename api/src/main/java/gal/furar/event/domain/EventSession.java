// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha sesión dunha actividade con horario (ADR-0041): un día, co seu inicio e fin. Nunha
 * instalación, a reserva que a ocupa ({@code bookingId}); noutro sitio, ningunha.
 *
 * @param attendanceTakenAt a primeira vez que se pasou lista; nula se aínda non
 */
public record EventSession(
    UUID id,
    EventId eventId,
    LocalDate date,
    Instant startsAt,
    Instant endsAt,
    @Nullable UUID bookingId,
    @Nullable Instant cancelledAt,
    @Nullable Instant attendanceTakenAt) {

  public EventSession {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(startsAt, "startsAt");
    Objects.requireNonNull(endsAt, "endsAt");
    if (!endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("A session ends after it starts");
    }
  }

  public boolean cancelled() {
    return cancelledAt != null;
  }

  public boolean started(Instant now) {
    return !now.isBefore(startsAt);
  }

  /**
   * Se se pode cancelar só esta sesión: sen cancelar, sen empezar e sen lista pasada.
   *
   * @throws DomainException {@code event.session-cancelled}, {@code event.session-started} ou
   *     {@code event.session-has-attendance}
   */
  public void checkCancellable(Instant now) {
    if (cancelled()) {
      throw new DomainException(EventError.SESSION_CANCELLED);
    }
    if (started(now)) {
      throw new DomainException(EventError.SESSION_STARTED);
    }
    if (attendanceTakenAt != null) {
      throw new DomainException(EventError.SESSION_HAS_ATTENDANCE);
    }
  }

  /**
   * Pasar lista: desde {@code margin} antes do inicio (a marxe da validación das reservas,
   * ADR-0032) e despois, para corrixir, sempre.
   *
   * @throws DomainException {@code event.session-cancelled} ou {@code event.attendance-too-early}
   */
  public void checkAttendance(Instant now, Duration margin) {
    if (cancelled()) {
      throw new DomainException(EventError.SESSION_CANCELLED);
    }
    if (now.isBefore(startsAt.minus(margin))) {
      throw new DomainException(EventError.ATTENDANCE_TOO_EARLY);
    }
  }

  /** Cancelada en {@code now}. */
  public EventSession cancel(Instant now) {
    return new EventSession(id, eventId, date, startsAt, endsAt, bookingId, now, attendanceTakenAt);
  }
}
