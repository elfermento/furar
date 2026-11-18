// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.out.persistence;

import gal.furar.people.application.port.out.AccountClosures;
import gal.furar.people.domain.AccountDeletionBlocker;
import gal.furar.shared.persistence.NativeSql;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eliminar a conta (ADR-0043): chama ás funcións {@code account_deletion_blockers} e {@code
 * close_account_counted} (V202611171130), que corren como o dono porque a RLS de cada táboa só
 * deixa ver o concello da transacción. Devolven un JSON cos tipos, os concellos e as cifras, sen
 * datos de ninguén; sen táboas no SQL.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "A conta é común a todos os concellos: as funcións só din que impide eliminala e cantas"
            + " filas tocou a baixa en cada concello, sen ningún dato delas")
class AccountClosureSql implements AccountClosures {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final JdbcClient jdbc;

  AccountClosureSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<AccountDeletionBlocker> blockers(String subject) {
    String json =
        jdbc.sql("select account_deletion_blockers(:subject)::text")
            .param("subject", subject)
            .query(String.class)
            .single();
    return parseBlockers(json);
  }

  @Override
  public Map<UUID, Long> close(String subject) {
    String json =
        jdbc.sql("select close_account_counted(:subject)::text")
            .param("subject", subject)
            .query(String.class)
            .single();
    return parseCounts(json);
  }

  /** {@code [{"kind", "municipalityId", "eventId", "nameGl", "nameEs", "count"}]}. */
  static List<AccountDeletionBlocker> parseBlockers(@Nullable String json) {
    List<AccountDeletionBlocker> blockers = new ArrayList<>();
    if (json == null) {
      return blockers;
    }
    for (JsonNode row : JSON.readTree(json)) {
      blockers.add(
          new AccountDeletionBlocker(
              AccountDeletionBlocker.Kind.valueOf(row.get("kind").asString()),
              uuid(row.get("municipalityId")),
              uuid(row.get("eventId")),
              text(row.get("nameGl")),
              text(row.get("nameEs")),
              row.get("count").asLong()));
    }
    return blockers;
  }

  /** {@code {"<concello>": filas}}. */
  static Map<UUID, Long> parseCounts(@Nullable String json) {
    Map<UUID, Long> counts = new LinkedHashMap<>();
    if (json == null) {
      return counts;
    }
    JSON.readTree(json)
        .properties()
        .forEach(e -> counts.put(UUID.fromString(e.getKey()), e.getValue().asLong()));
    return counts;
  }

  private static @Nullable UUID uuid(@Nullable JsonNode node) {
    String value = text(node);
    return value == null ? null : UUID.fromString(value);
  }

  private static @Nullable String text(@Nullable JsonNode node) {
    return node == null || node.isNull() ? null : node.asString();
  }
}
