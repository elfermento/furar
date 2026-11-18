// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.Price;
import gal.furar.booking.domain.RentalMode;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A tempada dunha entidade (ADR-0039, puntos 4 e 11): o horario, o pago da tempada e, se se
 * rematou, desde cando. As súas sesións son reservas ({@code booking.series_id}). Non se edita:
 * rématase e créase outra. Inmutable.
 *
 * @param endedFrom desde que día se rematou (as sesións desde ese día están canceladas)
 * @param requestId a solicitude da que saíu, se a hai
 * @param idempotencyKey a clave de reintento coa que se creou
 * @param fingerprint SHA-256 da entrada canónica
 * @param createdBy quen a creou (o persoal)
 */
public record Series(
    UUID id,
    UUID organizationId,
    SeriesSchedule schedule,
    RentalMode rentalMode,
    @Nullable LocalDate endedFrom,
    PaymentStatus paymentStatus,
    @Nullable UUID requestId,
    String idempotencyKey,
    String fingerprint,
    String createdBy,
    @Nullable Long version,
    Instant createdAt,
    Instant updatedAt) {

  public Series {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(schedule, "schedule");
    Objects.requireNonNull(rentalMode, "rentalMode");
    Objects.requireNonNull(paymentStatus, "paymentStatus");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(fingerprint, "fingerprint");
    Objects.requireNonNull(createdBy, "createdBy");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (rentalMode == RentalMode.SEATS) {
      throw new IllegalArgumentException("A series is never by seats");
    }
  }

  /** O pago da tempada enteira, sen importe (ADR-0039, punto 11). */
  public enum PaymentStatus {
    FREE,
    PENDING,
    PAID;

    /**
     * Ao nacer: pendente se unha reserva dese uso tería que pagarse (o mesmo prezo que unha reserva
     * normal, {@link Price#initialPayment}); se non, gratis.
     */
    public static PaymentStatus initial(Price unitPrice) {
      return unitPrice.initialPayment() == gal.furar.booking.domain.PaymentStatus.PENDING
          ? PENDING
          : FREE;
    }
  }

  /**
   * Rematada desde {@code from}: entre hoxe e a última data. Rematala outra vez desde un día
   * posterior non cambia o día gardado.
   *
   * @throws DomainException {@code booking.series-end-invalid}
   */
  public Series endFrom(LocalDate from, LocalDate today, Instant now) {
    if (from.isBefore(today) || from.isAfter(schedule.lastDate())) {
      throw new DomainException(BookingError.SERIES_END_INVALID);
    }
    LocalDate kept = endedFrom != null && endedFrom.isBefore(from) ? endedFrom : from;
    return new Series(
        id,
        organizationId,
        schedule,
        rentalMode,
        kept,
        paymentStatus,
        requestId,
        idempotencyKey,
        fingerprint,
        createdBy,
        version,
        createdAt,
        now);
  }

  public Series withPayment(PaymentStatus status, Instant now) {
    return new Series(
        id,
        organizationId,
        schedule,
        rentalMode,
        endedFrom,
        Objects.requireNonNull(status, "status"),
        requestId,
        idempotencyKey,
        fingerprint,
        createdBy,
        version,
        createdAt,
        now);
  }

  /** Se xa non lle quedan días: rematada ou pasada a súa última data. */
  public boolean isEnded(LocalDate today) {
    return endedFrom != null || schedule.lastDate().isBefore(today);
  }
}
