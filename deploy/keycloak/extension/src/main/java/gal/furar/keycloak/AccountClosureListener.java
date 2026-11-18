// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.lang.System.Logger.Level;
import java.util.Optional;
import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakTransaction;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.timer.TimerProvider;

/**
 * Avisa á API de Furar de cada conta eliminada (ADR-0043), para que borre e desligue os seus datos.
 *
 * <ul>
 *   <li><b>Ao momento:</b> co evento {@code DELETE_ACCOUNT} (o que deixa {@link AccountDeletion}),
 *       despois do commit da transacción que borrou a conta: se a eliminación falla, non avisa.
 *       Unha soa tentativa de 3 s como moito.
 *   <li><b>Sen perder ningún:</b> se a API non estaba, {@link AccountClosureReplay} volve avisar
 *       cada {@value #REPLAY_MINUTES} minutos dos {@code DELETE_ACCOUNT} gardados no almacén de
 *       eventos de Keycloak (90 días), desde o último que a API recibiu. A API é idempotente:
 *       avisar dúas veces da mesma conta non fai nada a segunda.
 * </ul>
 *
 * Global: non hai que engadilo aos «Event listeners» do realm. Non rexistra o {@code sub}.
 */
public final class AccountClosureListener implements EventListenerProviderFactory {

  /** Id do provedor. */
  public static final String PROVIDER_ID = "furar-account-closure";

  /** Cada cantos minutos se repasan os avisos pendentes. */
  static final long REPLAY_MINUTES = 10;

  private static final System.Logger LOG =
      System.getLogger(AccountClosureListener.class.getName());

  private Optional<AccountClosureApi> api = Optional.empty();

  /** Se o evento é unha conta eliminada; o id da conta, se si. */
  static Optional<String> closedAccount(Event event) {
    if (event == null || event.getType() != EventType.DELETE_ACCOUNT || event.getError() != null) {
      return Optional.empty();
    }
    String user = event.getUserId();
    return user == null || user.isBlank() ? Optional.empty() : Optional.of(user);
  }

  @Override
  public EventListenerProvider create(KeycloakSession session) {
    return new Listener(session, api);
  }

  @Override
  public void init(Config.Scope config) {
    api = AccountClosureApi.fromEnvironment();
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    if (api.isEmpty()) {
      return;
    }
    AccountClosureApi closure = api.get();
    KeycloakModelUtils.runJobInTransaction(
        factory,
        session ->
            session
                .getProvider(TimerProvider.class)
                .scheduleTask(
                    new AccountClosureReplay(closure),
                    REPLAY_MINUTES * 60_000L,
                    AccountClosureReplay.TASK_NAME));
  }

  @Override
  public void close() {}

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public boolean isGlobal() {
    return true;
  }

  /** O provedor de cada sesión: só mira os {@code DELETE_ACCOUNT} que saíron ben. */
  private static final class Listener implements EventListenerProvider {

    private final KeycloakSession session;
    private final Optional<AccountClosureApi> api;

    Listener(KeycloakSession session, Optional<AccountClosureApi> api) {
      this.session = session;
      this.api = api;
    }

    @Override
    public void onEvent(Event event) {
      Optional<String> account = closedAccount(event);
      if (account.isEmpty() || api.isEmpty()) {
        return;
      }
      AccountClosureApi closure = api.get();
      String subject = account.get();
      session.getTransactionManager().enlistAfterCompletion(new AfterCommit(closure, subject));
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {}

    @Override
    public void close() {}
  }

  /** Avisa só se a transacción que borrou a conta fixo commit. */
  private static final class AfterCommit implements KeycloakTransaction {

    private final AccountClosureApi api;
    private final String subject;
    private boolean active;
    private boolean rollbackOnly;

    AfterCommit(AccountClosureApi api, String subject) {
      this.api = api;
      this.subject = subject;
    }

    @Override
    public void begin() {
      active = true;
    }

    @Override
    public void commit() {
      active = false;
      if (!api.closed(subject)) {
        LOG.log(
            Level.WARNING,
            "Furar API did not take an account deletion; it will be retried (ADR-0043)");
      }
    }

    @Override
    public void rollback() {
      active = false;
    }

    @Override
    public void setRollbackOnly() {
      rollbackOnly = true;
    }

    @Override
    public boolean getRollbackOnly() {
      return rollbackOnly;
    }

    @Override
    public boolean isActive() {
      return active;
    }
  }
}
