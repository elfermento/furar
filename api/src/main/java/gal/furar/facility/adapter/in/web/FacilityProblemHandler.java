// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.application.port.in.AffectedBookingsException;
import gal.furar.facility.domain.FacilityError;
import gal.furar.shared.web.ProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 409 {@code facility.affected-bookings} coa extensión {@code affected[]} (RFC 9457, ADR-0008;
 * ADR-0022 punto 6). O resto do problema é o de sempre ({@link ProblemWriter}); por iso vai antes
 * do manexador xeral. Tamén o 413 dos límites de {@code spring.servlet.multipart.*}: a única subida
 * {@code multipart} da API son as fotos, así que sae como {@code facility.photo-too-large}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class FacilityProblemHandler {

  private final ProblemWriter problems;

  FacilityProblemHandler(ProblemWriter problems) {
    this.problems = problems;
  }

  @ExceptionHandler(AffectedBookingsException.class)
  ResponseEntity<ProblemDetail> affected(AffectedBookingsException e, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        problems.response(e.errorCode(), e.params(), e.violations(), request);
    ProblemDetail body = response.getBody();
    if (body != null) {
      body.setProperty("affected", e.affected().stream().map(FacilityWebMapper::affected).toList());
    }
    return response;
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> tooLarge(
      MaxUploadSizeExceededException e, HttpServletRequest request) {
    return problems.response(
        FacilityError.PHOTO_TOO_LARGE, Map.of("max", "8 MB"), List.of(), request);
  }
}
