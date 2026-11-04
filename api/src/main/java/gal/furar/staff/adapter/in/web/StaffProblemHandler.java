// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web;

import gal.furar.shared.web.ProblemWriter;
import gal.furar.staff.domain.InvitationRateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 429 {@code staff.invitation-rate-limited} coa cabeceira {@code Retry-After} en segundos (RFC
 * 9110, 10.2.3; plan do dominio 4, §1.2.7). Sen ela cando esperar non serve (a persoa xa gastou os
 * seus envíos). O resto do problema é o de sempre ({@link ProblemWriter}); por iso vai antes do
 * manexador xeral.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class StaffProblemHandler {

  private final ProblemWriter problems;

  StaffProblemHandler(ProblemWriter problems) {
    this.problems = problems;
  }

  @ExceptionHandler(InvitationRateLimitedException.class)
  ResponseEntity<ProblemDetail> rateLimited(
      InvitationRateLimitedException e, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        problems.response(e.errorCode(), e.params(), e.violations(), request);
    return e.retryAfterSeconds()
        .map(
            seconds ->
                ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                    .body(response.getBody()))
        .orElse(response);
  }
}
