// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.service;

import gal.furar.people.application.port.in.AccountClosureUseCase;
import gal.furar.people.application.port.out.AccountClosures;
import gal.furar.shared.account.AccountClosureStep;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A conta xa se eliminou en Keycloak (ADR-0043): Keycloak avisa polo porto interno, coa clave, e
 * aquí bórrase e desligase o que era dela, menos o que hai que conservar.
 *
 * <ol>
 *   <li>{@code close_account} nunha transacción (a función {@code close_account_counted}, que conta
 *       antes cantas filas toca en cada concello): o común da conta e o que deixa en cada módulo
 *       (docs/datos-persoais.md, «Dereitos»).
 *   <li>Os pasos dos módulos ({@link AccountClosureStep}), despois: hoxe, {@code booking} anonimiza
 *       xa as reservas sen pago. As de pago quedan ata o seu prazo (5 anos).
 *   <li>Un evento {@value #CLOSE} no rexistro de actividade de cada concello onde se tocou algo,
 *       coa tarefa como quen actúa ({@value #SUBJECT}) e só as cifras: nin a conta nin o correo.
 * </ol>
 *
 * Idempotente: unha conta xa limpa non toca nada nin deixa outro evento. Se un paso falla, o
 * rexistro leva o que xa se fixo e o erro chega a Keycloak, que volve avisar máis tarde; a segunda
 * vez só fai (e rexistra) o que faltaba. O rexistro técnico só leva o concello e as cifras.
 */
@Service
public class AccountClosureService implements AccountClosureUseCase {

  /** Quen actúa no rexistro de actividade: a baixa da conta, non unha persoa. */
  public static final String SUBJECT = AuditEvent.SYSTEM_PREFIX + "account-closure";

  /** Acción do rexistro de actividade: unha conta eliminada tocou filas deste concello. */
  public static final String CLOSE = "people.account-close";

  /** Detalle co número de filas de {@code close_account}. */
  static final String ROWS = "rows";

  private static final Logger LOG = LoggerFactory.getLogger(AccountClosureService.class);

  private final AccountClosures closures;
  private final ObjectProvider<AccountClosureStep> steps;
  private final MunicipalityDirectory directory;
  private final AuditLog audit;
  private final TransactionTemplate tx;

  public AccountClosureService(
      AccountClosures closures,
      ObjectProvider<AccountClosureStep> steps,
      MunicipalityDirectory directory,
      AuditLog audit,
      PlatformTransactionManager transactions) {
    this.closures = closures;
    this.steps = steps;
    this.directory = directory;
    this.audit = audit;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  @PublicUseCase
  public long closed(String subject) {
    Map<UUID, Map<String, Long>> done = new LinkedHashMap<>();
    try {
      add(done, ROWS, Objects.requireNonNull(tx.execute(s -> closures.close(subject)), "rows"));
      for (AccountClosureStep step : steps.orderedStream().toList()) {
        add(done, step.detail(), step.close(subject));
      }
    } finally {
      record(done);
    }
    return done.values().stream()
        .flatMap(m -> m.values().stream())
        .mapToLong(Long::longValue)
        .sum();
  }

  /** Suma as cifras dun paso ás do concello; un concello sen ningunha fila non entra. */
  static void add(Map<UUID, Map<String, Long>> done, String detail, Map<UUID, Long> counts) {
    counts.forEach(
        (municipality, n) -> {
          if (n != null && n > 0) {
            done.computeIfAbsent(municipality, m -> new LinkedHashMap<>())
                .merge(detail, n, Long::sum);
          }
        });
  }

  private void record(Map<UUID, Map<String, Long>> done) {
    if (done.isEmpty()) {
      return;
    }
    Map<UUID, String> slugs =
        directory.all().stream()
            .filter(t -> t.slug() != null)
            .collect(Collectors.toMap(TenantRef::id, TenantRef::slug, (a, b) -> a));
    done.forEach(
        (municipality, details) -> {
          TenantRef tenant = new TenantRef(municipality, slugs.get(municipality));
          try {
            TenantContext.runAs(
                tenant,
                () ->
                    tx.executeWithoutResult(
                        s ->
                            audit.record(
                                AuditEvent.bySystem(
                                    SUBJECT,
                                    CLOSE,
                                    Outcome.SUCCESS,
                                    municipality,
                                    tenant.slug(),
                                    Map.copyOf(details)))));
            LOG.info("account closure municipality={} {}", municipality, details);
          } catch (RuntimeException e) {
            // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
            LOG.warn(
                "account closure audit failed municipality={} error={}",
                municipality,
                e.getClass().getName());
          }
        });
  }
}
