// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.tenancy.TenancyError;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.shared.tenancy.TenantSlugResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixa o concello da petición desde o URL ({@code /api/v1/municipalities/{slug}/...}) e límpao ao
 * rematar (ADR-0006). Resolve o slug sen abrir sesión JPA; descoñecido → 404 {@code
 * municipality.not-found}.
 */
public class TenantFilter extends OncePerRequestFilter {

  public static final String PREFIX = "/api/v1/municipalities/";

  private final TenantSlugResolver resolver;
  private final ProblemWriter problems;

  public TenantFilter(TenantSlugResolver resolver, ProblemWriter problems) {
    this.resolver = resolver;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<String> slug = slugOf(request);
    if (slug.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    Optional<TenantRef> tenant = resolver.resolve(slug.get());
    if (tenant.isEmpty()) {
      problems.write(request, response, TenancyError.MUNICIPALITY_NOT_FOUND);
      return;
    }
    try (TenantContext.Scope ignored = TenantContext.open(tenant.get())) {
      chain.doFilter(request, response);
    }
  }

  static Optional<String> slugOf(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.startsWith(PREFIX)) {
      return Optional.empty();
    }
    String rest = path.substring(PREFIX.length());
    int slash = rest.indexOf('/');
    String slug = slash < 0 ? rest : rest.substring(0, slash);
    return slug.isEmpty() ? Optional.empty() : Optional.of(slug);
  }
}
