// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.application.port.in.DependentsRateLimitedException;
import gal.furar.booking.application.port.in.SeriesConflictsChangedException;
import gal.furar.booking.application.port.in.SlotTakenException;
import gal.furar.shared.web.ProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 409 {@code booking.slot-taken} coa extensión {@code alternatives[]} (RFC 9457, ADR-0008; ADR-0025
 * punto 9). O resto do problema é o de sempre ({@link ProblemWriter}); por iso vai antes do
 * manexador xeral.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class BookingProblemHandler {

  private final ProblemWriter problems;

  BookingProblemHandler(ProblemWriter problems) {
    this.problems = problems;
  }

  /** 429 {@code booking.dependents-rate-limited} con {@code Retry-After} en segundos. */
  @ExceptionHandler(DependentsRateLimitedException.class)
  ResponseEntity<ProblemDetail> rateLimited(
      DependentsRateLimitedException e, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        problems.response(e.errorCode(), e.params(), e.violations(), request);
    return ResponseEntity.status(response.getStatusCode())
        .headers(response.getHeaders())
        .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
        .body(response.getBody());
  }

  /** 409 {@code booking.series-conflicts-changed} coa vista previa nova en {@code preview}. */
  @ExceptionHandler(SeriesConflictsChangedException.class)
  ResponseEntity<ProblemDetail> seriesConflicts(
      SeriesConflictsChangedException e, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        problems.response(e.errorCode(), e.params(), e.violations(), request);
    ProblemDetail body = response.getBody();
    if (body != null) {
      body.setProperty("preview", SeasonWebMapper.preview(e.preview()));
    }
    return response;
  }

  @ExceptionHandler(SlotTakenException.class)
  ResponseEntity<ProblemDetail> slotTaken(SlotTakenException e, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        problems.response(e.errorCode(), e.params(), e.violations(), request);
    ProblemDetail body = response.getBody();
    if (body != null) {
      body.setProperty(
          "alternatives", e.alternatives().stream().map(BookingWebMapper::slot).toList());
    }
    return response;
  }
}
