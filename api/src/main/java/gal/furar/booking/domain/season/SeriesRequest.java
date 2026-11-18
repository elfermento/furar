// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.RentalMode;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A tempada que pide unha entidade (ADR-0039, punto 10): o horario, unha nota e quen a pide. O
 * xestor apróbaa (creando a serie, cos datos axustados) ou rexéitaa cunha mensaxe. Inmutable.
 *
 * @param requestedBySubject a conta de quen a pide; nula tras a baixa da conta
 * @param requestedByEmail o seu correo; nulo tras a baixa da conta
 */
public record SeriesRequest(
    UUID id,
    UUID organizationId,
    SeriesSchedule schedule,
    RentalMode rentalMode,
    @Nullable String note,
    @Nullable String requestedBySubject,
    @Nullable String requestedByEmail,
    Status status,
    @Nullable String rejectionMessage,
    @Nullable UUID seriesId,
    @Nullable Instant decidedAt,
    @Nullable String decidedBy,
    @Nullable Long version,
    Instant createdAt,
    Instant updatedAt) {

  /** Mensaxe ao rexeitar, como moito. */
  public static final int MAX_MESSAGE = 1000;

  /** Nota de quen a pide, como moito. */
  public static final int MAX_NOTE = 500;

  public SeriesRequest {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(schedule, "schedule");
    Objects.requireNonNull(rentalMode, "rentalMode");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if ((status == Status.PENDING) != (decidedAt == null)) {
      throw new IllegalStateException("Only a decided request has a decision date");
    }
    if ((status == Status.REJECTED) != (rejectionMessage != null)) {
      throw new IllegalStateException("Only a rejected request has a message");
    }
  }

  /** Estado da solicitude. */
  public enum Status {
    PENDING,
    APPROVED,
    REJECTED
  }

  /** Nova, pendente. */
  public static SeriesRequest create(
      UUID id,
      UUID organizationId,
      SeriesSchedule schedule,
      RentalMode rentalMode,
      @Nullable String note,
      String subject,
      String email,
      Instant now) {
    String trimmed = note == null || note.isBlank() ? null : note.strip();
    if (trimmed != null && trimmed.length() > MAX_NOTE) {
      throw new IllegalArgumentException("Note too long");
    }
    return new SeriesRequest(
        id,
        organizationId,
        schedule,
        rentalMode,
        trimmed,
        subject,
        email,
        Status.PENDING,
        null,
        null,
        null,
        null,
        null,
        now,
        now);
  }

  /**
   * @throws DomainException {@code booking.series-request-decided} se xa non está pendente
   */
  public void requirePending() {
    if (status != Status.PENDING) {
      throw new DomainException(BookingError.SERIES_REQUEST_DECIDED);
    }
  }

  /** Aprobada: a serie que se creou con ela. */
  public SeriesRequest approve(UUID series, String actor, Instant now) {
    requirePending();
    return new SeriesRequest(
        id,
        organizationId,
        schedule,
        rentalMode,
        note,
        requestedBySubject,
        requestedByEmail,
        Status.APPROVED,
        null,
        Objects.requireNonNull(series, "series"),
        now,
        actor,
        version,
        createdAt,
        now);
  }

  /**
   * Rexeitada coa mensaxe para a entidade.
   *
   * @throws DomainException {@code booking.series-request-message-required} sen mensaxe; {@code
   *     booking.series-request-decided}
   */
  public SeriesRequest reject(@Nullable String message, String actor, Instant now) {
    String text = message == null ? "" : message.strip();
    if (text.isEmpty()) {
      throw new DomainException(BookingError.SERIES_REQUEST_MESSAGE_REQUIRED);
    }
    if (text.length() > MAX_MESSAGE) {
      throw new IllegalArgumentException("Message too long");
    }
    requirePending();
    return new SeriesRequest(
        id,
        organizationId,
        schedule,
        rentalMode,
        note,
        requestedBySubject,
        requestedByEmail,
        Status.REJECTED,
        text,
        null,
        now,
        actor,
        version,
        createdAt,
        now);
  }
}
