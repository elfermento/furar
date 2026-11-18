// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.shared.tenancy.TenantSlugResolver;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Slug → concello por JDBC, sen sesión JPA, para o filtro de entrada (ADR-0006). Como o slug é
 * inmutable, os acertos gárdanse en memoria; os fallos non (o concello pódese crear despois).
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason = "O filtro de entrada traduce slug a id antes de haber concello no contexto")
class MunicipalitySlugJdbcResolver implements TenantSlugResolver {

  private static final int MAX_SLUG = 40;

  private final JdbcClient jdbc;
  private final Map<String, TenantRef> cache = new ConcurrentHashMap<>();

  MunicipalitySlugJdbcResolver(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<TenantRef> resolve(String slug) {
    if (slug == null || slug.isEmpty() || slug.length() > MAX_SLUG) {
      return Optional.empty();
    }
    TenantRef cached = cache.get(slug);
    if (cached != null) {
      return Optional.of(cached);
    }
    Optional<TenantRef> found =
        jdbc.sql("select id from municipality where slug = :slug")
            .param("slug", slug)
            .query(UUID.class)
            .optional()
            .map(id -> new TenantRef(id, slug));
    found.ifPresent(ref -> cache.put(slug, ref));
    return found;
  }
}
