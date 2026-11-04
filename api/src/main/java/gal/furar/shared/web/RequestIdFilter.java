// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Identificador de petición para correlacionar rexistros (OPS-8): toma {@code X-Request-Id} de
 * Caddy se é seguro ou xera un, déixao no MDC ({@code http.request.id}, campo ECS) e devólveo na
 * resposta. Así unha queixa cun erro da pantalla atópase nos rexistros.
 */
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "http.request.id";
  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HEADER);
    String id = incoming != null && SAFE.matcher(incoming).matches() ? incoming : newId();
    response.setHeader(HEADER, id);
    MDC.put(MDC_KEY, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private static String newId() {
    return UUID.randomUUID().toString();
  }
}
