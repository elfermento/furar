// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.shared.domain.Language;
import gal.furar.shared.persistence.NativeSql;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O SQL que JPA non expresa (ADR-0022): o bloqueo {@code for no key update} da instalación (punto
 * 4) e a orde alfabética coa colación ICU do idioma (punto 10). Sempre dentro dunha transacción
 * (RLS) e co concello como parámetro (A8b, {@code FacilitySqlTenantIT}).
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class FacilitySql {

  private static final String LOCK_NOT_AVAILABLE = "55P03";

  private final JdbcClient jdbc;

  FacilitySql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Bloquea a fila ata o fin da transacción. {@code no key update} e non {@code update}: non
   * bloquea as FK que apuntan á instalación (usos, ocupación).
   */
  boolean lock(UUID municipality, UUID id) {
    try {
      return lockRow(municipality, id);
    } catch (UncategorizedSQLException e) {
      // lock_timeout (application.yaml): quen ten o bloqueo tárdao demasiado → 503 common.busy.
      if (e.getSQLException() != null
          && LOCK_NOT_AVAILABLE.equals(e.getSQLException().getSQLState())) {
        throw new CannotAcquireLockException("facility lock not acquired in time", e);
      }
      throw e;
    }
  }

  private boolean lockRow(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id from facility
            where id = :id and municipality_id = :tenant
            for no key update
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query(UUID.class)
        .optional()
        .isPresent();
  }

  /** Ids dunha páxina en orde alfabética do idioma; o id desempata. */
  List<UUID> pageIds(UUID municipality, Language language, int limit, long offset) {
    JdbcClient.StatementSpec spec =
        language == Language.ES
            ? jdbc.sql(
                """
                select id from facility
                where municipality_id = :tenant
                order by name collate "es-x-icu", id
                limit :limit offset :offset
                """)
            : jdbc.sql(
                """
                select id from facility
                where municipality_id = :tenant
                order by name collate "gl-x-icu", id
                limit :limit offset :offset
                """);
    return spec.param("tenant", municipality)
        .param("limit", limit)
        .param("offset", offset)
        .query(UUID.class)
        .list();
  }

  /**
   * Ids dunha páxina das que poden verse no público (non de baixa e publicadas; con {@code
   * onlineBooking}, só as que se reservan por internet), na mesma orde ca {@link #pageIds}. Un
   * literal por variante: a regra A8b le cada SQL enteiro.
   */
  List<UUID> publishedPageIds(
      UUID municipality, Language language, boolean onlineBooking, int limit, long offset) {
    JdbcClient.StatementSpec spec;
    if (language == Language.ES) {
      spec =
          onlineBooking
              ? jdbc.sql(
                  """
                  select id from facility
                  where municipality_id = :tenant and published and status <> 'WITHDRAWN'
                    and online_booking
                  order by name collate "es-x-icu", id
                  limit :limit offset :offset
                  """)
              : jdbc.sql(
                  """
                  select id from facility
                  where municipality_id = :tenant and published and status <> 'WITHDRAWN'
                  order by name collate "es-x-icu", id
                  limit :limit offset :offset
                  """);
    } else {
      spec =
          onlineBooking
              ? jdbc.sql(
                  """
                  select id from facility
                  where municipality_id = :tenant and published and status <> 'WITHDRAWN'
                    and online_booking
                  order by name collate "gl-x-icu", id
                  limit :limit offset :offset
                  """)
              : jdbc.sql(
                  """
                  select id from facility
                  where municipality_id = :tenant and published and status <> 'WITHDRAWN'
                  order by name collate "gl-x-icu", id
                  limit :limit offset :offset
                  """);
    }
    return spec.param("tenant", municipality)
        .param("limit", limit)
        .param("offset", offset)
        .query(UUID.class)
        .list();
  }
}
