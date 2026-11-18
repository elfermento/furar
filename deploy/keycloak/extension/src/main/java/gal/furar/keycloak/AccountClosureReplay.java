// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.keycloak.events.Event;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionProviderModel;
import org.keycloak.timer.ScheduledTask;

/**
 * Que ningunha conta eliminada quede sen limpar na API (ADR-0043), aínda que a API estivese parada
 * ou Keycloak se reiniciase xusto despois. Cada poucos minutos, en cada realm coa acción {@value
 * AccountDeletion#PROVIDER_ID} activa, le do almacén de eventos de Keycloak os {@code
 * DELETE_ACCOUNT} desde a marca do realm ({@value #MARK_ATTRIBUTE}, un instante en milisegundos) e
 * avisa á API de cada un, por orde. A marca só avanza ata o último que a API recibiu: se falla un,
 * para e volve tentalo na seguinte pasada.
 *
 * <p>O evento gárdase na mesma transacción ca o borrado da conta, así que non se perde ningún. Os
 * eventos duran o que diga o realm (90 días): se a API pasase máis ca iso sen responder, eses avisos
 * perderíanse (e o rexistro técnico de Keycloak dio cada vez). A API é idempotente: o aviso que xa
 * chegou ao momento ({@link AccountClosureListener}) chega outra vez aquí e non fai nada.
 */
final class AccountClosureReplay implements ScheduledTask {

  /** Nome da tarefa no temporizador de Keycloak. */
  static final String TASK_NAME = "furar-account-closure-replay";

  /** Atributo do realm: ata cando (ms) a API xa recibiu todos os avisos. */
  static final String MARK_ATTRIBUTE = "furarAccountClosureMark";

  /** Avisos por pasada, como moito. */
  static final int BATCH = 100;

  private static final System.Logger LOG = System.getLogger(AccountClosureReplay.class.getName());

  private final AccountClosureApi api;

  AccountClosureReplay(AccountClosureApi api) {
    this.api = api;
  }

  /** Un {@code DELETE_ACCOUNT} gardado: cando e de que conta. */
  record Closed(long time, String subject) {}

  /**
   * A marca nova despois dunha pasada: o instante do último aviso recibido máis un milisegundo, para
   * non repetilo. Se o primeiro que falla é do mesmo milisegundo ca o último bo, a marca queda nese
   * milisegundo (o bo repítese, e a API non fai nada a segunda vez).
   *
   * @param mark a marca de antes
   * @param closed os avisos lidos, en orde de tempo
   * @param delivered avisa á API; falso se non o recibiu
   */
  static long advance(long mark, List<Closed> closed, Predicate<Closed> delivered) {
    long next = mark;
    for (Closed c : closed) {
      if (!delivered.test(c)) {
        return Math.min(next, c.time());
      }
      next = Math.max(next, c.time() + 1);
    }
    return next;
  }

  /** A marca gardada; sen ela (ou mal escrita), desde o principio do almacén. */
  static long mark(String stored) {
    if (stored == null || stored.isBlank()) {
      return 0;
    }
    try {
      return Math.max(0, Long.parseLong(stored.strip()));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public void run(KeycloakSession session) {
    EventStoreProvider store = session.getProvider(EventStoreProvider.class);
    if (store == null) {
      return;
    }
    List<RealmModel> realms =
        session.realms().getRealmsStream().filter(AccountClosureReplay::deletes).toList();
    realms.forEach(realm -> replay(store, realm));
  }

  /** Só os realms onde a persoa pode eliminar a súa conta. */
  private static boolean deletes(RealmModel realm) {
    RequiredActionProviderModel action =
        realm.getRequiredActionProviderByAlias(AccountDeletion.PROVIDER_ID);
    return action != null && action.isEnabled();
  }

  private void replay(EventStoreProvider store, RealmModel realm) {
    long mark = mark(realm.getAttribute(MARK_ATTRIBUTE));
    List<Closed> closed =
        store
            .createQuery()
            .realm(realm.getId())
            .type(EventType.DELETE_ACCOUNT)
            .fromDate(mark)
            .orderByAscTime()
            .maxResults(BATCH)
            .getResultStream()
            .map(AccountClosureReplay::closed)
            .filter(Objects::nonNull)
            .toList();
    if (closed.isEmpty()) {
      return;
    }
    int[] failed = {0};
    long next =
        advance(
            mark,
            closed,
            c -> {
              boolean ok = api.closed(c.subject());
              if (!ok) {
                failed[0]++;
              }
              return ok;
            });
    if (next != mark) {
      realm.setAttribute(MARK_ATTRIBUTE, Long.toString(next));
    }
    if (failed[0] > 0) {
      LOG.log(
          Level.WARNING,
          "Furar API did not take a pending account deletion; retrying in the next pass"
              + " (ADR-0043)");
    }
  }

  private static Closed closed(Event event) {
    return AccountClosureListener.closedAccount(event)
        .map(subject -> new Closed(event.getTime(), subject))
        .orElse(null);
  }

  @Override
  public String getTaskName() {
    return TASK_NAME;
  }
}
