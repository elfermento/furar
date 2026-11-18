// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha valoración (ADR-0037): a nota de 1 a 5 estrelas que deixa quen reservou sobre a instalación
 * dunha reserva pasada á que foi, cun comentario opcional. Non garda a persoa: chega a ela só pola
 * reserva. A instalación e o uso son os da reserva.
 *
 * @param comment sen espazos nos extremos; nulo se non o escribiu
 * @param hiddenAt cando o xestor ocultou o comentario; nulo se se ve
 */
public record Review(
    UUID id,
    UUID bookingId,
    UUID facilityId,
    UUID useId,
    int stars,
    @Nullable String comment,
    Instant createdAt,
    @Nullable Instant hiddenAt) {

  /** Nota máis baixa. */
  public static final int MIN_STARS = 1;

  /** Nota máis alta. */
  public static final int MAX_STARS = 5;

  /** Caracteres como moito do comentario. */
  public static final int MAX_COMMENT = 300;

  public Review {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(createdAt, "createdAt");
    if (stars < MIN_STARS || stars > MAX_STARS) {
      throw new DomainException(BookingError.REVIEW_INVALID);
    }
    comment = clean(comment);
    if (comment != null && comment.codePointCount(0, comment.length()) > MAX_COMMENT) {
      throw new DomainException(BookingError.REVIEW_INVALID);
    }
    if (hiddenAt != null && comment == null) {
      throw new IllegalArgumentException("Only a comment can be hidden");
    }
  }

  /** Unha valoración nova da reserva, coa instalación e o uso dela. */
  public static Review of(
      UUID id, Booking booking, int stars, @Nullable String comment, Instant now) {
    return new Review(
        id,
        booking.id().value(),
        booking.slot().facilityId(),
        booking.slot().useId(),
        stars,
        comment,
        now,
        null);
  }

  /** O comentario que se ve en público: nulo se non hai ou se o xestor o ocultou. */
  public @Nullable String publicComment() {
    return hiddenAt == null ? comment : null;
  }

  public boolean hidden() {
    return hiddenAt != null;
  }

  private static @Nullable String clean(@Nullable String comment) {
    if (comment == null) {
      return null;
    }
    String stripped = comment.strip();
    return stripped.isEmpty() ? null : stripped;
  }
}
