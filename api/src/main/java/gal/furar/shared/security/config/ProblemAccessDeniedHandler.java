// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.security.AuthError;
import gal.furar.shared.web.ProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 403 {@code auth.forbidden} en {@code problem+json}. */
class ProblemAccessDeniedHandler implements AccessDeniedHandler {

  private final ProblemWriter problems;

  ProblemAccessDeniedHandler(ProblemWriter problems) {
    this.problems = problems;
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException {
    problems.write(request, response, AuthError.FORBIDDEN);
  }
}
