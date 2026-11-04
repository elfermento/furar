// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.security.AuthError;
import gal.furar.shared.web.ProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 401 {@code auth.unauthenticated} en {@code problem+json}, tamén con token inválido. */
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ProblemWriter problems;

  ProblemAuthenticationEntryPoint(ProblemWriter problems) {
    this.problems = problems;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
      throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    problems.write(request, response, AuthError.UNAUTHENTICATED);
  }
}
