// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.adapter.out.persistence;

import gal.furar.shared.audit.AuditCursor;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditFilter;
import gal.furar.shared.audit.AuditPage;
import gal.furar.shared.audit.AuditRecord;
import gal.furar.shared.audit.AuditTrail;
import gal.furar.shared.persistence.NativeSql;
import gal.furar.shared.security.Role;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AuditTrail} sobre {@code audit_event} (ADR-0020; plan do dominio 4, §1.2.12).
 *
 * <ul>
 *   <li>O concello vai sempre explícito ({@code municipality_id = :municipality}): a táboa non ten
 *       RLS porque recolle accións sen concello e sobrevive ao concello.
 *   <li>Paxinación por chave {@code (occurred_at, id)} descendente: a seguinte páxina empeza
 *       despois do último evento lido, así que non repite nin salta eventos aínda que entren novos
 *       ou que varios teñan o mesmo instante. Lese un de máis para saber se hai seguinte.
 *   <li>O concello usa o índice {@code (municipality_id, occurred_at desc)}; a plataforma percorre
 *       o período (volume baixo, sen índice GIN sobre {@code actor_roles}).
 * </ul>
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "O rexistro de actividade é da plataforma: o concello vai como filtro explícito e a"
            + " plataforma le as súas accións de todos os concellos e as que non teñen concello")
public class JdbcAuditTrail implements AuditTrail {

  private static final String COLUMNS =
      """
      select id, occurred_at, actor_subject, actor_roles, action, outcome, municipality_id,
             municipality_slug, request_id, details::text as details
        from audit_event
      """;

  private static final String FILTERS =
      """
         and occurred_at >= :since and occurred_at < :until
         and (:anyArea or action like :areaPrefix)
         and (:anyActor or actor_subject = :actor)
         and (:anyOutcome or outcome = :outcome)
         and (:first or (occurred_at, id) < (:afterAt, :afterId))
       order by occurred_at desc, id desc
       limit :limit
      """;

  private static final String MUNICIPALITY =
      COLUMNS + " where municipality_id = :municipality\n" + FILTERS;

  private static final String PLATFORM =
      COLUMNS
          + " where (municipality_id is null or 'PLATFORM_ADMIN' = any(actor_roles))\n"
          + FILTERS;

  private static final String EXPORT =
      COLUMNS
          + """
           where municipality_id = :municipality
             and occurred_at >= :since and occurred_at < :until
           order by occurred_at, id
           limit :limit
          """;

  private static final OffsetDateTime NO_CURSOR_AT =
      OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final UUID NO_CURSOR_ID = new UUID(0, 0);

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public JdbcAuditTrail(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public AuditPage page(UUID municipalityId, AuditFilter filter, @Nullable AuditCursor after) {
    Objects.requireNonNull(municipalityId, "municipalityId");
    return read(
        bind(jdbc.sql(MUNICIPALITY).param("municipality", municipalityId), filter, after)
            .query(this::record)
            .list());
  }

  @Override
  public AuditPage platformPage(AuditFilter filter, @Nullable AuditCursor after) {
    return read(bind(jdbc.sql(PLATFORM), filter, after).query(this::record).list());
  }

  @Override
  public List<AuditRecord> all(UUID municipalityId, Instant from, Instant to, int limit) {
    Objects.requireNonNull(municipalityId, "municipalityId");
    return jdbc.sql(EXPORT)
        .param("municipality", municipalityId)
        .param("since", utc(from))
        .param("until", utc(to))
        .param("limit", limit + 1)
        .query(this::record)
        .list();
  }

  private static JdbcClient.StatementSpec bind(
      JdbcClient.StatementSpec sql, AuditFilter filter, @Nullable AuditCursor after) {
    String area = filter.area();
    String actor = filter.actorSubject();
    Outcome outcome = filter.outcome();
    return sql.param("since", utc(filter.from()))
        .param("until", utc(filter.to()))
        .param("anyArea", area == null)
        .param("areaPrefix", area == null ? "" : area + ".%")
        .param("anyActor", actor == null)
        .param("actor", actor == null ? "" : actor)
        .param("anyOutcome", outcome == null)
        .param("outcome", outcome == null ? "" : outcome.name())
        .param("first", after == null)
        .param("afterAt", after == null ? NO_CURSOR_AT : utc(after.occurredAt()))
        .param("afterId", after == null ? NO_CURSOR_ID : after.id())
        .param("limit", PAGE_SIZE + 1);
  }

  /** Corta o evento de máis e, se o había, deixa o cursor no último da páxina. */
  private static AuditPage read(List<AuditRecord> rows) {
    if (rows.size() <= PAGE_SIZE) {
      return new AuditPage(rows, null);
    }
    List<AuditRecord> page = rows.subList(0, PAGE_SIZE);
    return new AuditPage(page, page.get(PAGE_SIZE - 1).cursor());
  }

  private AuditRecord record(ResultSet rs, int row) throws SQLException {
    return new AuditRecord(
        rs.getObject("id", UUID.class),
        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
        rs.getString("actor_subject"),
        roles(rs.getArray("actor_roles")),
        rs.getString("action"),
        Outcome.valueOf(rs.getString("outcome")),
        rs.getObject("municipality_id", UUID.class),
        rs.getString("municipality_slug"),
        rs.getString("request_id"),
        details(rs.getString("details")));
  }

  private static Set<Role> roles(@Nullable Array array) throws SQLException {
    Set<Role> roles = EnumSet.noneOf(Role.class);
    if (array != null) {
      for (Object role : (Object[]) array.getArray()) {
        roles.add(Role.valueOf((String) role));
      }
    }
    return roles;
  }

  /** Os detalles en JSON; as claves con valor nulo non se devolven. */
  private Map<String, Object> details(@Nullable String text) {
    Map<String, Object> details = new LinkedHashMap<>();
    if (text != null) {
      @SuppressWarnings("unchecked")
      Map<String, Object> parsed = json.readValue(text, Map.class);
      parsed.forEach(
          (key, value) -> {
            if (value != null) {
              details.put(key, value);
            }
          });
    }
    return details;
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
