// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.in.web;

import gal.furar.notification.domain.JoinRequestRateLimitedException;
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
 * 429 {@code notification.join-request-rate-limited} coa cabeceira {@code Retry-After} en segundos
 * (RFC 9110, 10.2.3). O resto do problema é o de sempre ({@link ProblemWriter}); por iso vai antes
 * do manexador xeral.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class NotificationProblemHandler {

  private final ProblemWriter problems;

  NotificationProblemHandler(ProblemWriter problems) {
    this.problems = problems;
  }

  @ExceptionHandler(JoinRequestRateLimitedException.class)
  ResponseEntity<ProblemDetail> rateLimited(
      JoinRequestRateLimitedException e, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        problems.response(e.errorCode(), e.params(), e.violations(), request);
    return ResponseEntity.status(response.getStatusCode())
        .headers(response.getHeaders())
        .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
        .body(response.getBody());
  }
}
