// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.adapter.out.persistence;

import com.zaxxer.hikari.HikariDataSource;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.persistence.NativeSql;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.RoleGrant;
import gal.furar.shared.web.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AuditLog} na táboa {@code audit_event}, onde {@code furar_app} só pode engadir e ler
 * (ADR-0020): unha inxección SQL non borra o rastro. Cada evento vai tamén aos rexistros (logger
 * {@code furar.audit}, JSON ECS en prod) para quen os recolla fóra do servidor.
 *
 * <p>Onde se escribe:
 *
 * <ul>
 *   <li>Éxito dentro dunha transacción de escritura: na mesma transacción e na mesma conexión. Só
 *       queda se a escritura se confirma (nunca un éxito que non houbo) e non pide unha segunda
 *       conexión mentres se ten o bloqueo da instalación (ADR-0022).
 *   <li>Fallo, ou fóra dunha transacción de escritura (só lectura, sen transacción): ao momento, en
 *       autocommit, por un pool propio de {@value #POOL_SIZE} conexións. Queda aínda que a
 *       transacción do caso de uso se desfaga, e como non usa o pool da API non o pode esgotar: con
 *       todas as conexións da API ocupadas por peticións que agardan o mesmo bloqueo, o rexistro
 *       segue saíndo.
 * </ul>
 *
 * <p>Cada evento leva os roles efectivos de quen actúa no concello da acción ({@code actor_roles}):
 * os que valen nesa petición no concello do evento (o da táboa de pertenzas) e os globais ({@code
 * PLATFORM_ADMIN}), máis os que o propio evento sabe ({@link AuditEvent#knownActorRoles()}, {@code
 * staff.sign-in}). Só os de persoal e plataforma: {@code RESIDENT} non dá permisos de xestión.
 *
 * <p>As tarefas de Furar sen persoa (a conservación do persoal) gravan co seu nome de sistema
 * ({@link AuditEvent#systemActor()}, {@code furar:…}) e sen roles.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "O rexistro de actividade é da plataforma: recolle accións sen concello (importar) e"
            + " sobrevive ao concello")
public class JdbcAuditLog implements AuditLog, DisposableBean {

  static final int POOL_SIZE = 2;
  private static final Logger AUDIT = LoggerFactory.getLogger("furar.audit");

  /** Os roles que se gardan: os de xestión do concello e o da plataforma. */
  private static final Set<Role> RECORDED =
      EnumSet.of(Role.PLATFORM_ADMIN, Role.MANAGER, Role.STAFF);

  private static final String INSERT =
      """
      insert into audit_event (id, occurred_at, actor_subject, actor_roles, action, outcome,
        municipality_id, municipality_slug, request_id, details)
      values (:id, :occurredAt, :subject, cast(:roles as text[]), :action, :outcome,
        :municipalityId, :slug, :requestId, cast(:details as jsonb))
      """;

  private final JdbcClient jdbc;
  private final JdbcClient autonomous;
  private final HikariDataSource autonomousPool;
  private final CurrentActor currentActor;
  private final JsonMapper json;
  private final Clock clock;

  public JdbcAuditLog(
      JdbcClient jdbc,
      DataSource dataSource,
      CurrentActor currentActor,
      JsonMapper json,
      Clock clock) {
    this.jdbc = jdbc;
    this.autonomousPool = pool(dataSource);
    this.autonomous = JdbcClient.create(autonomousPool);
    this.currentActor = currentActor;
    this.json = json;
    this.clock = clock;
  }

  /** Pool pequeno coa mesma base e o mesmo rol ca o da API, sen conexións en repouso. */
  private static HikariDataSource pool(DataSource dataSource) {
    if (!(dataSource instanceof HikariDataSource main)) {
      throw new IllegalStateException("the audit log expects a Hikari data source");
    }
    HikariDataSource pool = new HikariDataSource();
    pool.setPoolName("furar-audit");
    pool.setJdbcUrl(main.getJdbcUrl());
    pool.setUsername(main.getUsername());
    pool.setPassword(main.getPassword());
    if (main.getDriverClassName() != null) {
      pool.setDriverClassName(main.getDriverClassName());
    }
    pool.setMaximumPoolSize(POOL_SIZE);
    pool.setMinimumIdle(0);
    pool.setIdleTimeout(60_000);
    pool.setConnectionTimeout(5_000);
    pool.setAutoCommit(true);
    pool.setInitializationFailTimeout(-1);
    return pool;
  }

  @Override
  public void record(AuditEvent event) {
    // Unha tarefa de Furar (sen petición) actúa co seu nome e sen roles; nunca co da petición.
    String system = event.systemActor();
    Optional<Actor> actor = system != null ? Optional.empty() : currentActor.current();
    String subject = system != null ? system : actor.map(Actor::subject).orElse("anonymous");
    Set<Role> roles = actorRoles(actor.orElse(null), event);
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    Instant now = clock.instant();
    String details = json.writeValueAsString(event.details());
    AUDIT.info(
        "audit action={} outcome={} subject={} roles={} municipality={} requestId={} details={}",
        event.action(),
        event.outcome(),
        subject,
        roles,
        event.municipalitySlug(),
        requestId,
        details);
    boolean inWriteTransaction =
        TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    JdbcClient target =
        event.outcome() == Outcome.SUCCESS && inWriteTransaction ? jdbc : autonomous;
    target
        .sql(INSERT)
        .param("id", UUID.randomUUID())
        .param("occurredAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
        .param("subject", subject)
        .param("roles", literal(roles))
        .param("action", event.action())
        .param("outcome", event.outcome().name())
        .param("municipalityId", event.municipalityId())
        .param("slug", event.municipalitySlug())
        .param("requestId", requestId)
        .param("details", details)
        .update();
  }

  /**
   * Os roles efectivos de quen actúa no concello do evento: os que valen nese concello e os
   * globais, máis os que o evento sabe. Sen concello, só os globais.
   */
  static Set<Role> actorRoles(@Nullable Actor actor, AuditEvent event) {
    Set<Role> roles = EnumSet.noneOf(Role.class);
    roles.addAll(event.knownActorRoles());
    if (actor != null) {
      String slug = event.municipalitySlug();
      actor.grants().stream()
          .filter(g -> RoleGrant.ANY.equals(g.scope()) || g.scope().equals(slug))
          .map(RoleGrant::role)
          .forEach(roles::add);
    }
    roles.retainAll(RECORDED);
    return roles;
  }

  /** Literal de {@code text[]} en orde fixa ({@code {PLATFORM_ADMIN,MANAGER}}). */
  private static String literal(Set<Role> roles) {
    return roles.stream().map(Role::name).collect(Collectors.joining(",", "{", "}"));
  }

  @Override
  public void destroy() {
    autonomousPool.close();
  }
}
