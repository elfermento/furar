// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.adapter.out.persistence;

import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.persistence.NativeSql;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Borra do rexistro de actividade o que pasou de {@code furar.audit.retention} (ADR-0020, RGPD art.
 * 5.1.e; P3Y por defecto: o bloqueado tamén se borra aquí). A API non ten DELETE en {@code
 * audit_event}: chama á función {@code purge_audit_events}, que se executa como o dono e nunca
 * borra nada con menos dun ano. É da plataforma e idempotente: se hai varias réplicas, a segunda
 * non atopa nada.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "O rexistro de actividade é da plataforma; a conservación aplícase a todos os concellos")
public class JdbcAuditRetention {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcAuditRetention.class);

  private final JdbcClient jdbc;
  private final AuditRetention policy;
  private final Clock clock;

  public JdbcAuditRetention(JdbcClient jdbc, AuditRetention policy, Clock clock) {
    this.jdbc = jdbc;
    this.policy = policy;
    this.clock = clock;
  }

  /** Borra os eventos anteriores ao prazo e devolve cantos. */
  @Scheduled(cron = "${furar.audit.purge-cron:0 30 3 * * *}")
  public long purge() {
    Instant now = clock.instant();
    OffsetDateTime cutoff = policy.keptSince(now).atOffset(ZoneOffset.UTC);
    Long purged =
        jdbc.sql("select purge_audit_events(:cutoff)")
            .param("cutoff", cutoff)
            .query(Long.class)
            .single();
    LOG.info("audit retention={} cutoff={} purged={}", policy.retention(), cutoff, purged);
    return purged;
  }
}
