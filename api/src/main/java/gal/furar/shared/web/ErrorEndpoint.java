// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.security.AuthError;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Substitúe o {@code BasicErrorController} de Boot: os erros que o contedor reenvía a {@code
 * /error} saen tamén en {@code problem+json} (ADR-0008).
 */
@Hidden
@RestController
public class ErrorEndpoint implements ErrorController {

  private final ProblemWriter problems;

  public ErrorEndpoint(ProblemWriter problems) {
    this.problems = problems;
  }

  @RequestMapping("${server.error.path:/error}")
  ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
    Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int code = status instanceof Integer i ? i : 500;
    return problems.response(codeFor(code), Map.of(), List.of(), request);
  }

  static ErrorCode codeFor(int status) {
    return switch (status) {
      case 400 -> CommonError.INVALID_PARAMETER;
      case 401 -> AuthError.UNAUTHENTICATED;
      case 403 -> AuthError.FORBIDDEN;
      case 404 -> CommonError.NOT_FOUND;
      case 405 -> CommonError.METHOD_NOT_ALLOWED;
      case 406 -> CommonError.NOT_ACCEPTABLE;
      case 415 -> CommonError.UNSUPPORTED_MEDIA_TYPE;
      default -> CommonError.INTERNAL;
    };
  }
}
